package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.User
import com.itcabs.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Auth via Supabase (Google + email/password). The client obtains a Supabase session, then this
 * repo talks to our backend (/auth/me, /auth/onboard). Implementations own token storage; the
 * UI never sees raw tokens.
 */
interface AuthRepository {
    fun getUserFlow(): Flow<User?>

    /** A Supabase session token is stored (the user may still need onboarding). */
    fun hasSession(): Boolean

    /** Exchange a Google ID token (from Credential Manager) for a Supabase session. */
    suspend fun signInWithGoogle(idToken: String): AppResult<Unit>

    suspend fun signInWithEmail(email: String, password: String): AppResult<Unit>
    suspend fun signUpWithEmail(email: String, password: String): AppResult<Unit>

    /**
     * GET /auth/me. Ok(user) when onboarded; Ok(null) when authenticated but not yet onboarded
     * (needs role); Err on no/invalid session.
     */
    suspend fun currentUser(): AppResult<User?>

    /** First-time setup after sign-in: pick role (+ name). Creates the domain user. */
    suspend fun onboard(role: UserRole, name: String?): AppResult<User>

    /** Clears the stored session (local sign-out). */
    suspend fun signOut()
}
