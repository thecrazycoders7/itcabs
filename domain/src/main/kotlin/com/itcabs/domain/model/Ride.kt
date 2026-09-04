package com.itcabs.domain.model

/** A carpool ride offered by a host, with seats bookable by riders. Money in paise (per seat). */
data class Ride(
    val id: Long,
    val hostId: Long,
    val hostName: String,
    val hostPhotoUrl: String?,
    val hostRating: Double?,
    val hostTrips: Int,
    val origin: String,
    val originLat: Double?,
    val originLng: Double?,
    val destination: String,
    val destLat: Double?,
    val destLng: Double?,
    val departAt: String?,
    val totalSeats: Int,
    val seatsLeft: Int,
    val pricePaise: Long,
    val carModel: String,
    val womenOnly: Boolean,
    val notes: String,
    val status: String,
    val myBookingStatus: String?,   // null / CONFIRMED / COMPLETED / CANCELLED
    val myOtp: String?,             // this rider's pickup code, if booked
)

/** A rider who booked the host's ride. */
data class RideRider(
    val riderId: Long,
    val riderName: String,
    val riderPhone: String?,
    val seats: Int,
    val status: String,
)

/** A ride a host is about to offer. */
data class NewRide(
    val origin: String,
    val originLat: Double?,
    val originLng: Double?,
    val destination: String,
    val destLat: Double?,
    val destLng: Double?,
    val departAt: String,
    val totalSeats: Int,
    val pricePaise: Long,
    val carModel: String?,
    val womenOnly: Boolean,
    val notes: String?,
)
