package com.itcabs.push

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.itcabs.identity.requireUserId
import jakarta.servlet.http.HttpServletRequest
import java.io.FileInputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*

/**
 * Push notifications (ADR-0008): FCM wakes a driver's device about new work — an alert channel, not
 * a data channel (live data still flows over the WebSocket). Behind a seam so dev/CI run with no
 * credentials (noop), and 'fcm' activates only when itcabs.push.provider=fcm.
 */
interface PushSender {
    fun send(tokens: List<String>, title: String, body: String)
}

@Component
@ConditionalOnProperty(name = ["itcabs.push.provider"], havingValue = "noop", matchIfMissing = true)
class NoopPushSender : PushSender {
    override fun send(tokens: List<String>, title: String, body: String) {}
}

@Component
@ConditionalOnProperty(name = ["itcabs.push.provider"], havingValue = "fcm")
class FcmPushSender(
    @Value("\${itcabs.push.fcm.credentials}") credentialsPath: String,
) : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(FileInputStream(credentialsPath)))
                    .build(),
            )
            log.info("FCM initialized from {}", credentialsPath)
        }
    }

    override fun send(tokens: List<String>, title: String, body: String) {
        val fm = FirebaseMessaging.getInstance()
        tokens.forEach { token ->
            val msg = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .build()
            runCatching { fm.send(msg) }.onFailure { log.warn("FCM send failed: {}", it.message) }
        }
    }
}

@Service
class PushService(
    private val db: NamedParameterJdbcTemplate,
    private val sender: PushSender,
) {
    fun registerToken(userId: Long, token: String) {
        db.update(
            """INSERT INTO push_tokens(user_id, token) VALUES (:u, :t)
               ON CONFLICT (user_id, token) DO UPDATE SET updated_at = now()""",
            MapSqlParameterSource().addValue("u", userId).addValue("t", token),
        )
    }

    /** Push to a single user's registered devices (KYC decisions, claim alerts, etc.). No-op if none. */
    fun notifyUser(userId: Long, title: String, body: String) {
        val tokens = db.queryForList(
            "SELECT token FROM push_tokens WHERE user_id = :u",
            MapSqlParameterSource("u", userId), String::class.java,
        )
        if (tokens.isNotEmpty()) sender.send(tokens, title, body)
    }

    /**
     * Alert verified drivers that new work is available. ponytail: broadcasts to all verified
     * drivers — scope by area/vehicle/time when relevance and fan-out cost matter (ADR-0008).
     */
    fun notifyDriversNewLeg(office: String) {
        val tokens = db.queryForList(
            """SELECT pt.token FROM push_tokens pt
                 JOIN users u ON u.id = pt.user_id
                 JOIN driver_profiles p ON p.user_id = u.id
                WHERE u.role='DRIVER' AND u.status='ACTIVE' AND p.kyc_status='VERIFIED'
                  AND p.available = true""",
            MapSqlParameterSource(), String::class.java,
        )
        if (tokens.isNotEmpty()) sender.send(tokens, "New trip available", "A new trip to $office was just posted.")
    }
}

data class PushTokenInput(val token: String)

@RestController
@RequestMapping("/api/v1")
class PushController(private val push: PushService) {
    /** A signed-in client registers its FCM device token. */
    @PostMapping("/push/token")
    fun register(req: HttpServletRequest, @RequestBody body: PushTokenInput): Map<String, Any> {
        push.registerToken(requireUserId(req), body.token)
        return mapOf("registered" to true)
    }
}
