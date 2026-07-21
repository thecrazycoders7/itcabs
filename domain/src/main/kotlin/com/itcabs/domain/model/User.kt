package com.itcabs.domain.model

enum class UserRole { COORDINATOR, DRIVER }
enum class UserStatus { ACTIVE, BLOCKED }

/** Opaque auth tokens. Access is a short-lived JWT; refresh is exchanged at /auth/refresh. */
data class AuthTokens(val accessToken: String, val refreshToken: String)

/**
 * The result of a successful login. The verify endpoint returns tokens + the caller's
 * id and role (not the full profile); fetch [User] via currentUser() when needed.
 */
data class Session(val tokens: AuthTokens, val userId: Long, val role: UserRole)

data class User(
    val id: Long,
    val phone: String,
    val role: UserRole,
    val name: String,
    val status: UserStatus,
)
