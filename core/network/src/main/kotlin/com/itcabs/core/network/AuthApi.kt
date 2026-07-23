package com.itcabs.core.network

import com.itcabs.core.network.dto.MeDto
import com.itcabs.core.network.dto.OnboardDto
import com.itcabs.core.network.dto.OnboardInputDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Backend auth endpoints (identity is Supabase; these map/onboard the domain user). Raw Response. */
interface AuthApi {
    /** Onboarded → user fields + onboarded=true; authenticated-but-new → onboarded=false. */
    @GET("api/v1/auth/me")
    suspend fun me(): Response<MeDto>

    @POST("api/v1/auth/onboard")
    suspend fun onboard(@Body body: OnboardInputDto): Response<OnboardDto>
}
