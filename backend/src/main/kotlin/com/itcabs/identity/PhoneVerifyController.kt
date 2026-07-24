package com.itcabs.identity

import com.google.firebase.auth.FirebaseAuth
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*

data class PhoneVerifyInput(val idToken: String)

/**
 * Phone verification via Firebase Phone Auth. The client completes the OTP flow with Firebase, then
 * sends the resulting Firebase ID token here; we verify it server-side (same Firebase project as FCM)
 * and trust the phone_number claim — so the "verified" flag can't be spoofed by the client.
 */
@RestController
@RequestMapping("/api/v1")
class PhoneVerifyController(private val db: NamedParameterJdbcTemplate) {

    @PostMapping("/driver/verify-phone")
    fun verifyPhone(req: HttpServletRequest, @RequestBody body: PhoneVerifyInput): Map<String, Any?> {
        val uid = requireUserId(req)
        val decoded = runCatching { FirebaseAuth.getInstance().verifyIdToken(body.idToken) }
            .getOrElse { throw com.itcabs.shared.badRequest("could not verify phone token") }
        val phone = decoded.claims["phone_number"] as? String
            ?: throw com.itcabs.shared.badRequest("token has no verified phone number")
        db.update(
            "UPDATE users SET phone = :p, phone_verified = true WHERE id = :id",
            MapSqlParameterSource().addValue("p", phone).addValue("id", uid),
        )
        return mapOf("verified" to true, "phone" to phone)
    }
}
