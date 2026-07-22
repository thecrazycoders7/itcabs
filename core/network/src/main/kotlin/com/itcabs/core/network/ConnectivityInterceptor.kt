package com.itcabs.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Outermost interceptor: converts connectivity failures (server unreachable, timeout, DNS)
 * into a synthetic 503 response, so repository calls get an AppResult.Err instead of a thrown
 * exception crashing the app. Without this, an offline device or a down backend is a fatal crash.
 */
class ConnectivityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service unavailable")
                .body("Network unavailable. Check your connection.".toResponseBody("text/plain".toMediaType()))
                .build()
        }
}
