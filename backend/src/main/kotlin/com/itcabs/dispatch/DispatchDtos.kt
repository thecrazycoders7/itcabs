package com.itcabs.dispatch

data class LegInput(
    val pickup: String,
    val drop: String,
    val area: String = "",
    val timeWindow: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val seats: Int = 1,
    val passengerName: String = "",
    val passengerPhone: String = "",
)

data class PostJobInput(
    val office: String,
    val shift: String,
    val legs: List<LegInput>,
    /** ISO-8601 instant; when in the future the legs stay hidden from the feed until then. */
    val publishAt: String? = null,
)

/** Edit an OPEN leg in place (fare typo, time change, …). Nulls leave a field unchanged. */
data class EditLegInput(
    val pickup: String? = null,
    val drop: String? = null,
    val area: String? = null,
    val timeWindow: String? = null,
    val vehicleType: String? = null,
    val farePaise: Long? = null,
    val seats: Int? = null,
    val passengerName: String? = null,
    val passengerPhone: String? = null,
)

data class AssignInput(val driverId: Long)
data class AvailabilityInput(val available: Boolean)
data class LocationInput(val lat: Double, val lng: Double)

data class LegDto(
    val id: Long,
    val jobId: Long,
    val coordinatorId: Long,
    val office: String,
    val shift: String,
    val pickup: String,
    val drop: String,
    val area: String,
    val timeWindow: String,
    val vehicleType: String,
    val farePaise: Long,
    val seats: Int,
    val status: String,
    val claimedBy: Long?,
    val claimedByName: String?,
    val tripStage: String? = null,
    val paid: Boolean = false,
    /** Km from the requesting driver to this leg's area centroid; null when unknown. */
    val distanceKm: Double? = null,
    val claimedByTrips: Int? = null,
    val claimedByNoShows: Int? = null,
    val passengerName: String = "",
    val passengerPhone: String = "",
    /** Set once claimed; the driver must enter it to start. Visible to the owning coordinator only. */
    val pickupOtp: String? = null,
    val version: Int,
)

/** A verified driver a coordinator can directly assign a trip to. */
data class VerifiedDriverDto(val id: Long, val name: String, val tripsCompleted: Int, val noShows: Int)

data class StatusUpdate(val status: String)
data class StageUpdate(val stage: String, val otp: String? = null)

/** A coordinator's own performance summary for the Insights tab. */
data class CoordinatorStatsDto(
    val posted: Int,
    val claimed: Int,
    val completed: Int,
    val cancelled: Int,
    val fillRatePct: Int,
    val totalPaidPaise: Long,
    val outstandingPaise: Long,
    val topDrivers: List<TopDriverDto>,
)

data class TopDriverDto(val name: String, val trips: Int)
data class RatingInput(val stars: Int, val review: String? = null)

/** A saved route the coordinator can re-post with one tap, optionally auto-posted daily. */
data class TemplateInput(
    val name: String,
    val office: String,
    val shift: String,
    val vehicleType: String = "",
    val legs: List<LegInput>,
    val recurring: Boolean = false,
)

data class TemplateDto(
    val id: Long,
    val name: String,
    val office: String,
    val shift: String,
    val vehicleType: String,
    val legs: List<LegInput>,
    val recurring: Boolean,
)
