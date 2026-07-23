package com.itcabs.domain.model

/** NONE = not submitted; PENDING = awaiting admin verify; VERIFIED = can claim; REJECTED = denied. */
enum class KycStatus { NONE, PENDING, VERIFIED, REJECTED }

data class DriverProfile(
    val kycStatus: KycStatus,
    val vehicleType: String?,
    val vehicleReg: String?,
    val tripsCompleted: Int = 0,
    val noShows: Int = 0,
    val rejectionReason: String? = null,
)

/** A driver awaiting KYC approval, shown in the admin review queue. */
data class PendingDriver(
    val id: Long,
    val name: String,
    val email: String?,
    val vehicleType: String?,
    val vehicleReg: String?,
    val aadhaarMasked: String?,
    val rcNumberMasked: String?,
)
