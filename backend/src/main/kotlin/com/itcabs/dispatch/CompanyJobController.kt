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

    @PatchMapping("/{id}/status")
    fun setStatus(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: StatusUpdate) {
        val driverId = jobs.setStatus(requireUserId(req), id, body.status)
        events.legChanged(id)
        if (body.status == "CANCELLED" && driverId != null)
            push.notifyUser(driverId, "Trip cancelled", "A company trip you took was cancelled.")
    }

    @PostMapping("/{id}/assign")
    fun assign(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: CompanyAssignInput): CompanyJobDto {
        val job = jobs.assign(requireUserId(req), id, body.driverId)
        events.legChanged(id)
        push.notifyUser(body.driverId, "Trip assigned to you", "You've been assigned a ${job.companyName} ${job.tripType.lowercase()} trip.")
        return job
    }

    // driver
    @GetMapping("/feed")
    fun feed() = jobs.feed()

    @GetMapping("/claimed")
    fun myTrips(req: HttpServletRequest) = jobs.myTrips(requireUserId(req))

    @PostMapping("/{id}/claim")
    fun claim(req: HttpServletRequest, @PathVariable id: Long): CompanyJobDto {
        val job = jobs.claim(requireUserId(req), id)
        events.legChanged(id)
        push.notifyUser(job.coordinatorId, "Trip claimed", "${job.claimedByName ?: "A driver"} took your ${job.companyName} trip.")
        return job
    }

    /** Driver confirms pickup at a stop with the employee's OTP. */
    @PostMapping("/stops/{stopId}/pickup")
    fun stopPickup(req: HttpServletRequest, @PathVariable stopId: Long, @RequestBody body: StopPickupInput): Map<String, Any> {
        jobs.confirmStopPickup(requireUserId(req), stopId, body.otp)
        return mapOf("ok" to true)
    }
}
