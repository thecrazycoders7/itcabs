package com.itcabs.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Builds the API clients. Framework-agnostic factory for now; a Hilt @Module wraps this
 * when :app is wired (next M2 slice) — nothing here needs to change then.
 */
object NetworkFactory {
    private val json = Json { ignoreUnknownKeys = true }

    fun authApi(baseUrl: String, supabaseUrl: String, anonKey: String, session: TokenSession, debug: Boolean = false): AuthApi =
        retrofit(baseUrl, supabaseUrl, anonKey, session, debug).create(AuthApi::class.java)

    fun dispatchApi(baseUrl: String, supabaseUrl: String, anonKey: String, session: TokenSession, debug: Boolean = false): DispatchApi =
        retrofit(baseUrl, supabaseUrl, anonKey, session, debug).create(DispatchApi::class.java)

    fun driverApi(baseUrl: String, supabaseUrl: String, anonKey: String, session: TokenSession, debug: Boolean = false): DriverApi =
        retrofit(baseUrl, supabaseUrl, anonKey, session, debug).create(DriverApi::class.java)

    fun pushApi(baseUrl: String, supabaseUrl: String, anonKey: String, session: TokenSession, debug: Boolean = false): PushApi =
        retrofit(baseUrl, supabaseUrl, anonKey, session, debug).create(PushApi::class.java)

    fun chatApi(baseUrl: String, supabaseUrl: String, anonKey: String, session: TokenSession, debug: Boolean = false): ChatApi =
        retrofit(baseUrl, supabaseUrl, anonKey, session, debug).create(ChatApi::class.java)

    /** Supabase GoTrue auth. Base URL = the Supabase project URL; the anon key rides as the apikey header. */
    fun supabaseAuthApi(supabaseUrl: String, anonKey: String): SupabaseAuthApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().addHeader("apikey", anonKey).build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(supabaseUrl.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SupabaseAuthApi::class.java)
    }

    /** Realtime leg events (ADR-0008). Own OkHttp client with a ping keepalive for the long-lived socket. */
    fun realtimeClient(baseUrl: String, session: TokenSession): RealtimeClient {
        val client = OkHttpClient.Builder()
            .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return RealtimeClient(baseUrl, session, client)
    }

    private fun retrofit(baseUrl: String, supabaseUrl: String, anonKey: String, session: TokenSession, debug: Boolean): Retrofit {
        val client = OkHttpClient.Builder()
            // Free-tier host spins down when idle; the first request must wait out a ~50s cold start,
            // so give reads generous headroom instead of failing with "network unavailable".
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(70, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(ConnectivityInterceptor()) // outermost: turn connection failures into a 503 result, not a crash
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(supabaseUrl, anonKey, session, json))
            .apply {
                if (debug) addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
