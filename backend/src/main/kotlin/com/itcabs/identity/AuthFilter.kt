package com.itcabs.identity

import com.itcabs.shared.forbidden
import com.itcabs.shared.unauthorized
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

const val USER_ID_ATTR = "itcabs.userId"

/**
 * Extracts + verifies the Bearer token and stashes the user id on the request.
 * Endpoints that need auth call requireUserId(request); public endpoints ignore it.
 * ponytail: lightweight filter instead of full Spring Security for M1; harden in M3.
 */
@Component
class AuthFilter(private val jwt: JwtService) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        req.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ") }?.let { header ->
            runCatching { jwt.verify(header.removePrefix("Bearer ").trim()) }
                .onSuccess { req.setAttribute(USER_ID_ATTR, it) }
        }
        chain.doFilter(req, res)
    }
}

/** Returns the authenticated user id or throws 401. */
fun requireUserId(req: HttpServletRequest): Long =
    req.getAttribute(USER_ID_ATTR) as? Long ?: throw unauthorized()

/**
 * Returns the authenticated user id, or throws 403 unless they carry the is_admin flag.
 * ponytail: one DB lookup per admin call — fine, admin endpoints are rare. Promote
 * is_admin to a JWT claim only if admin traffic ever gets hot.
 */
fun requireAdmin(req: HttpServletRequest, db: NamedParameterJdbcTemplate): Long {
    val uid = requireUserId(req)
    val isAdmin = db.queryForList(
        "SELECT is_admin FROM users WHERE id = :id",
        MapSqlParameterSource("id", uid),
    ).firstOrNull()?.get("is_admin") as? Boolean ?: false
    if (!isAdmin) throw forbidden("admin only")
    return uid
}
