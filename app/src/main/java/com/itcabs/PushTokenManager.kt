package com.itcabs

import com.google.firebase.messaging.FirebaseMessaging
import com.itcabs.core.network.PushApi
import com.itcabs.core.network.PushTokenDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fetches this device's FCM token and registers it with the backend so the signed-in user can be
 * pushed about new work. Best-effort: the token fetch needs Google Play services, and the register
 * call needs a live session — both failures are swallowed (we retry on the next sign-in / rotation).
 */
class PushTokenManager(private val api: PushApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            scope.launch { runCatching { api.register(PushTokenDto(token)) } }
        }
    }
}
