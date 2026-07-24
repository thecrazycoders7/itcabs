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
    val passengerName: String = "",
    val passengerPhone: String = "",
)

@Serializable
data class PostJobDto(
    val office: String,
    val shift: String,
    val legs: List<LegInputDto>,
    val publishAt: String? = null,
)

@Serializable
data class EditLegDto(
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

@Serializable
data class AssignDto(val driverId: Long)

@Serializable
data class VerifiedDriverDto(val id: Long, val name: String, val tripsCompleted: Int = 0, val noShows: Int = 0)

@Serializable
data class TemplateInputDto(
    val name: String,
    val office: String,
    val shift: String,
    val vehicleType: String = "",
    val legs: List<LegInputDto>,
    val recurring: Boolean = false,
)

@Serializable
data class TemplateDto(
    val id: Long,
    val name: String,
    val office: String,
    val shift: String,
    val vehicleType: String = "",
    val legs: List<LegInputDto> = emptyList(),
    val recurring: Boolean = false,
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
    val passengerName: String = "",
    val passengerPhone: String = "",
    val pickupOtp: String? = null,
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
data class LocationDto(val lat: Double, val lng: Double)

/** GET /legs/{id}/driver-location — the claimed driver's latest point (empty object when none). */
@Serializable
data class DriverLocationDto(val lat: Double? = null, val lng: Double? = null, val updatedAt: String? = null)

@Serializable
data class StatusUpdateDto(val status: String)

@Serializable
data class StageUpdateDto(val stage: String, val otp: String? = null)

@Serializable
data class RatingDto(val stars: Int, val review: String? = null)
