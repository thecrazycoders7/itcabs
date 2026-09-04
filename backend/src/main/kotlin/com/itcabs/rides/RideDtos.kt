package com.itcabs.rides

/** Host offers a ride. Times are ISO-8601; money in paise (per seat). */
data class RideInput(
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

/** Rider books N seats on a ride. */
data class BookInput(val seats: Int = 1)
