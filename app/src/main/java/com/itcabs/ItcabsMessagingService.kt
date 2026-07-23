package com.itcabs

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.itcabs.core.network.PushApi
import com.itcabs.core.network.PushTokenDto
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives FCM messages and token rotations. Displays a notification for new-trip alerts. */
@AndroidEntryPoint
class ItcabsMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushApi: PushApi
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Register the rotated token (succeeds only if signed in; otherwise re-registered on next login).
        scope.launch { runCatching { pushApi.register(PushTokenDto(token)) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Trips", NotificationManager.IMPORTANCE_HIGH),
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private companion object { const val CHANNEL_ID = "itcabs_trips" }
}
