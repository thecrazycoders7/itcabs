package com.itcabs.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Supplies the current access token for the Authorization header, or null when signed out. */
fun interface TokenProvider {
    fun accessToken(): String?
}

/** Attaches `Authorization: Bearer <access>` when a token is present. */
class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessToken()
        val request = if (token != null) {
            chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
