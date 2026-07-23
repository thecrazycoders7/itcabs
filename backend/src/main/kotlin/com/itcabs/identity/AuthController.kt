package com.itcabs.identity

import com.itcabs.shared.badRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*

data class OnboardInput(val role: String, val name: String? = null)

/**
 * Auth against Supabase (Google + email/password). The client signs in with Supabase and sends its
 * access token; the AuthFilter verifies it. First-time users onboard here (pick role); returning
 * users are recognised by their Supabase auth_id. Phone-OTP is retired.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val db: NamedParameterJdbcTemplate) {

    /** Who am I. Needs a valid Supabase token. Returns the domain user if onboarded, else onboarded:false. */
    @GetMapping("/me")
    fun me(req: HttpServletRequest): Map<String, Any?> {
        requireAuthId(req) // 401 if not authenticated
        val uid = req.getAttribute(USER_ID_ATTR) as? Long ?: return mapOf("onboarded" to false)
        return db.queryForList(
            "SELECT id, phone, email, role, name, status, is_admin FROM users WHERE id = :id",
            MapSqlParameterSource("id", uid),
        ).first() + mapOf("onboarded" to true)
    }

    /** First-time setup after Supabase sign-in: pick role (+ name). Creates the domain user. Idempotent. */
    @PostMapping("/onboard")
    fun onboard(req: HttpServletRequest, @RequestBody body: OnboardInput): Map<String, Any?> {
        val authId = requireAuthId(req)
        val role = body.role.uppercase()
        if (role !in setOf("COORDINATOR", "DRIVER")) throw badRequest("role must be COORDINATOR or DRIVER")

        db.queryForList("SELECT id, role FROM users WHERE auth_id = :a", MapSqlParameterSource("a", authId))
            .firstOrNull()?.let {
                return mapOf("userId" to (it["id"] as Number).toLong(), "role" to it["role"], "onboarded" to true)
            }

        val id = db.queryForObject(
            "INSERT INTO users(auth_id, email, role, name) VALUES (:a,:e,:r,:n) RETURNING id",
            MapSqlParameterSource().addValue("a", authId).addValue("e", emailOf(req))
                .addValue("r", role).addValue("n", body.name ?: ""),
            Long::class.java,
        )!!
        return mapOf("userId" to id, "role" to role, "onboarded" to true)
    }
}
