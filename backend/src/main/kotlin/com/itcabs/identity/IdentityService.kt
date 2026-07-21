package com.itcabs.identity

import com.itcabs.shared.badRequest
import com.itcabs.shared.unauthorized
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

data class Tokens(val accessToken: String, val refreshToken: String, val userId: Long, val role: String)

@Service
class IdentityService(
    private val db: NamedParameterJdbcTemplate,
    private val jwt: JwtService,
    private val otp: OtpSender,
    @Value("\${itcabs.otp.ttl-seconds}") private val otpTtlSeconds: Long,
    @Value("\${itcabs.jwt.refresh-ttl-days}") private val refreshTtlDays: Long,
) {
    private val rng = SecureRandom()

    /** Creates a one-time code. Dev: logs it; prod: an SMS gateway sends it (M3). */
    fun requestOtp(phone: String) {
        require(phone.isNotBlank()) { throw badRequest("phone required") }
        val code = "%06d".format(rng.nextInt(1_000_000))
        db.update(
            "INSERT INTO otp_challenges(phone, code_hash, expires_at) VALUES (:p, :h, :e)",
            MapSqlParameterSource()
                .addValue("p", phone)
                .addValue("h", sha256(code))
                .addValue("e", java.sql.Timestamp.from(Instant.now().plusSeconds(otpTtlSeconds))),
        )
        otp.send(phone, code)
    }

    /**
     * Verifies the latest unconsumed code, upserts the user, issues tokens, and
     * records a session. First-time users pass role (+ name); returning users keep theirs.
     */
    @Transactional
    fun verifyOtp(phone: String, code: String, role: String?, name: String?): Tokens {
        val row = db.queryForList(
            """SELECT id, code_hash, expires_at FROM otp_challenges
               WHERE phone = :p AND consumed_at IS NULL
               ORDER BY created_at DESC LIMIT 1""",
            MapSqlParameterSource("p", phone),
        ).firstOrNull() ?: throw unauthorized("no otp requested")

        val expires = (row["expires_at"] as java.sql.Timestamp).toInstant()
        if (Instant.now().isAfter(expires)) throw unauthorized("otp expired")
        if (row["code_hash"] != sha256(code)) throw unauthorized("wrong code")
        db.update("UPDATE otp_challenges SET consumed_at = now() WHERE id = :id",
            MapSqlParameterSource("id", row["id"]))

        val user = db.queryForList("SELECT id, role FROM users WHERE phone = :p",
            MapSqlParameterSource("p", phone)).firstOrNull()

        val (userId, resolvedRole) = if (user != null) {
            (user["id"] as Number).toLong() to user["role"] as String
        } else {
            val r = role ?: throw badRequest("role required for new user")
            if (r !in setOf("COORDINATOR", "DRIVER")) throw badRequest("invalid role")
            val id = db.queryForObject(
                "INSERT INTO users(phone, role, name) VALUES (:p,:r,:n) RETURNING id",
                MapSqlParameterSource().addValue("p", phone).addValue("r", r).addValue("n", name ?: ""),
                Long::class.java,
            )!!
            id to r
        }

        val access = jwt.issueAccess(userId, resolvedRole)
        val refresh = randomToken()
        db.update(
            "INSERT INTO device_sessions(user_id, refresh_token_hash) VALUES (:u, :h)",
            MapSqlParameterSource().addValue("u", userId).addValue("h", sha256(refresh)),
        )
        return Tokens(access, refresh, userId, resolvedRole)
    }

    /**
     * Exchanges a valid, unrevoked, unexpired refresh token for a fresh access token.
     * The refresh token itself is opaque and tracked in device_sessions (ADR-0005).
     */
    fun refresh(refreshToken: String): Tokens {
        require(refreshToken.isNotBlank()) { throw badRequest("refreshToken required") }
        val row = db.queryForList(
            """SELECT s.user_id, s.created_at, u.role
                 FROM device_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.refresh_token_hash = :h AND s.revoked_at IS NULL
                LIMIT 1""",
            MapSqlParameterSource("h", sha256(refreshToken)),
        ).firstOrNull() ?: throw unauthorized("invalid refresh token")

        val createdAt = (row["created_at"] as java.sql.Timestamp).toInstant()
        if (Instant.now().isAfter(createdAt.plusSeconds(refreshTtlDays * 86_400)))
            throw unauthorized("refresh token expired")

        val userId = (row["user_id"] as Number).toLong()
        val role = row["role"] as String
        // ponytail: no refresh-token rotation for M1; same token reused until revoked/expired.
        // Add rotation (issue new refresh, revoke old) when the threat model needs theft detection.
        return Tokens(jwt.issueAccess(userId, role), refreshToken, userId, role)
    }

    private fun randomToken(): String {
        val b = ByteArray(32); rng.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
