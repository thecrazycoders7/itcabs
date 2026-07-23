package com.itcabs.dispatch

import com.itcabs.identity.requireUserId
import com.itcabs.push.PushService
import com.itcabs.realtime.LegWebSocketHandler
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class DispatchController(
    private val dispatch: DispatchService,
    private val events: LegWebSocketHandler,
    private val push: PushService,
) {

    // coordinator
    @PostMapping("/jobs")
    fun postJob(req: HttpServletRequest, @RequestBody body: PostJobInput): List<LegDto> {
        // Broadcast AFTER the @Transactional service call returns (committed), so a client that
        // re-fetches on the event sees the new rows.
        val legs = dispatch.postJob(requireUserId(req), body)
        legs.forEach { events.legChanged(it.id) }
        push.notifyDriversNewLeg(body.office) // wake drivers whose app is backgrounded (WS covers foreground)
        return legs
    }

    @PostMapping("/jobs/{jobId}/repost")
    fun repost(req: HttpServletRequest, @PathVariable jobId: Long): List<LegDto> {
        val legs = dispatch.repostJob(requireUserId(req), jobId)
        legs.forEach { events.legChanged(it.id) }
        return legs
    }

    @GetMapping("/legs/mine")
    fun myLegs(req: HttpServletRequest) = dispatch.myLegs(requireUserId(req))

    @GetMapping("/coordinator/stats")
    fun coordinatorStats(req: HttpServletRequest) = dispatch.coordinatorStats(requireUserId(req))

    @PatchMapping("/legs/{id}/status")
    fun setStatus(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: StatusUpdate) {
        dispatch.setStatus(requireUserId(req), id, body.status)
        events.legChanged(id)
    }

    /** Coordinator reports a no-show: dings the driver's reliability and reopens the leg. */
    @PostMapping("/legs/{id}/no-show")
    fun noShow(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        dispatch.markNoShow(requireUserId(req), id)
        events.legChanged(id)              // it's OPEN again → feeds refresh
        push.notifyDriversNewLeg("a reopened trip") // wake drivers about the now-available trip
        return mapOf("reopened" to true)
    }

    @PostMapping("/legs/{id}/paid")
    fun markPaid(req: HttpServletRequest, @PathVariable id: Long) {
        dispatch.markPaid(requireUserId(req), id)
        events.legChanged(id) // driver's trip list flips to "Paid" live
    }

    @PostMapping("/legs/{id}/rating")
    fun rate(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: RatingInput) =
        dispatch.rate(requireUserId(req), id, body.stars, body.review)

    // driver
    @GetMapping("/legs")
    fun feed(@RequestParam(required = false) area: String?, @RequestParam(required = false) vehicleType: String?) =
        dispatch.feed(area, vehicleType)

    @PostMapping("/legs/{id}/claim")
    fun claim(req: HttpServletRequest, @PathVariable id: Long): LegDto {
        val leg = dispatch.claim(requireUserId(req), id)
        events.legChanged(id)
        return leg
    }

    /** Driver reports live trip progress (EN_ROUTE / ARRIVED / STARTED). */
    @PostMapping("/legs/{id}/stage")
    fun setStage(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: StageUpdate) {
        dispatch.setStage(requireUserId(req), id, body.stage)
        events.legChanged(id)
    }

    @GetMapping("/legs/claimed")
    fun myClaims(req: HttpServletRequest) = dispatch.myClaims(requireUserId(req))
}
