package com.itcabs.domain.model

/** NONE = not submitted; PENDING = awaiting admin verify; VERIFIED = can claim; REJECTED = denied. */
enum class KycStatus { NONE, PENDING, VERIFIED, REJECTED }

data class DriverProfile(
    val kycStatus: KycStatus,
    val vehicleType: String?,
    val vehicleReg: String?,
)
