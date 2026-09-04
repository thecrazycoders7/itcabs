package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.NewRide
import com.itcabs.domain.model.Ride

/** Peer-to-peer carpooling: host rides, search, book seats, run the trip. */
interface RideRepository {
    suspend fun create(input: NewRide): AppResult<Ride>
    suspend fun search(originLat: Double?, originLng: Double?, destLat: Double?, destLng: Double?, on: String?): AppResult<List<Ride>>
    suspend fun myRides(): AppResult<List<Ride>>
    suspend fun myBookings(): AppResult<List<Ride>>
    suspend fun detail(rideId: Long): AppResult<Ride>
    suspend fun book(rideId: Long, seats: Int): AppResult<Ride>
    suspend fun cancelBooking(rideId: Long): AppResult<Unit>
    suspend fun confirmPickup(rideId: Long, riderId: Long, otp: String): AppResult<Unit>
    suspend fun setStatus(rideId: Long, status: String): AppResult<Unit>
}
