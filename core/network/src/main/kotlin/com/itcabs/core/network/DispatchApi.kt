package com.itcabs.core.network

import com.itcabs.core.network.dto.AreaDto
import com.itcabs.core.network.dto.AssignDto
import com.itcabs.core.network.dto.CoordinatorStatsDto
import com.itcabs.core.network.dto.DriverLocationDto
import com.itcabs.core.network.dto.EditLegDto
import com.itcabs.core.network.dto.LocationDto
import com.itcabs.core.network.dto.LegDto
import com.itcabs.core.network.dto.PostJobDto
import com.itcabs.core.network.dto.RatingDto
import com.itcabs.core.network.dto.StageUpdateDto
import com.itcabs.core.network.dto.StatusUpdateDto
import com.itcabs.core.network.dto.TemplateDto
import com.itcabs.core.network.dto.TemplateInputDto
import com.itcabs.core.network.dto.VerifiedDriverDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit binding for the dispatch endpoints. Raw Response so callers see the 409 on a lost claim. */
interface DispatchApi {
    @POST("api/v1/jobs")
    suspend fun postJob(@Body body: PostJobDto): Response<List<LegDto>>

    @POST("api/v1/jobs/{jobId}/repost")
    suspend fun repost(@Path("jobId") jobId: Long): Response<List<LegDto>>

    @GET("api/v1/legs/mine")
    suspend fun myLegs(): Response<List<LegDto>>

    @GET("api/v1/coordinator/stats")
    suspend fun coordinatorStats(@Query("days") days: Int?): Response<CoordinatorStatsDto>

    @PATCH("api/v1/legs/{id}/status")
    suspend fun setStatus(@Path("id") id: Long, @Body body: StatusUpdateDto): Response<Unit>

    @PATCH("api/v1/legs/{id}")
    suspend fun editLeg(@Path("id") id: Long, @Body body: EditLegDto): Response<LegDto>

    @GET("api/v1/drivers/verified")
    suspend fun verifiedDrivers(): Response<List<VerifiedDriverDto>>

    @POST("api/v1/legs/{id}/assign")
    suspend fun assign(@Path("id") id: Long, @Body body: AssignDto): Response<LegDto>

    @GET("api/v1/templates")
    suspend fun templates(): Response<List<TemplateDto>>

    @POST("api/v1/templates")
    suspend fun saveTemplate(@Body body: TemplateInputDto): Response<TemplateDto>

    @DELETE("api/v1/templates/{id}")
    suspend fun deleteTemplate(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/templates/{id}/post")
    suspend fun postTemplate(@Path("id") id: Long): Response<List<LegDto>>

    @POST("api/v1/legs/{id}/no-show")
    suspend fun noShow(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/legs/{id}/paid")
    suspend fun markPaid(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/legs/{id}/rating")
    suspend fun rate(@Path("id") id: Long, @Body body: RatingDto): Response<Unit>

    @GET("api/v1/legs")
    suspend fun feed(
        @Query("area") area: String?,
        @Query("vehicleType") vehicleType: String?,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): Response<List<LegDto>>

    @GET("api/v1/areas")
    suspend fun areas(): Response<List<AreaDto>>

    @POST("api/v1/legs/{id}/claim")
    suspend fun claim(@Path("id") id: Long): Response<LegDto>

    @POST("api/v1/legs/{id}/stage")
    suspend fun setStage(@Path("id") id: Long, @Body body: StageUpdateDto): Response<Unit>

    @GET("api/v1/legs/claimed")
    suspend fun myClaims(): Response<List<LegDto>>

    @GET("api/v1/legs/upcoming")
    suspend fun upcoming(@Query("lat") lat: Double? = null, @Query("lng") lng: Double? = null): Response<List<LegDto>>

    @POST("api/v1/legs/{id}/release")
    suspend fun release(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/legs/{id}/complete")
    suspend fun driverComplete(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/driver/location")
    suspend fun postDriverLocation(@Body body: LocationDto): Response<Map<String, Boolean>>

    @GET("api/v1/legs/{id}/driver-location")
    suspend fun driverLocation(@Path("id") id: Long): Response<DriverLocationDto>
}
