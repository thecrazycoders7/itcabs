package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Session
import com.itcabs.domain.model.User
import com.itcabs.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Auth against the backend (ADR-0005). Implementations own token storage; the UI/domain
 * never sees raw tokens. Replaces the Firestore-direct auth path in the old `Repo`.
 */
interface AuthRepository {
    fun getUserFlow(): Flow<User?>

    suspend fun requestOtp(phone: String): AppResult<Unit>

    /** [role] and [name] are required only for a first-time user; ignored for returning ones. */
    suspend fun verifyOtp(phone: String, code: String, role: UserRole?, name: String?): AppResult<Session>

    /** Exchanges the stored refresh token for a fresh access token. */
    suspend fun refresh(): AppResult<Unit>

    suspend fun currentUser(): AppResult<User>

    /** Clears stored tokens (local sign-out). */
    suspend fun signOut()
}
