package com.itcabs.data

import com.itcabs.core.database.UserDao
import com.itcabs.core.database.toDomain
import com.itcabs.core.database.toEntity
import com.itcabs.core.network.AuthApi
import com.itcabs.core.network.IdTokenGrant
import com.itcabs.core.network.PasswordCredentials
import com.itcabs.core.network.SupabaseAuthApi
import com.itcabs.core.network.SupabaseSession
import com.itcabs.core.network.dto.MeDto
import com.itcabs.core.network.dto.OnboardInputDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.User
import com.itcabs.domain.model.UserRole
import com.itcabs.domain.model.UserStatus
import com.itcabs.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Auth via Supabase (Google + email/password): the client gets a Supabase session, then our backend
 * maps/onboards the domain user. [tokens] holds the Supabase access + refresh tokens; the backend
 * OkHttp interceptor sends the access token as the Bearer.
 */
class AuthRepositoryImpl(
    private val backend: AuthApi,
    private val supabase: SupabaseAuthApi,
    private val tokens: TokenStore,
    private val userDao: UserDao,
) : AuthRepository {

    override fun getUserFlow(): Flow<User?> = userDao.getUserFlow().map { it?.toDomain() }

    override fun hasSession(): Boolean = tokens.accessToken() != null

    override suspend fun signInWithGoogle(idToken: String): AppResult<Unit> =
        supabase.signInWithIdToken(body = IdTokenGrant("google", idToken)).asResult { it.store() }

    override suspend fun signInWithEmail(email: String, password: String): AppResult<Unit> =
        supabase.signInWithPassword(body = PasswordCredentials(email, password)).asResult { it.store() }

    override suspend fun signUpWithEmail(email: String, password: String): AppResult<Unit> =
        supabase.signUp(PasswordCredentials(email, password)).asResult { it.store() }

    private fun SupabaseSession.store() {
        // Missing tokens (e.g. signup while email-confirmation is on) → no session; caller stays signed out.
        val access = accessToken
        val refresh = refreshToken
        if (access != null && refresh != null) tokens.save(access, refresh)
    }

    override suspend fun currentUser(): AppResult<User?> =
        backend.me().asResult { dto ->
            if (!dto.onboarded) null
            else dto.toUser().also { userDao.insertUser(it.toEntity()) }
        }

    override suspend fun onboard(role: UserRole, name: String?): AppResult<User> =
        backend.onboard(OnboardInputDto(role.name, name)).asResult { dto ->
            User(dto.userId, phone = "", role = UserRole.valueOf(dto.role), name = name ?: "", status = UserStatus.ACTIVE)
                .also { userDao.insertUser(it.toEntity()) }
        }

    override suspend fun signOut() {
        tokens.clear()
        userDao.clear()
    }
}

private fun MeDto.toUser() = User(
    id = id ?: 0,
    phone = phone ?: "",
    role = UserRole.valueOf(role ?: "DRIVER"),
    name = name ?: "",
    status = UserStatus.valueOf(status ?: "ACTIVE"),
)
