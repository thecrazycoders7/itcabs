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

    fun authApi(baseUrl: String, session: TokenSession, debug: Boolean = false): AuthApi =
        retrofit(baseUrl, session, debug).create(AuthApi::class.java)

    fun dispatchApi(baseUrl: String, session: TokenSession, debug: Boolean = false): DispatchApi =
        retrofit(baseUrl, session, debug).create(DispatchApi::class.java)

    private fun retrofit(baseUrl: String, session: TokenSession, debug: Boolean): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .authenticator(TokenAuthenticator(baseUrl, session, json))
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
