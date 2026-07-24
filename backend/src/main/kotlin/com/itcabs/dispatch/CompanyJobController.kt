package com.itcabs.dispatch

import com.itcabs.identity.requireUserId
import com.itcabs.push.PushService
import com.itcabs.realtime.LegWebSocketHandler
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

/** Multi-stop corporate jobs (company + ordered employee stops, one driver). Coexists with /legs. */
@RestController
@RequestMapping("/api/v1/company-jobs")
class CompanyJobController(
    private val jobs: CompanyJobService,
    private val events: LegWebSocketHandler,
    private val push: PushService,
) {
    // coordinator
    @PostMapping
    fun create(req: HttpServletRequest, @RequestBody body: CompanyJobInput): CompanyJobDto {
        val job = jobs.create(requireUserId(req), body)
        events.legChanged(job.id)
        push.notifyDriversNewLeg(job.companyName)
        return job
    }

    @GetMapping("/mine")
    fun mine(req: HttpServletRequest) = jobs.myJobs(requireUserId(req))

    @PutMapping("/{id}/stops")
    fun replaceStops(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: StopsUpdate): Map<String, Any> {
        jobs.replaceStops(requireUserId(req), id, body.stops)
        events.legChanged(id)
        return mapOf("ok" to true)
    }

    /** Full edit while OPEN (before a driver accepts): job fields + stops. */
    @PatchMapping("/{id}")
    fun edit(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: CompanyJobInput): CompanyJobDto {
        val job = jobs.edit(requireUserId(req), id, body)
        events.legChanged(id)
        return job
    }

    @PatchMapping("/{id}/status")
    fun setStatus(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: StatusUpdate) {
        val driverId = jobs.setStatus(requireUserId(req), id, body.status)
        events.legChanged(id)
        if (body.status == "CANCELLED" && driverId != null)
            push.notifyUser(driverId, "Trip cancelled", "A company trip you took was cancelled.")
    }

    // Manual assignment removed (spec 5): drivers self-claim eligible jobs only.

    @PostMapping("/{id}/paid")
    fun markPaid(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        jobs.markPaid(requireUserId(req), id); events.legChanged(id); return mapOf("ok" to true)
    }

    @PostMapping("/{id}/no-show")
    fun noShow(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        jobs.markNoShow(requireUserId(req), id)
        events.legChanged(id)
        push.notifyDriversNewLeg("a reopened company trip")
        return mapOf("reopened" to true)
    }

    @PostMapping("/{id}/release")
    fun release(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        jobs.releaseTrip(requireUserId(req), id); events.legChanged(id); push.notifyDriversNewLeg("a released company trip"); return mapOf("released" to true)
    }

    @PostMapping("/{id}/complete")
    fun complete(req: HttpServletRequest, @PathVariable id: Long) {
        jobs.driverComplete(requireUserId(req), id); events.legChanged(id)
    }

    /** Coordinator reads the claimed driver's live location for the company route map. */
    @GetMapping("/{id}/driver-location")
    fun driverLocation(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any?> =
        jobs.driverLocation(requireUserId(req), id) ?: emptyMap()

    // driver
    @GetMapping("/feed")
    fun feed(req: HttpServletRequest) = jobs.feed(requireUserId(req))

    @GetMapping("/claimed")
    fun myTrips(req: HttpServletRequest) = jobs.myTrips(requireUserId(req))

    @PostMapping("/{id}/claim")
    fun claim(req: HttpServletRequest, @PathVariable id: Long): CompanyJobDto {
        val job = jobs.claim(requireUserId(req), id)
        events.legChanged(id)
        push.notifyUser(job.coordinatorId, "Trip claimed", "${job.claimedByName ?: "A driver"} took your ${job.companyName} trip.", route = "coordinator_company")
        return job
    }

    /** Driver confirms pickup at a stop with the employee's OTP. */
    @PostMapping("/stops/{stopId}/pickup")
    fun stopPickup(req: HttpServletRequest, @PathVariable stopId: Long, @RequestBody body: StopPickupInput): Map<String, Any> {
        jobs.confirmStopPickup(requireUserId(req), stopId, body.otp)
        return mapOf("ok" to true)
    }
}
