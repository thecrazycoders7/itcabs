package com.itcabs.dispatch

data class LegInput(
    val pickup: String,
    val drop: String,
    val area: String = "",
    val timeWindow: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val seats: Int = 1,
)

data class PostJobInput(
    val office: String,
    val shift: String,
    val legs: List<LegInput>,
)

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
    val version: Int,
)

data class StatusUpdate(val status: String)
data class StageUpdate(val stage: String)

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
