package com.itcabs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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
        // Support both notification-payload and data-only messages: fall back to data fields so the
        // alert renders consistently whether the app is in the foreground or background.
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val route = message.data["route"]

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Trips", NotificationManager.IMPORTANCE_HIGH),
        )
        // Tapping opens the app deep-linked to the route the backend sent (e.g. "coordinator_jobs").
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra("route", it) }
        }
        val pending = PendingIntent.getActivity(
            this, route.hashCode(), tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // Stable id per route so a fresh alert for the same context replaces the old one instead of stacking.
        nm.notify(route?.hashCode() ?: NOTIF_ID_DEFAULT, notif)
    }

    private companion object {
        const val CHANNEL_ID = "itcabs_trips"
        const val NOTIF_ID_DEFAULT = 1001
    }
}
