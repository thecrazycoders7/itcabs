package com.itcabs.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class PushTokenDto(val token: String)

/** Registers this device's FCM token with the backend so it can receive push (ADR-0008). */
interface PushApi {
    @POST("api/v1/push/token")
    suspend fun register(@Body body: PushTokenDto): Response<Unit>
}
