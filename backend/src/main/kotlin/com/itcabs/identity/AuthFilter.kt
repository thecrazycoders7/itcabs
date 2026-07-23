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

const val USER_ID_ATTR = "itcabs.userId"    // our internal users.id (Long) — set once onboarded
const val AUTH_ID_ATTR = "itcabs.authId"    // the Supabase user UUID (String)
const val EMAIL_ATTR = "itcabs.email"       // the Supabase email (String), if present

/**
 * Verifies the Supabase access token (ES256 via JWKS) and stashes identity on the request:
 * always the Supabase auth_id + email; plus our internal user id once the user is onboarded and
 * ACTIVE. Endpoints call requireUserId (onboarded) or requireAuthId (authenticated, pre-onboard).
 * ponytail: lightweight filter + one users lookup per request; harden/cache in M3 if it matters.
 */
@Component
class AuthFilter(
    private val supabase: SupabaseJwtVerifier,
    private val db: NamedParameterJdbcTemplate,
) : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        req.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ") }?.let { header ->
            supabase.verify(header.removePrefix("Bearer ").trim())?.let { user ->
                req.setAttribute(AUTH_ID_ATTR, user.authId)
                user.email?.let { req.setAttribute(EMAIL_ATTR, it) }
                // Map to our domain user if onboarded; a BLOCKED user is treated as not-onboarded.
                db.queryForList(
                    "SELECT id, status FROM users WHERE auth_id = :a",
                    MapSqlParameterSource("a", user.authId),
                ).firstOrNull()?.let { row ->
                    if (row["status"] != "BLOCKED") req.setAttribute(USER_ID_ATTR, (row["id"] as Number).toLong())
                }
            }
        }
        chain.doFilter(req, res)
    }
}

/** Our internal user id (onboarded + active), or 401. */
fun requireUserId(req: HttpServletRequest): Long =
    req.getAttribute(USER_ID_ATTR) as? Long ?: throw unauthorized()

/** The Supabase auth_id for an authenticated caller (even before onboarding), or 401. */
fun requireAuthId(req: HttpServletRequest): String =
    req.getAttribute(AUTH_ID_ATTR) as? String ?: throw unauthorized()

/** The Supabase email of the caller, if the token carried one. */
fun emailOf(req: HttpServletRequest): String? = req.getAttribute(EMAIL_ATTR) as? String

/**
 * Returns the authenticated user id, or throws 403 unless they carry the is_admin flag.
 * ponytail: one DB lookup per admin call — fine, admin endpoints are rare.
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
