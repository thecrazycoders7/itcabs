package com.itcabs.core.network.dto

import kotlinx.serialization.Serializable

/** A carpool ride + host summary + seats left (keys mirror RideService.row() camelCase). */
@Serializable
data class RideDto(
    val id: Long = 0,
    val hostId: Long = 0,
    val hostName: String? = null,
    val hostPhotoUrl: String? = null,
    val hostRating: Double? = null,
    val hostTrips: Int = 0,
    val origin: String = "",
    val originLat: Double? = null,
    val originLng: Double? = null,
    val destination: String = "",
    val destLat: Double? = null,
    val destLng: Double? = null,
    val departAt: String? = null,
    val totalSeats: Int = 0,
    val seatsLeft: Int = 0,
    val pricePaise: Long = 0,
    val carModel: String = "",
    val womenOnly: Boolean = false,
    val notes: String = "",
    val status: String = "OPEN",
    val myBookingStatus: String? = null,
    val myOtp: String? = null,
)

@Serializable
data class RideInputDto(
    val origin: String,
    val originLat: Double? = null,
    val originLng: Double? = null,
    val destination: String,
    val destLat: Double? = null,
    val destLng: Double? = null,
    val departAt: String,
    val totalSeats: Int,
    val pricePaise: Long,
    val carModel: String? = null,
    val womenOnly: Boolean = false,
    val notes: String? = null,
)

@Serializable
data class RideRiderDto(
    val riderId: Long = 0,
    val riderName: String = "",
    val riderPhone: String? = null,
    val seats: Int = 1,
    val status: String = "CONFIRMED",
)

@Serializable
data class BookInputDto(val seats: Int = 1)

@Serializable
data class PickupInputDto(val riderId: Long, val otp: String)

@Serializable
data class RideStatusInputDto(val status: String)
