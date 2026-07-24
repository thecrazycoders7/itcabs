package com.itcabs.core.network

import com.itcabs.core.network.dto.CompanyAssignDto
import com.itcabs.core.network.dto.CompanyJobDto
import com.itcabs.core.network.dto.CompanyJobInputDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.core.network.dto.StopPickupDto
import com.itcabs.core.network.dto.StopsUpdateDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Retrofit binding for the multi-stop company-job endpoints. */
interface CompanyJobApi {
    @POST("api/v1/company-jobs")
    suspend fun create(@Body body: CompanyJobInputDto): Response<CompanyJobDto>

    @GET("api/v1/company-jobs/mine")
    suspend fun mine(): Response<List<CompanyJobDto>>

    @PUT("api/v1/company-jobs/{id}/stops")
    suspend fun replaceStops(@Path("id") id: Long, @Body body: StopsUpdateDto): Response<Map<String, Boolean>>

    @PATCH("api/v1/company-jobs/{id}/status")
    suspend fun setStatus(@Path("id") id: Long, @Body body: StatusUpdateDto): Response<Unit>

    @POST("api/v1/company-jobs/{id}/assign")
    suspend fun assign(@Path("id") id: Long, @Body body: CompanyAssignDto): Response<CompanyJobDto>

    @GET("api/v1/company-jobs/feed")
    suspend fun feed(): Response<List<CompanyJobDto>>

    @GET("api/v1/company-jobs/claimed")
    suspend fun myTrips(): Response<List<CompanyJobDto>>

    @POST("api/v1/company-jobs/{id}/claim")
    suspend fun claim(@Path("id") id: Long): Response<CompanyJobDto>

    @POST("api/v1/company-jobs/stops/{stopId}/pickup")
    suspend fun confirmStopPickup(@Path("stopId") stopId: Long, @Body body: StopPickupDto): Response<Map<String, Boolean>>
}
