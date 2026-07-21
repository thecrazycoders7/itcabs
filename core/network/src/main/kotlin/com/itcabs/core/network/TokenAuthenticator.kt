package com.itcabs.core.network

import com.itcabs.core.network.dto.RefreshDto
import com.itcabs.core.network.dto.TokensDto
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/** Token storage the network layer can refresh: read the refresh token and persist new ones. */
interface TokenSession : TokenProvider {
    fun refreshToken(): String?
    fun save(access: String, refresh: String)
}

/**
 * On a 401, exchanges the refresh token for a fresh access token and retries the request once,
 * so a live session survives access-token expiry (15 min) without bouncing the user to login.
 * The refresh call uses a bare client (no auth interceptor/authenticator) to avoid recursion.
 *
 * ponytail: @Synchronized + retry-once handles a pilot. If a burst of parallel requests all 401,
 * add single-flight de-dup so they share one refresh instead of each doing their own.
 */
class TokenAuthenticator(
    private val baseUrl: String,
    private val session: TokenSession,
    private val json: Json,
) : Authenticator {
    private val bare = OkHttpClient()

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // already retried once — give up

        val current = session.accessToken()
        val failed = response.request.header("Authorization")?.removePrefix("Bearer ")
        // Another request may have refreshed while we waited on the lock — reuse that token.
        if (current != null && current != failed) {
            return response.request.newBuilder().header("Authorization", "Bearer $current").build()
        }

        val refresh = session.refreshToken() ?: return null
        val fresh = refreshTokens(refresh) ?: return null
        session.save(fresh.accessToken, fresh.refreshToken)
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${fresh.accessToken}")
            .build()
    }

    private fun refreshTokens(refresh: String): TokensDto? = runCatching {
        val body = json.encodeToString(RefreshDto.serializer(), RefreshDto(refresh))
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("${baseUrl}api/v1/auth/refresh").post(body).build()
        bare.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else resp.body?.string()?.let { json.decodeFromString(TokensDto.serializer(), it) }
        }
    }.getOrNull()

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var count = 1
        while (r?.priorResponse != null) { count++; r = r.priorResponse }
        return count
    }
}
