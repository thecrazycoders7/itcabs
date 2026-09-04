package com.itcabs.rides

import com.itcabs.identity.requireUserId
import com.itcabs.push.PushService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

/** Peer-to-peer carpooling endpoints: host a ride, search, book seats, run the trip. */
@RestController
@RequestMapping("/api/v1/rides")
class RideController(private val rides: RideService, private val push: PushService) {

    @PostMapping
    fun create(req: HttpServletRequest, @RequestBody body: RideInput) = rides.create(requireUserId(req), body)

    @GetMapping("/search")
    fun search(
        req: HttpServletRequest,
        @RequestParam(required = false) originLat: Double?,
        @RequestParam(required = false) originLng: Double?,
        @RequestParam(required = false) destLat: Double?,
        @RequestParam(required = false) destLng: Double?,
        @RequestParam(required = false) on: String?,
    ) = rides.search(requireUserId(req), originLat, originLng, destLat, destLng, on)

    @GetMapping("/mine")
    fun mine(req: HttpServletRequest) = rides.myRides(requireUserId(req))

    @GetMapping("/bookings")
    fun bookings(req: HttpServletRequest) = rides.myBookings(requireUserId(req))

    @GetMapping("/{id}")
    fun detail(req: HttpServletRequest, @PathVariable id: Long) = rides.detail(requireUserId(req), id)

    @PostMapping("/{id}/book")
    fun book(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: BookInput): Map<String, Any?> {
        val me = requireUserId(req)
        val ride = rides.book(me, id, body.seats)
        (ride["hostId"] as? Long)?.let { push.notifyUser(it, "New booking", "Someone booked a seat on your ride.") }
        return ride
    }

    @PostMapping("/{id}/cancel-booking")
    fun cancelBooking(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        rides.cancelBooking(requireUserId(req), id); return mapOf("ok" to true)
    }

    @PostMapping("/{id}/pickup")
    fun pickup(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: PickupInput): Map<String, Any> {
        rides.confirmPickup(requireUserId(req), id, body.riderId, body.otp); return mapOf("ok" to true)
    }

    @PostMapping("/{id}/status")
    fun status(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: RideStatusInput): Map<String, Any> {
        rides.setStatus(requireUserId(req), id, body.status.uppercase()); return mapOf("status" to body.status.uppercase())
    }
}

data class PickupInput(val riderId: Long, val otp: String)
data class RideStatusInput(val status: String)
