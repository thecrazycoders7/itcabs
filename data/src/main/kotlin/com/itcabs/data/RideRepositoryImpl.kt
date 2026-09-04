package com.itcabs.data

import com.itcabs.core.network.RideApi
import com.itcabs.core.network.dto.BookInputDto
import com.itcabs.core.network.dto.PickupInputDto
import com.itcabs.core.network.dto.RideDto
import com.itcabs.core.network.dto.RideInputDto
import com.itcabs.core.network.dto.RideStatusInputDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.NewRide
import com.itcabs.domain.model.Ride
import com.itcabs.domain.repository.RideRepository

class RideRepositoryImpl(private val api: RideApi) : RideRepository {
    override suspend fun create(input: NewRide): AppResult<Ride> =
        api.create(
            RideInputDto(
                origin = input.origin, originLat = input.originLat, originLng = input.originLng,
                destination = input.destination, destLat = input.destLat, destLng = input.destLng,
                departAt = input.departAt, totalSeats = input.totalSeats, pricePaise = input.pricePaise,
                carModel = input.carModel, womenOnly = input.womenOnly, notes = input.notes,
            ),
        ).asResult { it.toDomain() }

    override suspend fun search(originLat: Double?, originLng: Double?, destLat: Double?, destLng: Double?, on: String?): AppResult<List<Ride>> =
        api.search(originLat, originLng, destLat, destLng, on).asResult { list -> list.map { it.toDomain() } }

    override suspend fun myRides(): AppResult<List<Ride>> = api.mine().asResult { l -> l.map { it.toDomain() } }
    override suspend fun myBookings(): AppResult<List<Ride>> = api.bookings().asResult { l -> l.map { it.toDomain() } }
    override suspend fun detail(rideId: Long): AppResult<Ride> = api.detail(rideId).asResult { it.toDomain() }
    override suspend fun book(rideId: Long, seats: Int): AppResult<Ride> = api.book(rideId, BookInputDto(seats)).asResult { it.toDomain() }
    override suspend fun cancelBooking(rideId: Long): AppResult<Unit> = api.cancelBooking(rideId).asResult { }
    override suspend fun confirmPickup(rideId: Long, riderId: Long, otp: String): AppResult<Unit> =
        api.pickup(rideId, PickupInputDto(riderId, otp)).asResult { }
    override suspend fun setStatus(rideId: Long, status: String): AppResult<Unit> =
        api.status(rideId, RideStatusInputDto(status)).asResult { }
}

private fun RideDto.toDomain() = Ride(
    id = id, hostId = hostId, hostName = hostName ?: "", hostPhotoUrl = hostPhotoUrl,
    hostRating = hostRating, hostTrips = hostTrips,
    origin = origin, originLat = originLat, originLng = originLng,
    destination = destination, destLat = destLat, destLng = destLng,
    departAt = departAt, totalSeats = totalSeats, seatsLeft = seatsLeft, pricePaise = pricePaise,
    carModel = carModel, womenOnly = womenOnly, notes = notes, status = status,
    myBookingStatus = myBookingStatus, myOtp = myOtp,
)
