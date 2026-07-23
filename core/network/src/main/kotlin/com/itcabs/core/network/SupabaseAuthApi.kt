package com.itcabs.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/** A Supabase auth session (GoTrue). Field names match the Supabase wire format. */
@Serializable
data class SupabaseSession(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: SupabaseUserDto? = null,
)

@Serializable
data class SupabaseUserDto(val id: String, val email: String? = null)

@Serializable
data class IdTokenGrant(val provider: String, @SerialName("id_token") val idToken: String)

@Serializable
data class PasswordCredentials(val email: String, val password: String)

@Serializable
data class RefreshGrant(@SerialName("refresh_token") val refreshToken: String)

/**
 * Supabase GoTrue auth endpoints (base URL = the Supabase project URL; the anon key is added as the
 * `apikey` header by the factory). We call these directly rather than pull in the full Supabase SDK.
 */
interface SupabaseAuthApi {
    /** Exchange a Google ID token (from Credential Manager) for a Supabase session. */
    @POST("auth/v1/token")
    suspend fun signInWithIdToken(
        @Query("grant_type") grantType: String = "id_token",
        @Body body: IdTokenGrant,
    ): Response<SupabaseSession>

    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Query("grant_type") grantType: String = "password",
        @Body body: PasswordCredentials,
    ): Response<SupabaseSession>

    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: PasswordCredentials): Response<SupabaseSession>

    @POST("auth/v1/token")
    suspend fun refresh(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body body: RefreshGrant,
    ): Response<SupabaseSession>
}
