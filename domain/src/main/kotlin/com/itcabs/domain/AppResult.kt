package com.itcabs.domain

/**
 * Outcome of a repository call. Err carries the HTTP status so callers can distinguish
 * cases that matter to the domain — e.g. 409 on a claim means "someone else got it".
 */
sealed interface AppResult<out T> {
    data class Ok<out T>(val value: T) : AppResult<T>

    /** [code] is the HTTP status, or 0 for a transport/connectivity failure. */
    data class Err(val code: Int, val message: String) : AppResult<Nothing>
}
