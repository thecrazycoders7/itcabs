package com.itcabs.domain.model

enum class TripType { PICKUP, DROP }

/** One employee stop on a multi-stop job: their location + a per-stop pickup OTP (coordinator-only). */
data class JobStop(
    val id: Long,
    val employeeName: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val placeId: String?,
    val phone: String,
    val stopOrder: Int,
    val pickedUp: Boolean,
    val pickupOtp: String? = null,
)

/** A multi-stop corporate job: one company + trip type + ordered stops, served by one driver. */
data class CompanyJob(
    val id: Long,
    val coordinatorId: Long,
    val companyName: String,
    val tripType: TripType,
    val office: String,
    val vehicleType: String,
    val farePaise: Long,
    val status: LegStatus,       // reuse OPEN/CLAIMED/CONFIRMED/COMPLETED/CANCELLED
    val claimedBy: Long?,
    val claimedByName: String?,
    val stops: List<JobStop>,
    val version: Int,
)

/** Coordinator input for a new stop. Coords come from the area picker (or Places, when enabled). */
data class NewStop(
    val employeeName: String,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: String? = null,
    val phone: String = "",
)

data class NewCompanyJob(
    val companyName: String,
    val tripType: TripType,
    val office: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val publishAt: String? = null,
    val stops: List<NewStop>,
)
