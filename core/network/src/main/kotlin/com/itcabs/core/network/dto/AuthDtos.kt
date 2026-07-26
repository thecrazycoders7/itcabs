package com.itcabs.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes for /api/v1/auth/*. These mirror the backend DTOs exactly; domain mapping
// happens in :data so the domain layer never depends on these serialization types.

@Serializable
data class OtpRequestDto(val phone: String)

@Serializable
data class OtpVerifyDto(
    val phone: String,
    val code: String,
    val role: String? = null,
    val name: String? = null,
    val deviceId: String? = null,
)

@Serializable
data class RefreshDto(val refreshToken: String)

@Serializable
data class TokensDto(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val role: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val phone: String,
    val role: String,
    val name: String,
    val status: String,
)

/** /auth/otp/request returns {"sent": true}. */
@Serializable
data class SentDto(val sent: Boolean = false)

/** GET /auth/me — the domain user if onboarded, else onboarded=false. */
@Serializable
data class MeDto(
    val onboarded: Boolean = false,
    val id: Long? = null,
    val phone: String? = null,
    val email: String? = null,
    val role: String? = null,
    val name: String? = null,
    val status: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("coordinator_status") val coordinatorStatus: String? = null,
)

/** Generic "ignore the body, just tell me it succeeded" response. Unknown keys are ignored. */
@Serializable
data class OkDto(val ok: Boolean = false)

@Serializable
data class OnboardInputDto(
    val role: String,
    val name: String? = null,
    val companyName: String? = null,
    val officeAddress: String? = null,
    val officeLat: Double? = null,
    val officeLng: Double? = null,
    val officePlaceId: String? = null,
)

/** GET /auth/coordinator/company — saved company defaults to prefill new jobs. */
@Serializable
data class CoordinatorCompanyDto(
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("office_address") val officeAddress: String? = null,
    @SerialName("office_lat") val officeLat: Double? = null,
    @SerialName("office_lng") val officeLng: Double? = null,
    @SerialName("office_place_id") val officePlaceId: String? = null,
)

@Serializable
data class OnboardDto(
    val userId: Long,
    val role: String,
    val onboarded: Boolean = true,
    val coordinatorStatus: String? = null,
)

@Serializable
data class KycInputDto(
    val vehicleType: String,
    val vehicleReg: String,
    val aadhaarRef: String,
    val aadhaarMasked: String,
    val rcNumberMasked: String,
    val photoUrl: String,
)
