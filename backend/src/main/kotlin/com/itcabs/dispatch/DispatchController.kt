package com.itcabs.dispatch

import com.itcabs.identity.requireUserId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class DispatchController(private val dispatch: DispatchService) {

    // coordinator
    @PostMapping("/jobs")
    fun postJob(req: HttpServletRequest, @RequestBody body: PostJobInput) =
        dispatch.postJob(requireUserId(req), body)

    @GetMapping("/legs/mine")
    fun myLegs(req: HttpServletRequest) = dispatch.myLegs(requireUserId(req))

    @PatchMapping("/legs/{id}/status")
    fun setStatus(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: StatusUpdate) =
        dispatch.setStatus(requireUserId(req), id, body.status)

    @PostMapping("/legs/{id}/rating")
    fun rate(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: RatingInput) =
        dispatch.rate(requireUserId(req), id, body.stars, body.review)

    // driver
    @GetMapping("/legs")
    fun feed(@RequestParam(required = false) area: String?, @RequestParam(required = false) vehicleType: String?) =
        dispatch.feed(area, vehicleType)

    @PostMapping("/legs/{id}/claim")
    fun claim(req: HttpServletRequest, @PathVariable id: Long) =
        dispatch.claim(requireUserId(req), id)

    @GetMapping("/legs/claimed")
    fun myClaims(req: HttpServletRequest) = dispatch.myClaims(requireUserId(req))
}
