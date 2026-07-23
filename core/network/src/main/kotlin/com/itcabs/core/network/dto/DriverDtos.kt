package com.itcabs.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /driver/me — the authenticated driver's KYC status + vehicle. kycStatus is "NONE" if unsubmitted. */
@Serializable
data class DriverProfileDto(
    val kycStatus: String,
    val vehicleType: String? = null,
    val vehicleReg: String? = null,
)

/** GET /admin/drivers/pending — one KYC-review row. Snake keys mirror the raw SQL projection. */
@Serializable
data class PendingDriverDto(
    val id: Long,
    val name: String? = null,
    val email: String? = null,
    @SerialName("vehicle_type") val vehicleType: String? = null,
    @SerialName("vehicle_reg") val vehicleReg: String? = null,
    @SerialName("aadhaar_masked") val aadhaarMasked: String? = null,
    @SerialName("rc_number_masked") val rcNumberMasked: String? = null,
)
