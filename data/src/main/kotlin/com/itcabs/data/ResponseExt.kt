package com.itcabs.data

import com.itcabs.domain.AppResult
import retrofit2.Response

/** Maps a Retrofit [Response] to [AppResult], running [onSuccess] on a non-null decoded body. */
internal inline fun <B, R> Response<B>.asResult(onSuccess: (B) -> R): AppResult<R> =
    if (isSuccessful) {
        when (val body = body()) {
            null -> AppResult.Err(code(), "empty body")
            else -> AppResult.Ok(onSuccess(body))
        }
    } else {
        AppResult.Err(code(), errorBody()?.string()?.take(200) ?: "http ${code()}")
    }
