package com.itcabs.realtime

import com.itcabs.identity.JwtService
import java.util.concurrent.ConcurrentHashMap
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/**
 * Realtime leg updates over an authenticated WebSocket (ADR-0008), minimal in-process slice: a
 * single backend instance fans out to every connected session, and clients reconcile by re-fetching
 * over REST on any event (no delta payload yet).
 *
 * ponytail: Redis pub/sub, per-subscription scoping (area/vehicle/time), and delta messages are the
 * scale story from ADR-0008 — add them when there's more than one backend instance or fan-out cost
 * actually bites. Broadcasting to all sessions + REST reconcile is correct, just not yet optimized.
 */
@Component
class LegWebSocketHandler : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) { sessions.add(session) }
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) { sessions.remove(session) }

    /** Tell every connected client that a leg changed; they reconcile via REST. */
    fun legChanged(legId: Long) = broadcast("""{"type":"leg-changed","id":$legId}""")

    // A WebSocketSession is not safe for concurrent sends; serialize the fan-out.
    @Synchronized
    private fun broadcast(text: String) {
        val msg = TextMessage(text)
        sessions.forEach { s -> if (s.isOpen) runCatching { s.sendMessage(msg) } }
    }
}

/** Verifies the Bearer JWT on the WS upgrade request; rejects the handshake if absent/invalid. */
@Component
class JwtHandshakeInterceptor(private val jwt: JwtService) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val header = (request as? ServletServerHttpRequest)?.servletRequest?.getHeader("Authorization")
        val token = header?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
        val userId = token?.let { runCatching { jwt.verify(it) }.getOrNull() }
        if (userId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        attributes["userId"] = userId
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {}
}

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: LegWebSocketHandler,
    private val handshake: JwtHandshakeInterceptor,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws/legs")
            .addInterceptors(handshake)
            .setAllowedOriginPatterns("*")
    }
}
