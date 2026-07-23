package com.itcabs.core.network.dto

import kotlinx.serialization.Serializable

// Wire shapes for /api/v1 dispatch endpoints — mirror the backend DTOs exactly.

@Serializable
data class LegInputDto(
    val pickup: String,
    val drop: String,
    val area: String = "",
    val timeWindow: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val seats: Int = 1,
)

@Serializable
data class PostJobDto(
    val office: String,
    val shift: String,
    val legs: List<LegInputDto>,
)

@Serializable
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
    val claimedBy: Long? = null,
    val claimedByName: String? = null,
    val tripStage: String? = null,
    val paid: Boolean = false,
    val distanceKm: Double? = null,
    val claimedByTrips: Int? = null,
    val claimedByNoShows: Int? = null,
    val version: Int,
)

@Serializable
data class CoordinatorStatsDto(
    val posted: Int = 0,
    val claimed: Int = 0,
    val completed: Int = 0,
    val cancelled: Int = 0,
    val fillRatePct: Int = 0,
    val totalPaidPaise: Long = 0,
    val outstandingPaise: Long = 0,
    val topDrivers: List<TopDriverDto> = emptyList(),
)

@Serializable
data class TopDriverDto(val name: String, val trips: Int)

/** GET /areas — a pickable area with its centroid. */
@Serializable
data class AreaDto(val name: String, val lat: Double, val lng: Double)

@Serializable
data class StatusUpdateDto(val status: String)

@Serializable
data class StageUpdateDto(val stage: String)

@Serializable
data class RatingDto(val stars: Int, val review: String? = null)
