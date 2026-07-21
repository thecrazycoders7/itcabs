package com.itcabs.identity

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import com.itcabs.shared.unauthorized
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date

/** Issues + validates access JWTs. Refresh tokens are opaque, tracked in device_sessions. */
@Service
class JwtService(
    @Value("\${itcabs.jwt.secret}") secret: String,
    @Value("\${itcabs.jwt.access-ttl-minutes}") private val accessTtlMinutes: Long,
) {
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun issueAccess(userId: Long, role: String): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .issuedAt(Date(now))
            .expiration(Date(now + accessTtlMinutes * 60_000))
            .signWith(key)
            .compact()
    }

    /** Returns the user id, or throws 401 if the token is invalid/expired. */
    fun verify(token: String): Long = try {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload.subject.toLong()
    } catch (e: Exception) {
        throw unauthorized("invalid token")
    }
}
