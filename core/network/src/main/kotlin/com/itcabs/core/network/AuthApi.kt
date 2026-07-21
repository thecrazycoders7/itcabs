package com.itcabs.core.network

import com.itcabs.core.network.dto.OtpRequestDto
import com.itcabs.core.network.dto.OtpVerifyDto
import com.itcabs.core.network.dto.RefreshDto
import com.itcabs.core.network.dto.SentDto
import com.itcabs.core.network.dto.TokensDto
import com.itcabs.core.network.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Retrofit binding for the backend auth endpoints. Returns raw Response so callers see HTTP codes. */
interface AuthApi {
    @POST("api/v1/auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestDto): Response<SentDto>

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyDto): Response<TokensDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshDto): Response<TokensDto>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<UserDto>
}
