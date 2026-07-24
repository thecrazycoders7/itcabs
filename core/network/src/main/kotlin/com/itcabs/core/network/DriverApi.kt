package com.itcabs.core.network

import com.itcabs.core.network.dto.AvailabilityDto
import com.itcabs.core.network.dto.DriverProfileDto
import com.itcabs.core.network.dto.DriverPublicDto
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

    @POST("api/v1/driver/availability")
    suspend fun setAvailability(@Body body: AvailabilityDto): Response<Map<String, Boolean>>

    @POST("api/v1/driver/verify-phone")
    suspend fun verifyPhone(@Body body: com.itcabs.core.network.dto.PhoneVerifyDto): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("api/v1/driver/kyc/documents")
    suspend fun registerKycDoc(@Body body: com.itcabs.core.network.dto.KycDocInputDto): Response<Map<String, @JvmSuppressWildcards Any?>>

    @GET("api/v1/driver/kyc/documents")
    suspend fun myKycDocs(): Response<List<com.itcabs.core.network.dto.KycDocDto>>

    @GET("api/v1/drivers/{id}/profile")
    suspend fun publicProfile(@Path("id") id: Long): Response<DriverPublicDto>

    @GET("api/v1/admin/drivers")
    suspend fun allDrivers(): Response<List<com.itcabs.core.network.dto.AdminDriverDto>>

    @POST("api/v1/admin/users/{id}/block")
    suspend fun blockUser(@Path("id") id: Long): Response<Map<String, String>>

    @POST("api/v1/admin/users/{id}/unblock")
    suspend fun unblockUser(@Path("id") id: Long): Response<Map<String, String>>

    // admin (is_admin-gated server-side)
    @GET("api/v1/admin/drivers/pending")
    suspend fun pendingDrivers(): Response<List<PendingDriverDto>>

    @POST("api/v1/admin/drivers/{id}/verify")
    suspend fun verifyDriver(@Path("id") id: Long): Response<Map<String, String>>

    @POST("api/v1/admin/drivers/{id}/reject")
    suspend fun rejectDriver(@Path("id") id: Long, @Body body: RejectInputDto): Response<Map<String, String>>
}
