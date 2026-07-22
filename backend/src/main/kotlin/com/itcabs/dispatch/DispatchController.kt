package com.itcabs.dispatch

import com.itcabs.identity.requireUserId
import com.itcabs.realtime.LegWebSocketHandler
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class DispatchController(
    private val dispatch: DispatchService,
    private val events: LegWebSocketHandler,
) {

    // coordinator
    @PostMapping("/jobs")
    fun postJob(req: HttpServletRequest, @RequestBody body: PostJobInput): List<LegDto> {
        // Broadcast AFTER the @Transactional service call returns (committed), so a client that
        // re-fetches on the event sees the new rows.
        val legs = dispatch.postJob(requireUserId(req), body)
        legs.forEach { events.legChanged(it.id) }
        return legs
    }

    @GetMapping("/legs/mine")
    fun myLegs(req: HttpServletRequest) = dispatch.myLegs(requireUserId(req))

    @PatchMapping("/legs/{id}/status")
    fun setStatus(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: StatusUpdate) {
        dispatch.setStatus(requireUserId(req), id, body.status)
        events.legChanged(id)
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

    @GetMapping("/legs/claimed")
    fun myClaims(req: HttpServletRequest) = dispatch.myClaims(requireUserId(req))
}
