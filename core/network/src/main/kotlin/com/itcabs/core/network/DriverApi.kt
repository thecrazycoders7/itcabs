package com.itcabs.core.network

import com.itcabs.core.network.dto.DriverProfileDto
import com.itcabs.core.network.dto.KycInputDto
import com.itcabs.core.network.dto.PendingDriverDto
import com.itcabs.core.network.dto.RejectInputDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DriverApi {
    @POST("api/v1/driver/kyc")
    suspend fun submitKyc(@Body body: KycInputDto): Response<Map<String, String>>

    @GET("api/v1/driver/me")
    suspend fun me(): Response<DriverProfileDto>

    // admin (is_admin-gated server-side)
    @GET("api/v1/admin/drivers/pending")
    suspend fun pendingDrivers(): Response<List<PendingDriverDto>>

    @POST("api/v1/admin/drivers/{id}/verify")
    suspend fun verifyDriver(@Path("id") id: Long): Response<Map<String, String>>

    @POST("api/v1/admin/drivers/{id}/reject")
    suspend fun rejectDriver(@Path("id") id: Long, @Body body: RejectInputDto): Response<Map<String, String>>
}
