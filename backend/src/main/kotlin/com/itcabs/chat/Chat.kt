package com.itcabs.chat

import com.itcabs.identity.requireUserId
import com.itcabs.realtime.LegWebSocketHandler
import com.itcabs.shared.badRequest
import com.itcabs.shared.forbidden
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*

data class MessageInput(val body: String)
data class MessageDto(val id: Long, val legId: Long, val senderId: Long, val body: String, val createdAt: String)

/**
 * In-app chat (M7): coordinator ↔ claiming driver, scoped to one leg. Keeps phone numbers private —
 * only the leg's two participants can read or post. Live delivery reuses the WebSocket (a 'message'
 * event nudges an open chat to re-fetch), same pattern as leg updates.
 */
@RestController
@RequestMapping("/api/v1")
class ChatController(
    private val db: NamedParameterJdbcTemplate,
    private val events: LegWebSocketHandler,
) {
    @GetMapping("/legs/{id}/messages")
    fun list(req: HttpServletRequest, @PathVariable id: Long): List<MessageDto> {
        requireParticipant(requireUserId(req), id)
        return db.query(
            "SELECT id, leg_id, sender_id, body, created_at FROM leg_messages WHERE leg_id=:l ORDER BY created_at",
            MapSqlParameterSource("l", id),
        ) { rs, _ ->
            MessageDto(
                rs.getLong("id"), rs.getLong("leg_id"), rs.getLong("sender_id"),
                rs.getString("body"), rs.getTimestamp("created_at").toInstant().toString(),
            )
        }
    }

    @PostMapping("/legs/{id}/messages")
    fun send(req: HttpServletRequest, @PathVariable id: Long, @RequestBody body: MessageInput): MessageDto {
        val uid = requireUserId(req)
        requireParticipant(uid, id)
        if (body.body.isBlank()) throw badRequest("message body required")
        val row = db.queryForMap(
            """INSERT INTO leg_messages(leg_id, sender_id, body) VALUES (:l,:s,:b)
               RETURNING id, leg_id, sender_id, body, created_at""",
            MapSqlParameterSource().addValue("l", id).addValue("s", uid).addValue("b", body.body.trim()),
        )
        events.messagePosted(id)
        return MessageDto(
            (row["id"] as Number).toLong(), (row["leg_id"] as Number).toLong(),
            (row["sender_id"] as Number).toLong(), row["body"] as String,
            (row["created_at"] as java.sql.Timestamp).toInstant().toString(),
        )
    }

    /** Only the leg's coordinator or its claiming driver may read/post. */
    private fun requireParticipant(userId: Long, legId: Long) {
        val ok = db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM legs WHERE id=:l AND (coordinator_id=:u OR claimed_by=:u))",
            MapSqlParameterSource().addValue("l", legId).addValue("u", userId), Boolean::class.java,
        ) ?: false
        if (!ok) throw forbidden("not a participant of this leg")
    }
}
