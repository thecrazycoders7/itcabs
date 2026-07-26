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

    // Bodies are ignored (we only care about success). Use a concrete @Serializable DTO — a
    // Map<String, Any?> has no kotlinx serializer and blows up Retrofit with "Unable to create
    // converter" at call time (which broke document upload + phone verify).
    @POST("api/v1/driver/verify-phone")
    suspend fun verifyPhone(@Body body: com.itcabs.core.network.dto.PhoneVerifyDto): Response<com.itcabs.core.network.dto.OkDto>

    @POST("api/v1/driver/kyc/documents")
    suspend fun registerKycDoc(@Body body: com.itcabs.core.network.dto.KycDocInputDto): Response<com.itcabs.core.network.dto.OkDto>

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

    @GET("api/v1/admin/coordinators/pending")
    suspend fun pendingCoordinators(): Response<List<com.itcabs.core.network.dto.PendingCoordinatorDto>>

    @POST("api/v1/admin/coordinators/{id}/approve")
    suspend fun approveCoordinator(@Path("id") id: Long): Response<Map<String, String>>

    @POST("api/v1/admin/coordinators/{id}/reject")
    suspend fun rejectCoordinator(@Path("id") id: Long, @Body body: RejectInputDto): Response<Map<String, String>>

    // admin (is_admin-gated server-side)
    @GET("api/v1/admin/drivers/pending")
    suspend fun pendingDrivers(): Response<List<PendingDriverDto>>

    @POST("api/v1/admin/drivers/{id}/verify")
    suspend fun verifyDriver(@Path("id") id: Long): Response<Map<String, String>>

    @POST("api/v1/admin/drivers/{id}/reject")
    suspend fun rejectDriver(@Path("id") id: Long, @Body body: RejectInputDto): Response<Map<String, String>>

    @GET("api/v1/admin/drivers/{id}/documents")
    suspend fun driverDocuments(@Path("id") id: Long): Response<List<com.itcabs.core.network.dto.KycDocDto>>

    @POST("api/v1/admin/drivers/{id}/documents/{docType}/reupload")
    suspend fun requestReupload(
        @Path("id") id: Long, @Path("docType") docType: String, @Body body: RejectInputDto,
    ): Response<Map<String, String>>
}
