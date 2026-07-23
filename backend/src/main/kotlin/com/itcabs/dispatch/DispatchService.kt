package com.itcabs.dispatch

import com.itcabs.shared.badRequest
import com.itcabs.shared.conflict
import com.itcabs.shared.forbidden
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DispatchService(private val db: NamedParameterJdbcTemplate) {

    // --- coordinator: post + manage ---

    @Transactional
    fun postJob(coordinatorId: Long, input: PostJobInput): List<LegDto> {
        if (input.legs.isEmpty()) throw badRequest("at least one leg required")
        // publishAt in the future schedules the job; absent/past → live now.
        val publishAt = input.publishAt?.let {
            runCatching { java.time.OffsetDateTime.parse(it) }.getOrElse { throw badRequest("publishAt must be ISO-8601") }
        }
        val jobId = db.queryForObject(
            "INSERT INTO jobs(coordinator_id, office, shift, publish_at) VALUES (:c,:o,:s, coalesce(:pa, now())) RETURNING id",
            MapSqlParameterSource().addValue("c", coordinatorId).addValue("o", input.office).addValue("s", input.shift)
                .addValue("pa", publishAt?.let { java.sql.Timestamp.from(it.toInstant()) }),
            Long::class.java,
        )!!
        input.legs.forEach { leg ->
            if (leg.farePaise < 0) throw badRequest("fare must be >= 0")
            db.update(
                """INSERT INTO legs(job_id, coordinator_id, pickup, drop_point, area, time_window,
                                    vehicle_type, fare_paise, seats, passenger_name, passenger_phone)
                   VALUES (:j,:c,:pk,:dp,:ar,:tw,:vt,:fp,:st,:pn,:pp)""",
                MapSqlParameterSource()
                    .addValue("j", jobId).addValue("c", coordinatorId)
                    .addValue("pk", leg.pickup).addValue("dp", leg.drop).addValue("ar", leg.area)
                    .addValue("tw", leg.timeWindow).addValue("vt", leg.vehicleType)
                    .addValue("fp", leg.farePaise).addValue("st", leg.seats)
                    .addValue("pn", leg.passengerName).addValue("pp", leg.passengerPhone),
            )
        }
        return legsWhere("job_id = :j", MapSqlParameterSource("j", jobId))
    }

    /** Repost a route (M6): clone an existing job's legs into a new OPEN job — one-action reuse. */
    @Transactional
    fun repostJob(coordinatorId: Long, jobId: Long): List<LegDto> {
        val legs = legsWhere("l.job_id = :j", MapSqlParameterSource("j", jobId))
        val first = legs.firstOrNull() ?: throw badRequest("job not found")
        if (first.coordinatorId != coordinatorId) throw forbidden("not your job")
        return postJob(
            coordinatorId,
            PostJobInput(
                office = first.office,
                shift = first.shift,
                legs = legs.map { LegInput(it.pickup, it.drop, it.area, it.timeWindow, it.vehicleType, it.farePaise, it.seats, it.passengerName, it.passengerPhone) },
            ),
        )
    }

    fun myLegs(coordinatorId: Long) =
        legsWhere("l.coordinator_id = :c ORDER BY l.created_at DESC", MapSqlParameterSource("c", coordinatorId))

    /**
     * Coordinator advances their own leg through the workflow (not the claim step).
     * Returns the claimed driver's id (if any) so the caller can notify them on cancellation.
     */
    @Transactional
    fun setStatus(coordinatorId: Long, legId: Long, status: String): Long? {
        if (status !in setOf("CONFIRMED", "COMPLETED", "CANCELLED"))
            throw badRequest("status must be CONFIRMED, COMPLETED or CANCELLED")
        val claimedBy = db.queryForList(
            "SELECT claimed_by FROM legs WHERE id=:id AND coordinator_id=:c",
            MapSqlParameterSource().addValue("id", legId).addValue("c", coordinatorId),
        ).firstOrNull()?.get("claimed_by") as? Number
        val n = db.update(
            "UPDATE legs SET status=:s, version=version+1 WHERE id=:id AND coordinator_id=:c",
            MapSqlParameterSource().addValue("s", status).addValue("id", legId).addValue("c", coordinatorId),
        )
        if (n == 0) throw forbidden("not your leg, or leg not found")
        if (status == "COMPLETED") {
            db.update(
                """UPDATE driver_profiles SET trips_completed = trips_completed + 1
                   WHERE user_id = (SELECT claimed_by FROM legs WHERE id = :id AND claimed_by IS NOT NULL)""",
                MapSqlParameterSource("id", legId),
            )
        }
        return claimedBy?.toLong()
    }

    /** Edit an OPEN leg in place (fare/time/route fixes). Only the owning coordinator, only while OPEN. */
    @Transactional
    fun editLeg(coordinatorId: Long, legId: Long, input: EditLegInput): LegDto {
        val sets = mutableListOf<String>()
        val params = MapSqlParameterSource().addValue("id", legId).addValue("c", coordinatorId)
        fun set(col: String, key: String, value: Any?) { if (value != null) { sets.add("$col=:$key"); params.addValue(key, value) } }
        set("pickup", "pk", input.pickup)
        set("drop_point", "dp", input.drop)
        set("area", "ar", input.area)
        set("time_window", "tw", input.timeWindow)
        set("vehicle_type", "vt", input.vehicleType)
        input.farePaise?.let { if (it < 0) throw badRequest("fare must be >= 0") }
        set("fare_paise", "fp", input.farePaise)
        set("seats", "st", input.seats)
        set("passenger_name", "pn", input.passengerName)
        set("passenger_phone", "pp", input.passengerPhone)
        if (sets.isEmpty()) throw badRequest("nothing to update")
        val n = db.update(
            "UPDATE legs SET ${sets.joinToString(", ")}, version=version+1 WHERE id=:id AND coordinator_id=:c AND status='OPEN'",
            params,
        )
        if (n == 0) throw badRequest("leg not found, not yours, or not editable (already claimed)")
        return legsWhere("l.id = :qid", MapSqlParameterSource("qid", legId)).first()
    }

    /** Verified, active drivers a coordinator can hand a trip to directly. */
    fun verifiedDrivers(): List<VerifiedDriverDto> = db.query(
        """SELECT u.id, u.name, p.trips_completed, p.no_shows
             FROM users u JOIN driver_profiles p ON p.user_id = u.id
            WHERE u.role='DRIVER' AND u.status='ACTIVE' AND p.kyc_status='VERIFIED'
            ORDER BY p.trips_completed DESC, u.name""",
        MapSqlParameterSource(),
    ) { rs, _ -> VerifiedDriverDto(rs.getLong("id"), rs.getString("name") ?: "", rs.getInt("trips_completed"), rs.getInt("no_shows")) }

    /** Coordinator hand-assigns an OPEN leg to a specific verified driver (skips first-claim bidding). */
    @Transactional
    fun assign(coordinatorId: Long, legId: Long, driverId: Long): LegDto {
        val eligible = db.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
               WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED')""",
            MapSqlParameterSource("d", driverId), Boolean::class.java,
        ) ?: false
        if (!eligible) throw badRequest("driver is not verified")
        val n = db.update(
            """UPDATE legs SET status='CLAIMED', claimed_by=:d, claimed_at=now(), pickup_otp=:otp, version=version+1
               WHERE id=:id AND coordinator_id=:c AND status='OPEN'""",
            MapSqlParameterSource().addValue("d", driverId).addValue("id", legId)
                .addValue("c", coordinatorId).addValue("otp", newOtp()),
        )
        if (n == 0) throw conflict("leg not open, not yours, or already taken")
        return legsWhere("l.id = :qid", MapSqlParameterSource("qid", legId)).first()
    }

    /**
     * Coordinator reports a no-show: records it against the driver's reliability and REOPENS the leg
     * so someone else can claim it. Accountability is the point — ghosting has a cost.
     */
    @Transactional
    fun markNoShow(coordinatorId: Long, legId: Long) {
        val row = db.queryForList(
            "SELECT claimed_by, status FROM legs WHERE id = :id AND coordinator_id = :c",
            MapSqlParameterSource().addValue("id", legId).addValue("c", coordinatorId),
        ).firstOrNull() ?: throw forbidden("not your leg, or leg not found")
        val driverId = (row["claimed_by"] as? Number)?.toLong() ?: throw badRequest("leg has no claimed driver")
        if (row["status"] !in setOf("CLAIMED", "CONFIRMED"))
            throw badRequest("can only report a no-show on a claimed or confirmed leg")
        db.update("UPDATE driver_profiles SET no_shows = no_shows + 1 WHERE user_id = :d", MapSqlParameterSource("d", driverId))
        db.update(
            "UPDATE legs SET status='OPEN', claimed_by=NULL, claimed_at=NULL, trip_stage=NULL, version=version+1 WHERE id = :id",
            MapSqlParameterSource("id", legId),
        )
    }

    /**
     * Driver reports live trip progress on a leg they claimed. Orthogonal to the OPEN/CLAIMED/…
     * status: a CONFIRMED leg can be EN_ROUTE. Coordinator still owns COMPLETED.
     */
    @Transactional
    fun setStage(driverId: Long, legId: Long, stage: String, otp: String?) {
        if (stage !in setOf("EN_ROUTE", "ARRIVED", "STARTED"))
            throw badRequest("stage must be EN_ROUTE, ARRIVED or STARTED")
        // Starting a trip is the pickup-proof gate: the driver must key in the passenger's OTP.
        if (stage == "STARTED") {
            val expected = db.queryForList(
                "SELECT pickup_otp FROM legs WHERE id=:id AND claimed_by=:d",
                MapSqlParameterSource().addValue("id", legId).addValue("d", driverId),
            ).firstOrNull()?.get("pickup_otp") as? String
            if (expected != null && otp?.trim() != expected) throw badRequest("wrong pickup code")
        }
        val n = db.update(
            """UPDATE legs SET trip_stage=:s, version=version+1
               WHERE id=:id AND claimed_by=:d AND status IN ('CLAIMED','CONFIRMED')""",
            MapSqlParameterSource().addValue("s", stage).addValue("id", legId).addValue("d", driverId),
        )
        if (n == 0) throw forbidden("not your active trip, or trip not claimed")
    }

    // --- driver: browse + claim ---

    /** Open legs; with driver coords, nearest-first (legs in unknown areas sort last). */
    fun feed(area: String?, vehicleType: String?, lat: Double?, lng: Double?): List<LegDto> {
        // Scheduled jobs stay hidden until their publish_at.
        val where = StringBuilder("l.status = 'OPEN' AND j.publish_at <= now()")
        val params = MapSqlParameterSource()
        if (!area.isNullOrBlank()) { where.append(" AND l.area = :ar"); params.addValue("ar", area) }
        if (!vehicleType.isNullOrBlank()) { where.append(" AND l.vehicle_type = :vt"); params.addValue("vt", vehicleType) }
        where.append(
            if (lat != null && lng != null) " ORDER BY distance_km NULLS LAST, l.created_at DESC"
            else " ORDER BY l.created_at DESC",
        )
        // Drivers never see the pickup OTP — they must obtain it from the passenger.
        return legsWhere(where.toString(), params, lat, lng).map { it.copy(pickupOtp = null) }
    }

    /** The pickable area gazetteer (Hyderabad tech corridor for the pilot). */
    fun areas(): List<Map<String, Any>> = db.queryForList(
        "SELECT name, lat, lng FROM areas ORDER BY name", MapSqlParameterSource(),
    ).map { mapOf("name" to it["name"]!!, "lat" to it["lat"]!!, "lng" to it["lng"]!!) }

    fun myClaims(driverId: Long) =
        legsWhere("l.claimed_by = :d ORDER BY l.claimed_at DESC", MapSqlParameterSource("d", driverId))
            .map { it.copy(pickupOtp = null) }

    /**
     * First-claim-wins. The verified+active driver gate is enforced INSIDE the atomic
     * UPDATE (EXISTS subquery), so the check and the lock are one indivisible operation.
     * Verified in isolation against real Postgres (50 concurrent claimers -> 1 winner).
     */
    @Transactional
    fun claim(driverId: Long, legId: Long): LegDto {
        // Pre-check only to return a precise 403 vs 409 message; the UPDATE re-checks atomically.
        val eligible = db.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
               WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED')""",
            MapSqlParameterSource("d", driverId), Boolean::class.java,
        ) ?: false
        if (!eligible) throw forbidden("driver not verified")

        val won = db.update(
            """UPDATE legs SET status='CLAIMED', claimed_by=:d, claimed_at=now(), pickup_otp=:otp, version=version+1
               WHERE id=:id AND status='OPEN'
                 AND EXISTS (SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
                             WHERE u.id=:d AND u.status='ACTIVE' AND p.kyc_status='VERIFIED')""",
            MapSqlParameterSource().addValue("d", driverId).addValue("id", legId).addValue("otp", newOtp()),
        )
        db.update(
            "INSERT INTO claims_audit(leg_id, driver_id, outcome) VALUES (:l,:d,:o)",
            MapSqlParameterSource().addValue("l", legId).addValue("d", driverId)
                .addValue("o", if (won == 1) "WON" else "LOST"),
        )
        if (won == 0) throw conflict("leg already taken")
        return legsWhere("l.id = :id", MapSqlParameterSource("id", legId)).first().copy(pickupOtp = null)
    }

    /** Coordinator marks a completed leg as settled (cash paid to the driver). Idempotent. */
    @Transactional
    fun markPaid(coordinatorId: Long, legId: Long) {
        val n = db.update(
            "UPDATE legs SET paid_at=now() WHERE id=:id AND coordinator_id=:c AND status='COMPLETED' AND paid_at IS NULL",
            MapSqlParameterSource().addValue("id", legId).addValue("c", coordinatorId),
        )
        if (n == 0) throw badRequest("leg not found, not yours, not completed, or already paid")
    }

    // --- rating (reputation touchpoint; per-trip immutable record) ---

    @Transactional
    fun rate(coordinatorId: Long, legId: Long, stars: Int, review: String?) {
        if (stars !in 1..5) throw badRequest("stars 1..5")
        val leg = db.queryForList(
            "SELECT coordinator_id, claimed_by, status FROM legs WHERE id=:id",
            MapSqlParameterSource("id", legId),
        ).firstOrNull() ?: throw badRequest("no such leg")
        if ((leg["coordinator_id"] as Number).toLong() != coordinatorId) throw forbidden("not your leg")
        if (leg["status"] != "COMPLETED") throw badRequest("can only rate a completed leg")
        val driverId = (leg["claimed_by"] as? Number)?.toLong() ?: throw badRequest("leg had no driver")
        db.update(
            """INSERT INTO ratings(leg_id, rater_id, ratee_id, stars, review)
               VALUES (:l,:r,:e,:s,:v)
               ON CONFLICT (leg_id, rater_id) DO NOTHING""",
            MapSqlParameterSource().addValue("l", legId).addValue("r", coordinatorId)
                .addValue("e", driverId).addValue("s", stars).addValue("v", review),
        )
    }

    // --- coordinator analytics (Insights tab) ---

    /** [days] limits to trips posted in the last N days; null = all time. */
    fun coordinatorStats(coordinatorId: Long, days: Int?): CoordinatorStatsDto {
        val params = MapSqlParameterSource("c", coordinatorId)
        // Only positive windows filter; anything else means "all time".
        val since = if (days != null && days > 0) {
            params.addValue("days", days)
            " AND created_at >= now() - make_interval(days => :days)"
        } else ""
        val agg = db.queryForList(
            """SELECT count(*) AS posted,
                      count(*) FILTER (WHERE claimed_by IS NOT NULL)                   AS claimed,
                      count(*) FILTER (WHERE status='COMPLETED')                        AS completed,
                      count(*) FILTER (WHERE status='CANCELLED')                        AS cancelled,
                      coalesce(sum(fare_paise) FILTER (WHERE paid_at IS NOT NULL),0)    AS total_paid,
                      coalesce(sum(fare_paise) FILTER (WHERE status='COMPLETED' AND paid_at IS NULL),0) AS outstanding
                 FROM legs WHERE coordinator_id = :c$since""",
            params,
        ).first()
        val posted = (agg["posted"] as Number).toInt()
        val claimed = (agg["claimed"] as Number).toInt()
        val topDrivers = db.query(
            """SELECT u.name AS name, count(*) AS trips
                 FROM legs l JOIN users u ON u.id = l.claimed_by
                WHERE l.coordinator_id = :c AND l.status = 'COMPLETED'${since.replace("created_at", "l.created_at")}
                GROUP BY u.name ORDER BY trips DESC LIMIT 5""",
            params,
        ) { rs, _ -> TopDriverDto(rs.getString("name") ?: "", rs.getInt("trips")) }
        return CoordinatorStatsDto(
            posted = posted,
            claimed = claimed,
            completed = (agg["completed"] as Number).toInt(),
            cancelled = (agg["cancelled"] as Number).toInt(),
            fillRatePct = if (posted == 0) 0 else (claimed * 100 / posted),
            totalPaidPaise = (agg["total_paid"] as Number).toLong(),
            outstandingPaise = (agg["outstanding"] as Number).toLong(),
            topDrivers = topDrivers,
        )
    }

    // --- helpers ---

    private fun legsWhere(
        where: String,
        params: MapSqlParameterSource,
        lat: Double? = null,
        lng: Double? = null,
    ): List<LegDto> = db.query(
        // distance_km: Haversine from the driver's coords to the leg's area centroid (null when
        // no coords supplied or the area isn't in the gazetteer). least(1.0,...) guards acos domain.
        """SELECT l.id, l.job_id, l.coordinator_id, j.office, j.shift, l.pickup, l.drop_point,
                  l.area, l.time_window, l.vehicle_type, l.fare_paise, l.seats, l.status,
                  l.claimed_by, u.name as claimed_by_name, l.trip_stage, l.paid_at, l.version,
                  l.passenger_name, l.passenger_phone, l.pickup_otp,
                  dp.trips_completed as claimed_by_trips, dp.no_shows as claimed_by_no_shows,
                  CASE WHEN CAST(:qlat AS double precision) IS NOT NULL AND a.lat IS NOT NULL THEN
                      6371 * acos(least(1.0,
                          cos(radians(:qlat)) * cos(radians(a.lat)) * cos(radians(a.lng) - radians(:qlng))
                          + sin(radians(:qlat)) * sin(radians(a.lat))))
                  END as distance_km
             FROM legs l
             JOIN jobs j ON j.id = l.job_id
             LEFT JOIN users u ON u.id = l.claimed_by
             LEFT JOIN driver_profiles dp ON dp.user_id = l.claimed_by
             LEFT JOIN areas a ON lower(a.name) = lower(l.area)
            WHERE $where""",
        params.addValue("qlat", lat, java.sql.Types.DOUBLE).addValue("qlng", lng, java.sql.Types.DOUBLE),
        LEG_MAPPER,
    )

    companion object {
        private val LEG_MAPPER = RowMapper { rs, _ ->
            LegDto(
                id = rs.getLong("id"),
                jobId = rs.getLong("job_id"),
                coordinatorId = rs.getLong("coordinator_id"),
                office = rs.getString("office"),
                shift = rs.getString("shift"),
                pickup = rs.getString("pickup"),
                drop = rs.getString("drop_point"),
                area = rs.getString("area"),
                timeWindow = rs.getString("time_window"),
                vehicleType = rs.getString("vehicle_type"),
                farePaise = rs.getLong("fare_paise"),
                seats = rs.getInt("seats"),
                status = rs.getString("status"),
                claimedBy = rs.getObject("claimed_by")?.let { (it as Number).toLong() },
                claimedByName = rs.getString("claimed_by_name"),
                tripStage = rs.getString("trip_stage"),
                paid = rs.getObject("paid_at") != null,
                distanceKm = rs.getObject("distance_km")?.let { (it as Number).toDouble() },
                claimedByTrips = rs.getObject("claimed_by_trips")?.let { (it as Number).toInt() },
                claimedByNoShows = rs.getObject("claimed_by_no_shows")?.let { (it as Number).toInt() },
                passengerName = rs.getString("passenger_name") ?: "",
                passengerPhone = rs.getString("passenger_phone") ?: "",
                pickupOtp = rs.getString("pickup_otp"),
                version = rs.getInt("version"),
            )
        }

        /** 4-digit pickup code (leading zeros kept). */
        private fun newOtp(): String = "%04d".format((0..9999).random())
    }
}
