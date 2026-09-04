package com.itcabs.rides

import com.itcabs.shared.badRequest
import com.itcabs.shared.conflict
import com.itcabs.shared.forbidden
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.OffsetDateTime

/**
 * Peer-to-peer carpooling. A vetted member HOSTS a ride; other members BOOK seats. Seat accounting is
 * atomic (a conditional insert) so two riders can't overbook the last seat. Money is per-seat, in paise.
 */
@Service
class RideService(private val db: NamedParameterJdbcTemplate) {

    // Shared projection: ride columns + host profile + seats already taken. `:me` scopes "my booking".
    private val base = """
        SELECT r.*, u.name AS host_name,
               (SELECT photo_url FROM driver_profiles WHERE user_id=r.host_id) AS host_photo,
               (SELECT avg(stars)::float FROM ratings WHERE ratee_id=r.host_id) AS host_rating,
               (SELECT count(*) FROM rides x WHERE x.host_id=r.host_id AND x.status='COMPLETED') AS host_trips,
               coalesce((SELECT sum(seats) FROM ride_bookings b WHERE b.ride_id=r.id AND b.status='CONFIRMED'),0) AS taken,
               (SELECT status FROM ride_bookings b WHERE b.ride_id=r.id AND b.rider_id=:me) AS my_booking_status,
               (SELECT pickup_otp FROM ride_bookings b WHERE b.ride_id=r.id AND b.rider_id=:me) AS my_otp
          FROM rides r JOIN users u ON u.id=r.host_id
    """

    private fun row(m: Map<String, Any?>): Map<String, Any?> {
        val total = (m["total_seats"] as Number).toInt()
        val taken = (m["taken"] as Number).toInt()
        return mapOf(
            "id" to (m["id"] as Number).toLong(),
            "hostId" to (m["host_id"] as Number).toLong(),
            "hostName" to (m["host_name"] ?: ""),
            "hostPhotoUrl" to m["host_photo"],
            "hostRating" to m["host_rating"],
            "hostTrips" to ((m["host_trips"] as? Number)?.toInt() ?: 0),
            "origin" to m["origin"], "originLat" to m["origin_lat"], "originLng" to m["origin_lng"],
            "destination" to m["destination"], "destLat" to m["dest_lat"], "destLng" to m["dest_lng"],
            "departAt" to m["depart_at"].toString(),
            "totalSeats" to total,
            "seatsLeft" to (total - taken).coerceAtLeast(0),
            "pricePaise" to (m["price_paise"] as Number).toLong(),
            "carModel" to (m["car_model"] ?: ""),
            "womenOnly" to (m["women_only"] as? Boolean ?: false),
            "notes" to (m["notes"] ?: ""),
            "status" to m["status"],
            "myBookingStatus" to m["my_booking_status"],
            "myOtp" to m["my_otp"],
        )
    }

    fun create(hostId: Long, input: RideInput): Map<String, Any?> {
        // Host must be a vetted member (KYC-verified + phone-verified) — they carry strangers.
        val vetted = db.queryForObject(
            """SELECT EXISTS(SELECT 1 FROM users u JOIN driver_profiles p ON p.user_id=u.id
                 WHERE u.id=:h AND u.status='ACTIVE' AND p.kyc_status='VERIFIED' AND u.phone_verified)""",
            MapSqlParameterSource("h", hostId), Boolean::class.java,
        ) ?: false
        if (!vetted) throw forbidden("Verify your phone + complete KYC before offering rides.")
        if (input.womenOnly && genderOf(hostId) != "FEMALE") throw forbidden("Only women can host a women-only ride.")
        if (input.origin.isBlank() || input.destination.isBlank()) throw badRequest("origin and destination required")
        if (input.totalSeats !in 1..6) throw badRequest("seats must be 1–6")
        if (input.pricePaise < 0) throw badRequest("price must be >= 0")
        val depart = runCatching { OffsetDateTime.parse(input.departAt) }.getOrElse { throw badRequest("departAt must be ISO-8601") }
        if (depart.isBefore(OffsetDateTime.now())) throw badRequest("departure must be in the future")
        val id = db.queryForObject(
            """INSERT INTO rides(host_id,origin,origin_lat,origin_lng,destination,dest_lat,dest_lng,
                                 depart_at,total_seats,price_paise,car_model,women_only,notes)
               VALUES (:h,:o,:olat,:olng,:d,:dlat,:dlng,:dep,:seats,:price,:car,:wo,:notes) RETURNING id""",
            MapSqlParameterSource().addValue("h", hostId).addValue("o", input.origin)
                .addValue("olat", input.originLat).addValue("olng", input.originLng)
                .addValue("d", input.destination).addValue("dlat", input.destLat).addValue("dlng", input.destLng)
                .addValue("dep", Timestamp.from(depart.toInstant()))
                .addValue("seats", input.totalSeats).addValue("price", input.pricePaise)
                .addValue("car", input.carModel ?: "").addValue("wo", input.womenOnly).addValue("notes", input.notes ?: ""),
            Long::class.java,
        )!!
        return detail(hostId, id)
    }

    fun detail(me: Long, rideId: Long): Map<String, Any?> =
        db.queryForList("$base WHERE r.id=:id", MapSqlParameterSource().addValue("id", rideId).addValue("me", me))
            .firstOrNull()?.let(::row) ?: throw badRequest("ride not found")

    /** Search open, future rides with seats left, optionally near origin/dest (rough box) and on a date. */
    fun search(me: Long, oLat: Double?, oLng: Double?, dLat: Double?, dLng: Double?, on: String?): List<Map<String, Any?>> {
        val p = MapSqlParameterSource().addValue("me", me)
            .addValue("olat", oLat).addValue("olng", oLng).addValue("dlat", dLat).addValue("dlng", dLng)
            .addValue("on", on)
        // ponytail: bounding-box proximity (~15km) instead of PostGIS — plenty for a city pilot.
        val sql = "$base WHERE r.status='OPEN' AND r.depart_at > now()" +
            " AND (:olat IS NULL OR (r.origin_lat IS NOT NULL AND abs(r.origin_lat-:olat)<0.15 AND abs(r.origin_lng-:olng)<0.15))" +
            " AND (:dlat IS NULL OR (r.dest_lat IS NOT NULL AND abs(r.dest_lat-:dlat)<0.15 AND abs(r.dest_lng-:dlng)<0.15))" +
            " AND (CAST(:on AS date) IS NULL OR r.depart_at::date = CAST(:on AS date))" +
            " ORDER BY r.depart_at"
        return db.queryForList(sql, p).map(::row).filter { (it["seatsLeft"] as Int) > 0 }
    }

    fun myRides(hostId: Long): List<Map<String, Any?>> =
        db.queryForList("$base WHERE r.host_id=:me ORDER BY r.depart_at DESC", MapSqlParameterSource("me", hostId)).map(::row)

    fun myBookings(riderId: Long): List<Map<String, Any?>> =
        db.queryForList(
            "$base JOIN ride_bookings mb ON mb.ride_id=r.id AND mb.rider_id=:me WHERE mb.status<>'CANCELLED' ORDER BY r.depart_at DESC",
            MapSqlParameterSource("me", riderId),
        ).map(::row)

    /** The host's view of who booked their ride (name + phone to coordinate + confirm pickup). */
    fun riders(hostId: Long, rideId: Long): List<Map<String, Any?>> {
        val owns = db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM rides WHERE id=:id AND host_id=:h)",
            MapSqlParameterSource().addValue("id", rideId).addValue("h", hostId), Boolean::class.java,
        ) ?: false
        if (!owns) throw forbidden("not your ride")
        return db.queryForList(
            """SELECT b.rider_id, u.name AS rider_name, u.phone AS rider_phone, b.seats, b.status
                 FROM ride_bookings b JOIN users u ON u.id=b.rider_id
                WHERE b.ride_id=:id AND b.status<>'CANCELLED' ORDER BY b.created_at""",
            MapSqlParameterSource("id", rideId),
        ).map {
            mapOf(
                "riderId" to (it["rider_id"] as Number).toLong(),
                "riderName" to (it["rider_name"] ?: ""),
                "riderPhone" to it["rider_phone"],
                "seats" to (it["seats"] as Number).toInt(),
                "status" to it["status"],
            )
        }
    }

    /** Book seats atomically: succeeds only if the ride is OPEN and enough seats remain. */
    @Transactional
    fun book(riderId: Long, rideId: Long, seats: Int): Map<String, Any?> {
        if (seats < 1) throw badRequest("at least one seat")
        val phoneOk = db.queryForObject(
            "SELECT coalesce(phone_verified,false) FROM users WHERE id=:r", MapSqlParameterSource("r", riderId), Boolean::class.java,
        ) ?: false
        if (!phoneOk) throw forbidden("Verify your phone before booking a ride.")
        val ride = db.queryForList("SELECT host_id, women_only FROM rides WHERE id=:id", MapSqlParameterSource("id", rideId))
            .firstOrNull() ?: throw badRequest("ride not found")
        if ((ride["host_id"] as Number).toLong() == riderId) throw badRequest("you can't book your own ride")
        if ((ride["women_only"] as? Boolean == true) && genderOf(riderId) != "FEMALE")
            throw forbidden("This is a women-only ride.")
        val n = db.update(
            """INSERT INTO ride_bookings(ride_id, rider_id, seats, status, pickup_otp)
               SELECT :id, :r, :s, 'CONFIRMED', :otp
                 FROM rides WHERE id=:id AND status='OPEN'
                   AND :s <= total_seats - coalesce(
                       (SELECT sum(seats) FROM ride_bookings WHERE ride_id=:id AND status='CONFIRMED'),0)
               ON CONFLICT (ride_id, rider_id) DO NOTHING""",
            MapSqlParameterSource().addValue("id", rideId).addValue("r", riderId)
                .addValue("s", seats).addValue("otp", newOtp()),
        )
        if (n == 0) throw conflict("Couldn't book — not enough seats, ride closed, or you already booked it.")
        markFullIfNeeded(rideId)
        return detail(riderId, rideId)
    }

    fun cancelBooking(riderId: Long, rideId: Long) {
        db.update(
            "UPDATE ride_bookings SET status='CANCELLED' WHERE ride_id=:id AND rider_id=:r AND status='CONFIRMED'",
            MapSqlParameterSource().addValue("id", rideId).addValue("r", riderId),
        )
        // A cancellation frees a seat — reopen a FULL ride.
        db.update("UPDATE rides SET status='OPEN' WHERE id=:id AND status='FULL'", MapSqlParameterSource("id", rideId))
    }

    /** Host confirms a rider boarded, using the rider's pickup OTP. */
    fun confirmPickup(hostId: Long, rideId: Long, riderId: Long, otp: String) {
        val n = db.update(
            """UPDATE ride_bookings SET status='COMPLETED'
               WHERE ride_id=:id AND rider_id=:r AND status='CONFIRMED' AND pickup_otp=:otp
                 AND EXISTS (SELECT 1 FROM rides WHERE id=:id AND host_id=:h)""",
            MapSqlParameterSource().addValue("id", rideId).addValue("r", riderId).addValue("h", hostId).addValue("otp", otp.trim()),
        )
        if (n == 0) throw badRequest("wrong code, not your ride, or already boarded")
    }

    fun setStatus(hostId: Long, rideId: Long, status: String) {
        if (status !in setOf("STARTED", "COMPLETED", "CANCELLED")) throw badRequest("bad status")
        val n = db.update(
            "UPDATE rides SET status=:s, version=version+1 WHERE id=:id AND host_id=:h AND status NOT IN ('COMPLETED','CANCELLED')",
            MapSqlParameterSource().addValue("s", status).addValue("id", rideId).addValue("h", hostId),
        )
        if (n == 0) throw forbidden("not your ride, or already finished")
    }

    private fun markFullIfNeeded(rideId: Long) {
        db.update(
            """UPDATE rides SET status='FULL'
               WHERE id=:id AND status='OPEN'
                 AND total_seats <= coalesce((SELECT sum(seats) FROM ride_bookings WHERE ride_id=:id AND status='CONFIRMED'),0)""",
            MapSqlParameterSource("id", rideId),
        )
    }

    /** Two-way rating after a completed ride: rider rates host, host rates each boarded rider. */
    fun rate(raterId: Long, rideId: Long, rateeId: Long, stars: Int, review: String?) {
        if (stars !in 1..5) throw badRequest("stars must be 1–5")
        if (rateeId == raterId) throw badRequest("you can't rate yourself")
        val ride = db.queryForList("SELECT host_id, status FROM rides WHERE id=:id", MapSqlParameterSource("id", rideId))
            .firstOrNull() ?: throw badRequest("ride not found")
        if (ride["status"] != "COMPLETED") throw badRequest("you can rate once the ride is completed")
        val hostId = (ride["host_id"] as Number).toLong()
        fun isRider(u: Long) = db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ride_bookings WHERE ride_id=:id AND rider_id=:u AND status IN ('CONFIRMED','COMPLETED'))",
            MapSqlParameterSource().addValue("id", rideId).addValue("u", u), Boolean::class.java,
        ) ?: false
        val validPair = (raterId == hostId && isRider(rateeId)) || (isRider(raterId) && rateeId == hostId)
        if (!validPair) throw forbidden("you can only rate someone you shared this ride with")
        // Idempotent: replace any prior rating for this (ride, rater, ratee).
        db.update(
            "DELETE FROM ratings WHERE ride_id=:ride AND rater_id=:rater AND ratee_id=:ratee",
            MapSqlParameterSource().addValue("ride", rideId).addValue("rater", raterId).addValue("ratee", rateeId),
        )
        db.update(
            "INSERT INTO ratings(ride_id, rater_id, ratee_id, stars, review) VALUES (:ride,:rater,:ratee,:s,:rev)",
            MapSqlParameterSource().addValue("ride", rideId).addValue("rater", raterId)
                .addValue("ratee", rateeId).addValue("s", stars).addValue("rev", review),
        )
    }

    private fun genderOf(userId: Long): String? = db.queryForList(
        "SELECT gender FROM users WHERE id=:u", MapSqlParameterSource("u", userId),
    ).firstOrNull()?.get("gender") as? String

    private fun newOtp(): String = "%04d".format((0..9999).random())
}
