package com.itcabs.core.network.dto

import kotlinx.serialization.Serializable

// Wire shapes for /api/v1/company-jobs — mirror the backend DTOs.

@Serializable
data class StopInputDto(
    val employeeName: String,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: String? = null,
    val phone: String = "",
)

@Serializable
data class CompanyJobInputDto(
    val companyName: String,
    val tripType: String,
    val office: String = "",
    val officeAddress: String = "",
    val officeLat: Double? = null,
    val officeLng: Double? = null,
    val officePlaceId: String? = null,
    val pickupTime: String = "",
    val dropTime: String = "",
    val vehicleType: String = "",
    val vehicleAc: Boolean = true,
    val farePaise: Long,
    val publishAt: String? = null,
    val stops: List<StopInputDto>,
)

@Serializable
data class StopsUpdateDto(val stops: List<StopInputDto>)

@Serializable
data class StopPickupDto(val otp: String? = null)

@Serializable
data class StopDto(
    val id: Long,
    val employeeName: String,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: String? = null,
    val phone: String = "",
    val stopOrder: Int = 0,
    val pickedUp: Boolean = false,
    val pickupOtp: String? = null,
)

@Serializable
data class CompanyJobDto(
    val id: Long,
    val coordinatorId: Long,
    val companyName: String,
    val tripType: String,
    val office: String = "",
    val vehicleType: String = "",
    val farePaise: Long,
    val officeAddress: String = "",
    val officeLat: Double? = null,
    val officeLng: Double? = null,
    val pickupTime: String = "",
    val dropTime: String = "",
    val vehicleAc: Boolean = true,
    val status: String,
    val claimedBy: Long? = null,
    val claimedByName: String? = null,
    val paid: Boolean = false,
    val stops: List<StopDto> = emptyList(),
    val version: Int = 0,
)
