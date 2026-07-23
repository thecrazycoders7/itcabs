package com.itcabs.dispatch

import com.itcabs.identity.requireUserId
import com.itcabs.realtime.LegWebSocketHandler
import com.itcabs.push.PushService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

/** Saved-route templates: save, list, delete, and one-tap post. */
@RestController
@RequestMapping("/api/v1/templates")
class TemplateController(
    private val templates: TemplateService,
    private val events: LegWebSocketHandler,
    private val push: PushService,
) {
    @GetMapping
    fun list(req: HttpServletRequest) = templates.list(requireUserId(req))

    @PostMapping
    fun save(req: HttpServletRequest, @RequestBody body: TemplateInput) = templates.save(requireUserId(req), body)

    @DeleteMapping("/{id}")
    fun delete(req: HttpServletRequest, @PathVariable id: Long): Map<String, Any> {
        templates.delete(requireUserId(req), id)
        return mapOf("deleted" to true)
    }

    @PostMapping("/{id}/post")
    fun postFrom(req: HttpServletRequest, @PathVariable id: Long): List<LegDto> {
        val legs = templates.postFrom(requireUserId(req), id)
        legs.forEach { events.legChanged(it.id) }
        legs.firstOrNull()?.let { push.notifyDriversNewLeg(it.office) }
        return legs
    }
}
