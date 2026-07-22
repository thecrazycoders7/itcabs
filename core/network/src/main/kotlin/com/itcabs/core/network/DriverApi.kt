package com.itcabs.core.network

import com.itcabs.core.network.dto.DriverProfileDto
import com.itcabs.core.network.dto.KycInputDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface DriverApi {
    @POST("api/v1/driver/kyc")
    suspend fun submitKyc(@Body body: KycInputDto): Response<Map<String, String>>

    @GET("api/v1/driver/me")
    suspend fun me(): Response<DriverProfileDto>
}
