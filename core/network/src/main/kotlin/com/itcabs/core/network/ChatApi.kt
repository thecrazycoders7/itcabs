package com.itcabs.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class MessageDto(val id: Long, val legId: Long, val senderId: Long, val body: String, val createdAt: String)

@Serializable
data class SendMessageDto(val body: String)

/** Per-leg chat between the coordinator and the claiming driver (M7). */
interface ChatApi {
    @GET("api/v1/legs/{id}/messages")
    suspend fun messages(@Path("id") legId: Long): Response<List<MessageDto>>

    @POST("api/v1/legs/{id}/messages")
    suspend fun send(@Path("id") legId: Long, @Body body: SendMessageDto): Response<MessageDto>
}
