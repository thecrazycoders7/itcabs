package com.itcabs.core.network

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Realtime leg events over an authenticated WebSocket (ADR-0008). Emits [Unit] on every server
 * "leg-changed" message; collectors reconcile by re-fetching over REST. Self-reconnects with a fixed
 * backoff and re-reads the access token on each (re)connect (so it recovers after sign-in).
 *
 * ponytail: OkHttp's built-in WebSocket — no new dependency. No delta payload is parsed; a bare
 * signal + REST re-fetch is the simplest correct reconcile until traffic justifies structured deltas.
 */
class RealtimeClient(
    baseUrl: String,
    private val tokens: TokenProvider,
    private val client: OkHttpClient,
) {
    private val wsUrl = baseUrl.replaceFirst("http", "ws").trimEnd('/') + "/ws/legs"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Emits once per server event; collect and re-fetch the relevant list. */
    val events: SharedFlow<Unit> = _events

    /** Idempotent: opens the socket once, then keeps it alive by reconnecting on drop. */
    fun ensureConnected() {
        if (started.compareAndSet(false, true)) connect()
    }

    private fun connect() {
        val token = tokens.accessToken()
        if (token == null) { reconnectLater(); return } // not signed in yet — retry shortly
        val request = Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build()
        client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { _events.tryEmit(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = reconnectLater()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = reconnectLater()
        })
    }

    private fun reconnectLater() {
        scope.launch { delay(RECONNECT_DELAY_MS); connect() }
    }

    private companion object { const val RECONNECT_DELAY_MS = 3000L }
}
