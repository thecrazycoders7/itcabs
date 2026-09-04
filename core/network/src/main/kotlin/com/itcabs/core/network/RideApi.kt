package com.itcabs.core.network

import com.itcabs.core.network.dto.BookInputDto
import com.itcabs.core.network.dto.PickupInputDto
import com.itcabs.core.network.dto.RideDto
import com.itcabs.core.network.dto.RideInputDto
import com.itcabs.core.network.dto.RideStatusInputDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit binding for peer-to-peer carpooling. */
interface RideApi {
    @POST("api/v1/rides")
    suspend fun create(@Body body: RideInputDto): Response<RideDto>

    @GET("api/v1/rides/search")
    suspend fun search(
        @Query("originLat") originLat: Double?,
        @Query("originLng") originLng: Double?,
        @Query("destLat") destLat: Double?,
        @Query("destLng") destLng: Double?,
        @Query("on") on: String?,
    ): Response<List<RideDto>>

    @GET("api/v1/rides/mine")
    suspend fun mine(): Response<List<RideDto>>

    @GET("api/v1/rides/bookings")
    suspend fun bookings(): Response<List<RideDto>>

    @GET("api/v1/rides/{id}")
    suspend fun detail(@Path("id") id: Long): Response<RideDto>

    @GET("api/v1/rides/{id}/riders")
    suspend fun riders(@Path("id") id: Long): Response<List<com.itcabs.core.network.dto.RideRiderDto>>

    @POST("api/v1/rides/{id}/book")
    suspend fun book(@Path("id") id: Long, @Body body: BookInputDto): Response<RideDto>

    @POST("api/v1/rides/{id}/cancel-booking")
    suspend fun cancelBooking(@Path("id") id: Long): Response<Map<String, Boolean>>

    @POST("api/v1/rides/{id}/pickup")
    suspend fun pickup(@Path("id") id: Long, @Body body: PickupInputDto): Response<Map<String, Boolean>>

    @POST("api/v1/rides/{id}/rate")
    suspend fun rate(@Path("id") id: Long, @Body body: com.itcabs.core.network.dto.RateInputDto): Response<Map<String, Boolean>>

    @POST("api/v1/rides/{id}/status")
    suspend fun status(@Path("id") id: Long, @Body body: RideStatusInputDto): Response<Map<String, String>>
}
