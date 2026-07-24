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
        validateStopsForVehicle(input.vehicleType, input.stops.size)
        if (input.farePaise < 0) throw badRequest("fare must be >= 0")
        val publishAt = input.publishAt?.let {
            runCatching { java.time.OffsetDateTime.parse(it) }.getOrElse { throw badRequest("publishAt must be ISO-8601") }
        }
        val jobId = db.queryForObject(
            """INSERT INTO company_jobs(coordinator_id, company_name, trip_type, office, office_address,
                     office_lat, office_lng, office_place_id, pickup_time, drop_time, vehicle_type, vehicle_ac, fare_paise, publish_at)
               VALUES (:c,:cn,:tt,:o,:oa,:olat,:olng,:opid,:pt,:dt,:vt,:ac,:fp, coalesce(:pa, now())) RETURNING id""",
            jobParams(coordinatorId, tripType, input).addValue("pa", publishAt?.let { java.sql.Timestamp.from(it.toInstant()) }),
            Long::class.java,
        )!!
        insertStops(jobId, input.stops)
        return oneJob(coordinatorId, jobId, forCoordinator = true)
    }

    /** Full edit while OPEN (before any driver accepts): job fields + stops. Locked once claimed. */
    @Transactional
    fun edit(coordinatorId: Long, jobId: Long, input: CompanyJobInput): CompanyJobDto {
        val tripType = input.tripType.uppercase()
        if (tripType !in setOf("PICKUP", "DROP")) throw badRequest("tripType must be PICKUP or DROP")
        validateStopsForVehicle(input.vehicleType, input.stops.size)
        val n = db.update(
            """UPDATE company_jobs SET company_name=:cn, trip_type=:tt, office=:o, office_address=:oa,
                     office_lat=:olat, office_lng=:olng, office_place_id=:opid, pickup_time=:pt, drop_time=:dt,
                     vehicle_type=:vt, vehicle_ac=:ac, fare_paise=:fp, version=version+1
               WHERE id=:id AND coordinator_id=:c AND status='OPEN'""",
            jobParams(coordinatorId, tripType, input).addValue("id", jobId),
        )
        if (n == 0) throw badRequest("job not found, not yours, or already accepted by a driver")
        db.update("DELETE FROM job_stops WHERE job_id=:id", MapSqlParameterSource("id", jobId))
        insertStops(jobId, input.stops)
        return oneJob(coordinatorId, jobId, forCoordinator = true)
    }

    private fun jobParams(coordinatorId: Long, tripType: String, input: CompanyJobInput) =
        MapSqlParameterSource().addValue("c", coordinatorId).addValue("cn", input.companyName)
            .addValue("tt", tripType).addValue("o", input.office).addValue("oa", input.officeAddress)
            .addValue("olat", input.officeLat).addValue("olng", input.officeLng).addValue("opid", input.officePlaceId)
            .addValue("pt", input.pickupTime).addValue("dt", input.dropTime)
            .addValue("vt", input.vehicleType.uppercase()).addValue("ac", input.vehicleAc).addValue("fp", input.farePaise)

    /** Capacity: Sedan ≤ 4 stops, SUV ≤ 6. */
    private fun validateStopsForVehicle(vehicleType: String, count: Int) {
        if (count == 0) throw badRequest("a job needs at least one stop")
        val max = when (vehicleType.uppercase()) { "SEDAN" -> 4; "SUV" -> 6; else -> 6 }
        if (count > max) throw badRequest("${vehicleType.ifBlank { "This vehicle" }} allows at most $max stops")
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

    /** Coordinator settles a completed job (flat fare, cash paid). Idempotent. */
    @Transactional
    fun markPaid(coordinatorId: Long, jobId: Long) {
        val n = db.update(
            "UPDATE company_jobs SET paid_at=now() WHERE id=:id AND coordinator_id=:c AND status='COMPLETED' AND paid_at IS NULL",
            MapSqlParameterSource().addValue("id", jobId).addValue("c", coordinatorId),
        )
        if (n == 0) throw badRequest("job not found, not yours, not completed, or already paid")
    }

    /** Coordinator reports a no-show: dings the driver, reopens the job, clears stop OTPs/pickups. */
    @Transactional
    fun markNoShow(coordinatorId: Long, jobId: Long) {
        val row = db.queryForList(
            "SELECT claimed_by, status FROM company_jobs WHERE id=:id AND coordinator_id=:c",
            MapSqlParameterSource().addValue("id", jobId).addValue("c", coordinatorId),
        ).firstOrNull() ?: throw forbidden("not your job, or job not found")
        val driverId = (row["claimed_by"] as? Number)?.toLong() ?: throw badRequest("job has no claimed driver")
        if (row["status"] !in setOf("CLAIMED", "CONFIRMED")) throw badRequest("can only report a no-show on a claimed/confirmed job")
        db.update("UPDATE driver_profiles SET no_shows = no_shows + 1 WHERE user_id=:d", MapSqlParameterSource("d", driverId))
        reopen(jobId)
    }

    /** Driver hands a claimed job back before it starts — no no-show recorded. */
    @Transactional
    fun releaseTrip(driverId: Long, jobId: Long) {
        val n = db.update(
            "UPDATE company_jobs SET status='OPEN', claimed_by=NULL, claimed_at=NULL, version=version+1 WHERE id=:id AND claimed_by=:d AND status IN ('CLAIMED','CONFIRMED')",
            MapSqlParameterSource().addValue("id", jobId).addValue("d", driverId),
        )
        if (n == 0) throw forbidden("not your active job")
        db.update("UPDATE job_stops SET pickup_otp=NULL, picked_up_at=NULL WHERE job_id=:id", MapSqlParameterSource("id", jobId))
    }

    /** Driver marks the whole job done after the last drop. Idempotent trips increment. */
    @Transactional
    fun driverComplete(driverId: Long, jobId: Long) {
        val n = db.update(
            "UPDATE company_jobs SET status='COMPLETED', version=version+1 WHERE id=:id AND claimed_by=:d AND status <> 'COMPLETED'",
            MapSqlParameterSource().addValue("id", jobId).addValue("d", driverId),
        )
        if (n == 0) throw forbidden("not your job, or already completed")
        db.update("UPDATE driver_profiles SET trips_completed = trips_completed + 1 WHERE user_id=:d", MapSqlParameterSource("d", driverId))
    }

    /** The claimed driver's latest location for a coordinator's company job (for the live map). */
    fun driverLocation(coordinatorId: Long, jobId: Long): Map<String, Any?>? {
        val row = db.queryForList(
            """SELECT p.last_lat, p.last_lng, p.last_loc_at FROM company_jobs j
                 JOIN driver_profiles p ON p.user_id = j.claimed_by
                WHERE j.id=:id AND j.coordinator_id=:c""",
            MapSqlParameterSource().addValue("id", jobId).addValue("c", coordinatorId),
        ).firstOrNull() ?: return null
        val lat = row["last_lat"] ?: return null
        return mapOf("lat" to lat, "lng" to row["last_lng"], "updatedAt" to row["last_loc_at"]?.toString())
    }

    private fun reopen(jobId: Long) {
        db.update("UPDATE company_jobs SET status='OPEN', claimed_by=NULL, claimed_at=NULL, version=version+1 WHERE id=:id", MapSqlParameterSource("id", jobId))
        db.update("UPDATE job_stops SET pickup_otp=NULL, picked_up_at=NULL WHERE job_id=:id", MapSqlParameterSource("id", jobId))
    }

    @Transactional
    fun assign(coordinatorId: Long, jobId: Long, driverId: Long): CompanyJobDto {
        val eligible = db.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
               WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED' AND u.phone_verified)""",
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

    /** Only OPEN jobs matching the driver's own vehicle type (Sedan driver → Sedan jobs). */
    fun feed(driverId: Long): List<CompanyJobDto> {
        val vt = db.queryForList("SELECT vehicle_type FROM driver_profiles WHERE user_id=:d", MapSqlParameterSource("d", driverId))
            .firstOrNull()?.get("vehicle_type") as? String
        val where = StringBuilder("j.status='OPEN' AND j.publish_at <= now()")
        val params = MapSqlParameterSource()
        if (!vt.isNullOrBlank()) { where.append(" AND upper(j.vehicle_type) = upper(:vt)"); params.addValue("vt", vt) }
        where.append(" ORDER BY j.created_at DESC")
        return jobsWhere(where.toString(), params, forCoordinator = false)
    }

    fun myTrips(driverId: Long): List<CompanyJobDto> =
        jobsWhere("j.claimed_by = :d ORDER BY j.claimed_at DESC", MapSqlParameterSource("d", driverId), forCoordinator = false)

    /** First-claim-wins on the whole job; generates a per-stop OTP the driver must collect from each employee. */
    @Transactional
    fun claim(driverId: Long, jobId: Long): CompanyJobDto {
        val eligible = db.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
               WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED' AND u.phone_verified)""",
            MapSqlParameterSource("d", driverId), Boolean::class.java,
        ) ?: false
        if (!eligible) throw forbidden("driver not verified")
        val won = db.update(
            """UPDATE company_jobs SET status='CLAIMED', claimed_by=:d, claimed_at=now(), version=version+1
               WHERE id=:id AND status='OPEN'
                 AND EXISTS (SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
                             WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED' AND u.phone_verified)""",
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
                      j.office_address, j.office_lat, j.office_lng, j.pickup_time, j.drop_time, j.vehicle_ac,
                      j.fare_paise, j.status, j.claimed_by, u.name AS claimed_by_name, j.paid_at, j.version
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
                officeAddress = rs.getString("office_address") ?: "",
                officeLat = rs.getObject("office_lat")?.let { (it as Number).toDouble() },
                officeLng = rs.getObject("office_lng")?.let { (it as Number).toDouble() },
                pickupTime = rs.getString("pickup_time") ?: "",
                dropTime = rs.getString("drop_time") ?: "",
                vehicleAc = rs.getBoolean("vehicle_ac"),
                claimedBy = rs.getObject("claimed_by")?.let { (it as Number).toLong() },
                claimedByName = rs.getString("claimed_by_name"),
                paid = rs.getObject("paid_at") != null,
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
        val withStops = jobs.map { it.copy(stops = stopsByJob[it.id] ?: emptyList()) }
        return if (forCoordinator) withStops else withStops.map { maskForDriver(it) }
    }

    /**
     * Privacy (spec 9–11): the driver sees pins + sequence, but employee details are revealed
     * stop-by-stop — only the active stop (first not-picked-up) and past stops show name; phone shows
     * only for the active stop while the trip is live; once COMPLETED all phones are hidden.
     */
    private fun maskForDriver(job: CompanyJobDto): CompanyJobDto {
        val activeOrder = job.stops.filter { !it.pickedUp }.minOfOrNull { it.stopOrder }
        val completed = job.status == "COMPLETED"
        return job.copy(stops = job.stops.map { s ->
            val future = activeOrder != null && s.stopOrder > activeOrder
            val isActive = activeOrder != null && s.stopOrder == activeOrder && !completed
            s.copy(
                employeeName = if (future) "" else s.employeeName,          // hide future names
                phone = if (isActive) s.phone else "",                      // phone only on the active stop, live
                pickupOtp = null,                                           // driver never reads the code
            )
        })
    }
}
