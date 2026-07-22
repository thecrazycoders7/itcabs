package com.itcabs.shared

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Domain/HTTP error carrying an explicit status. */
class ApiException(val status: HttpStatus, override val message: String) : RuntimeException(message)

fun conflict(msg: String) = ApiException(HttpStatus.CONFLICT, msg)
fun unauthorized(msg: String = "unauthorized") = ApiException(HttpStatus.UNAUTHORIZED, msg)
fun badRequest(msg: String) = ApiException(HttpStatus.BAD_REQUEST, msg)
fun forbidden(msg: String) = ApiException(HttpStatus.FORBIDDEN, msg)
fun tooManyRequests(msg: String) = ApiException(HttpStatus.TOO_MANY_REQUESTS, msg)
fun notFound(msg: String) = ApiException(HttpStatus.NOT_FOUND, msg)

@RestControllerAdvice
class ErrorHandler {
    @ExceptionHandler(ApiException::class)
    fun handle(e: ApiException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(e.status).body(mapOf("error" to e.message))
}
