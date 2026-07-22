package com.itcabs.data

import com.itcabs.domain.AppResult
import retrofit2.Response

/** Maps a Retrofit [Response] to [AppResult], running [onSuccess] on a non-null decoded body. */
internal inline fun <B, R> Response<B>.asResult(onSuccess: (B) -> R): AppResult<R> =
    if (isSuccessful) {
        when (val body = body()) {
            null -> AppResult.Err(code(), "Something went wrong.")
            else -> AppResult.Ok(onSuccess(body))
        }
    } else {
        AppResult.Err(code(), errorMessage(errorBody()?.string(), code()))
    }

// Backend errors are {"error":"..."}; the ConnectivityInterceptor sends plain text.
private val ERROR_FIELD = Regex(""""error"\s*:\s*"((?:[^"\\]|\\.)*)"""")

/**
 * Turns a raw error body into a user-facing message — extracts the {"error":"…"} field when
 * present, else uses the plain body, else a generic message. Never shows raw JSON to the user.
 */
internal fun errorMessage(rawBody: String?, code: Int): String {
    val extracted = rawBody?.let { ERROR_FIELD.find(it)?.groupValues?.get(1) } ?: rawBody
    val message = extracted?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        ?: return "Something went wrong (HTTP $code)."
    return message.replaceFirstChar { it.uppercase() }
}
