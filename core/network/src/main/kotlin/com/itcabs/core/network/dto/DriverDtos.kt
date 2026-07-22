package com.itcabs.core.network.dto

import kotlinx.serialization.Serializable

/** GET /driver/me — the authenticated driver's KYC status + vehicle. kycStatus is "NONE" if unsubmitted. */
@Serializable
data class DriverProfileDto(
    val kycStatus: String,
    val vehicleType: String? = null,
    val vehicleReg: String? = null,
)
