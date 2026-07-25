package com.itcabs.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /driver/me — the authenticated driver's KYC status + vehicle. kycStatus is "NONE" if unsubmitted. */
@Serializable
data class DriverProfileDto(
    val kycStatus: String,
    val vehicleType: String? = null,
    val vehicleReg: String? = null,
    val tripsCompleted: Int = 0,
    val noShows: Int = 0,
    val rejectionReason: String? = null,
    val available: Boolean = true,
    val avgRating: Double? = null,
    val ratingCount: Int = 0,
    val phone: String? = null,
    val phoneVerified: Boolean = false,
)

@Serializable
data class PhoneVerifyDto(val idToken: String)

/** Register one uploaded KYC document (object already in the private bucket). */
@Serializable
data class KycDocInputDto(val docType: String, val storagePath: String)

/** GET /admin/coordinators/pending — a coordinator awaiting approval. */
@Serializable
data class PendingCoordinatorDto(val id: Long, val name: String? = null, val email: String? = null)

/** GET /driver/kyc/documents — one uploaded doc + its review state. */
@Serializable
data class KycDocDto(
    @SerialName("doc_type") val docType: String,
    @SerialName("storage_path") val storagePath: String,
    val status: String = "UPLOADED",
    @SerialName("reject_reason") val rejectReason: String? = null,
)

@Serializable
data class AvailabilityDto(val available: Boolean)

/** GET /admin/drivers — roster with status for block/unblock. */
@Serializable
data class AdminDriverDto(
    val id: Long,
    val name: String? = null,
    val status: String = "ACTIVE",
    val kycStatus: String? = null,
    val tripsCompleted: Int = 0,
    val noShows: Int = 0,
)

/** GET /drivers/{id}/profile — a driver's public profile for the coordinator. */
@Serializable
data class DriverPublicDto(
    val id: Long,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val vehicleType: String? = null,
    val vehicleReg: String? = null,
    val kycStatus: String? = null,
    val tripsCompleted: Int = 0,
    val noShows: Int = 0,
    val photoUrl: String? = null,
    val avgRating: Double? = null,
    val ratingCount: Int = 0,
)

@Serializable
data class RejectInputDto(val reason: String? = null)

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
