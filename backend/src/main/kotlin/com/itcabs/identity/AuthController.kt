package com.itcabs.identity

import jakarta.servlet.http.HttpServletRequest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*

data class OtpRequest(val phone: String)
data class OtpVerify(val phone: String, val code: String, val role: String? = null, val name: String? = null)
data class RefreshRequest(val refreshToken: String)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val identity: IdentityService,
    private val db: NamedParameterJdbcTemplate,
) {
    @PostMapping("/otp/request")
    fun request(@RequestBody body: OtpRequest): Map<String, Any> {
        identity.requestOtp(body.phone)
        return mapOf("sent" to true)
    }

    @PostMapping("/otp/verify")
    fun verify(@RequestBody body: OtpVerify): Tokens =
        identity.verifyOtp(body.phone, body.code, body.role, body.name)

    /** Exchange a refresh token for a fresh access token (access JWTs expire in 15 min). */
    @PostMapping("/refresh")
    fun refresh(@RequestBody body: RefreshRequest): Tokens =
        identity.refresh(body.refreshToken)

    /** Who am I — used by clients after login to route by role. */
    @GetMapping("/me")
    fun me(req: HttpServletRequest): Map<String, Any?> {
        val id = requireUserId(req)
        return db.queryForList(
            "SELECT id, phone, role, name, status FROM users WHERE id = :id",
            MapSqlParameterSource("id", id),
        ).first()
    }
}
