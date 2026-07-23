package com.itcabs.core.network

import com.itcabs.core.network.dto.LegDto
import com.itcabs.core.network.dto.PostJobDto
import com.itcabs.core.network.dto.RatingDto
import com.itcabs.core.network.dto.StatusUpdateDto
import retrofit2.Response
import retrofit2.http.Body
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

    @PATCH("api/v1/legs/{id}/status")
    suspend fun setStatus(@Path("id") id: Long, @Body body: StatusUpdateDto): Response<Unit>

    @POST("api/v1/legs/{id}/rating")
    suspend fun rate(@Path("id") id: Long, @Body body: RatingDto): Response<Unit>

    @GET("api/v1/legs")
    suspend fun feed(
        @Query("area") area: String?,
        @Query("vehicleType") vehicleType: String?,
    ): Response<List<LegDto>>

    @POST("api/v1/legs/{id}/claim")
    suspend fun claim(@Path("id") id: Long): Response<LegDto>

    @GET("api/v1/legs/claimed")
    suspend fun myClaims(): Response<List<LegDto>>
}
