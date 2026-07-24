package com.itcabs.dispatch

import com.itcabs.shared.badRequest
import com.itcabs.shared.conflict
import com.itcabs.shared.forbidden
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Multi-stop corporate jobs: one company + trip type + ordered employee stops, served by one driver.
 * Coexists with the single-leg [DispatchService]. The whole job is the claimable unit; each stop has
 * its own GPS + a per-stop pickup OTP the employee gives to prove pickup.
 */
@Service
class CompanyJobService(private val db: NamedParameterJdbcTemplate) {

    // --- coordinator ---

    @Transactional
    fun create(coordinatorId: Long, input: CompanyJobInput): CompanyJobDto {
        val tripType = input.tripType.uppercase()
        if (tripType !in setOf("PICKUP", "DROP")) throw badRequest("tripType must be PICKUP or DROP")
        if (input.stops.isEmpty()) throw badRequest("a job needs at least one stop")
        if (input.farePaise < 0) throw badRequest("fare must be >= 0")
        val publishAt = input.publishAt?.let {
            runCatching { java.time.OffsetDateTime.parse(it) }.getOrElse { throw badRequest("publishAt must be ISO-8601") }
        }
        val jobId = db.queryForObject(
            """INSERT INTO company_jobs(coordinator_id, company_name, trip_type, office, vehicle_type, fare_paise, publish_at)
               VALUES (:c,:cn,:tt,:o,:vt,:fp, coalesce(:pa, now())) RETURNING id""",
            MapSqlParameterSource().addValue("c", coordinatorId).addValue("cn", input.companyName)
                .addValue("tt", tripType).addValue("o", input.office).addValue("vt", input.vehicleType)
                .addValue("fp", input.farePaise)
                .addValue("pa", publishAt?.let { java.sql.Timestamp.from(it.toInstant()) }),
            Long::class.java,
        )!!
        insertStops(jobId, input.stops)
        return oneJob(coordinatorId, jobId, forCoordinator = true)
    }

    fun myJobs(coordinatorId: Long): List<CompanyJobDto> =
        jobsWhere("j.coordinator_id = :c ORDER BY j.created_at DESC", MapSqlParameterSource("c", coordinatorId), forCoordinator = true)

    /** Replace the ordered stop list of an OPEN job (edit / remove / reorder before dispatch). */
    @Transactional
    fun replaceStops(coordinatorId: Long, jobId: Long, stops: List<StopInput>) {
        if (stops.isEmpty()) throw badRequest("a job needs at least one stop")
        val n = db.update(
            "UPDATE company_jobs SET version=version+1 WHERE id=:id AND coordinator_id=:c AND status='OPEN'",
            MapSqlParameterSource().addValue("id", jobId).addValue("c", coordinatorId),
        )
        if (n == 0) throw badRequest("job not found, not yours, or already dispatched")
        db.update("DELETE FROM job_stops WHERE job_id = :id", MapSqlParameterSource("id", jobId))
        insertStops(jobId, stops)
    }

    @Transactional
    fun setStatus(coordinatorId: Long, jobId: Long, status: String): Long? {
        if (status !in setOf("CONFIRMED", "COMPLETED", "CANCELLED"))
            throw badRequest("status must be CONFIRMED, COMPLETED or CANCELLED")
        val claimedBy = db.queryForList(
            "SELECT claimed_by FROM company_jobs WHERE id=:id AND coordinator_id=:c",
            MapSqlParameterSource().addValue("id", jobId).addValue("c", coordinatorId),
        ).firstOrNull()?.get("claimed_by") as? Number
        val n = db.update(
            "UPDATE company_jobs SET status=:s, version=version+1 WHERE id=:id AND coordinator_id=:c",
            MapSqlParameterSource().addValue("s", status).addValue("id", jobId).addValue("c", coordinatorId),
        )
        if (n == 0) throw forbidden("not your job, or job not found")
        if (status == "COMPLETED" && claimedBy != null) {
            db.update("UPDATE driver_profiles SET trips_completed = trips_completed + 1 WHERE user_id = :d",
                MapSqlParameterSource("d", claimedBy.toLong()))
        }
        return claimedBy?.toLong()
    }

    @Transactional
    fun assign(coordinatorId: Long, jobId: Long, driverId: Long): CompanyJobDto {
        val eligible = db.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
               WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED')""",
            MapSqlParameterSource("d", driverId), Boolean::class.java,
        ) ?: false
        if (!eligible) throw badRequest("driver is not verified")
        val n = db.update(
            """UPDATE company_jobs SET status='CLAIMED', claimed_by=:d, claimed_at=now(), version=version+1
               WHERE id=:id AND coordinator_id=:c AND status='OPEN'""",
            MapSqlParameterSource().addValue("d", driverId).addValue("id", jobId).addValue("c", coordinatorId),
        )
        if (n == 0) throw conflict("job not open, not yours, or already taken")
        assignStopOtps(jobId)
        return oneJob(coordinatorId, jobId, forCoordinator = true)
    }

    // --- driver ---

    fun feed(): List<CompanyJobDto> =
        jobsWhere("j.status='OPEN' AND j.publish_at <= now() ORDER BY j.created_at DESC", MapSqlParameterSource(), forCoordinator = false)

    fun myTrips(driverId: Long): List<CompanyJobDto> =
        jobsWhere("j.claimed_by = :d ORDER BY j.claimed_at DESC", MapSqlParameterSource("d", driverId), forCoordinator = false)

    /** First-claim-wins on the whole job; generates a per-stop OTP the driver must collect from each employee. */
    @Transactional
    fun claim(driverId: Long, jobId: Long): CompanyJobDto {
        val eligible = db.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
               WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED')""",
            MapSqlParameterSource("d", driverId), Boolean::class.java,
        ) ?: false
        if (!eligible) throw forbidden("driver not verified")
        val won = db.update(
            """UPDATE company_jobs SET status='CLAIMED', claimed_by=:d, claimed_at=now(), version=version+1
               WHERE id=:id AND status='OPEN'
                 AND EXISTS (SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
                             WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED')""",
            MapSqlParameterSource().addValue("d", driverId).addValue("id", jobId),
        )
        if (won == 0) throw conflict("job already taken")
        assignStopOtps(jobId)
        return oneJob(driverId, jobId, forCoordinator = false)
    }

    /** Driver confirms pickup at a stop by entering the employee's OTP. */
    @Transactional
    fun confirmStopPickup(driverId: Long, stopId: Long, otp: String?) {
        val row = db.queryForList(
            """SELECT s.pickup_otp FROM job_stops s JOIN company_jobs j ON j.id = s.job_id
                WHERE s.id=:s AND j.claimed_by=:d""",
            MapSqlParameterSource().addValue("s", stopId).addValue("d", driverId),
        ).firstOrNull() ?: throw forbidden("not your trip, or stop not found")
        val expected = row["pickup_otp"] as? String
        if (expected != null && otp?.trim() != expected) throw badRequest("wrong pickup code")
        db.update("UPDATE job_stops SET picked_up_at = now() WHERE id = :s", MapSqlParameterSource("s", stopId))
    }

    // --- helpers ---

    private fun insertStops(jobId: Long, stops: List<StopInput>) {
        stops.forEachIndexed { i, s ->
            if (s.employeeName.isBlank()) throw badRequest("every stop needs an employee name")
            db.update(
                """INSERT INTO job_stops(job_id, employee_name, address, lat, lng, place_id, phone, stop_order)
                   VALUES (:j,:n,:a,:lat,:lng,:pid,:ph,:ord)""",
                MapSqlParameterSource().addValue("j", jobId).addValue("n", s.employeeName)
                    .addValue("a", s.address).addValue("lat", s.lat).addValue("lng", s.lng)
                    .addValue("pid", s.placeId).addValue("ph", s.phone).addValue("ord", i),
            )
        }
    }

    private fun assignStopOtps(jobId: Long) {
        db.query("SELECT id FROM job_stops WHERE job_id = :j", MapSqlParameterSource("j", jobId)) { rs, _ -> rs.getLong("id") }
            .forEach { stopId ->
                db.update("UPDATE job_stops SET pickup_otp = :otp WHERE id = :id",
                    MapSqlParameterSource().addValue("otp", "%04d".format((0..9999).random())).addValue("id", stopId))
            }
    }

    private fun oneJob(userId: Long, jobId: Long, forCoordinator: Boolean): CompanyJobDto =
        jobsWhere("j.id = :id", MapSqlParameterSource("id", jobId), forCoordinator).firstOrNull()
            ?: throw badRequest("job not found")

    private fun jobsWhere(where: String, params: MapSqlParameterSource, forCoordinator: Boolean): List<CompanyJobDto> {
        val jobs = db.query(
            """SELECT j.id, j.coordinator_id, j.company_name, j.trip_type, j.office, j.vehicle_type,
                      j.fare_paise, j.status, j.claimed_by, u.name AS claimed_by_name, j.version
                 FROM company_jobs j LEFT JOIN users u ON u.id = j.claimed_by
                WHERE $where""",
            params,
        ) { rs, _ ->
            CompanyJobDto(
                id = rs.getLong("id"),
                coordinatorId = rs.getLong("coordinator_id"),
                companyName = rs.getString("company_name"),
                tripType = rs.getString("trip_type"),
                office = rs.getString("office"),
                vehicleType = rs.getString("vehicle_type"),
                farePaise = rs.getLong("fare_paise"),
                status = rs.getString("status"),
                claimedBy = rs.getObject("claimed_by")?.let { (it as Number).toLong() },
                claimedByName = rs.getString("claimed_by_name"),
                stops = emptyList(),
                version = rs.getInt("version"),
            )
        }
        if (jobs.isEmpty()) return jobs
        val stopsByJob = db.query(
            "SELECT * FROM job_stops WHERE job_id IN (:ids) ORDER BY job_id, stop_order",
            MapSqlParameterSource("ids", jobs.map { it.id }),
        ) { rs, _ ->
            rs.getLong("job_id") to StopDto(
                id = rs.getLong("id"),
                employeeName = rs.getString("employee_name"),
                address = rs.getString("address"),
                lat = rs.getObject("lat")?.let { (it as Number).toDouble() },
                lng = rs.getObject("lng")?.let { (it as Number).toDouble() },
                placeId = rs.getString("place_id"),
                phone = rs.getString("phone"),
                stopOrder = rs.getInt("stop_order"),
                pickedUp = rs.getObject("picked_up_at") != null,
                // OTP only to the coordinator; the driver collects it from the employee.
                pickupOtp = if (forCoordinator) rs.getString("pickup_otp") else null,
            )
        }.groupBy({ it.first }, { it.second })
        return jobs.map { it.copy(stops = stopsByJob[it.id] ?: emptyList()) }
    }
}
