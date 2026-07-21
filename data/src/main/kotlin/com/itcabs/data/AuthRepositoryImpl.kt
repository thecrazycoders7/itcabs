package com.itcabs.data

import com.itcabs.core.network.AuthApi
import com.itcabs.core.network.dto.OtpRequestDto
import com.itcabs.core.network.dto.OtpVerifyDto
import com.itcabs.core.network.dto.RefreshDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.Session
import com.itcabs.domain.model.User
import com.itcabs.domain.model.UserRole
import com.itcabs.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokens: TokenStore,
) : AuthRepository {

    override suspend fun requestOtp(phone: String): AppResult<Unit> =
        api.requestOtp(OtpRequestDto(phone)).asResult { }

    override suspend fun verifyOtp(
        phone: String,
        code: String,
        role: UserRole?,
        name: String?,
    ): AppResult<Session> =
        api.verifyOtp(OtpVerifyDto(phone, code, role?.name, name)).asResult { dto ->
            tokens.save(dto.accessToken, dto.refreshToken)
            dto.toSession()
        }

    override suspend fun refresh(): AppResult<Unit> {
        val refreshToken = tokens.refreshToken() ?: return AppResult.Err(401, "no refresh token")
        return api.refresh(RefreshDto(refreshToken)).asResult { dto ->
            tokens.save(dto.accessToken, dto.refreshToken)
        }
    }

    override suspend fun currentUser(): AppResult<User> =
        api.me().asResult { it.toDomain() }
}
