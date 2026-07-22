package com.itcabs.identity

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** Delivers an OTP code to a phone. Prod wires a real SMS gateway (MSG91) behind this seam. */
interface OtpSender {
    fun send(phone: String, code: String)
}

/**
 * Default sender: logs the code instead of sending SMS. Active unless a real provider is
 * selected (itcabs.otp.provider=msg91), so dev/CI keep working with zero config.
 * In prod set dev-log-code=false so codes are never logged.
 */
@Component
@ConditionalOnProperty(name = ["itcabs.otp.provider"], havingValue = "dev", matchIfMissing = true)
class DevLogOtpSender(
    @Value("\${itcabs.otp.dev-log-code}") private val devLogCode: Boolean,
) : OtpSender {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun send(phone: String, code: String) {
        if (devLogCode) log.info("OTP for {} = {}", phone, code)
    }
}

/**
 * MSG91 sender: delivers our own generated code via an MSG91 Flow template variable. Active only
 * when itcabs.otp.provider=msg91, so it's a drop-in — set the env and nothing else changes.
 *
 * ponytail: uses the JDK HttpClient (no new dependency). The template variable key (here "otp")
 * must match the variable defined in the MSG91 template. Inputs are controlled (our 6-digit code,
 * digit-filtered phone) so the inline JSON is safe. NOT exercised against the live API yet — wire
 * a real MSG91 account (MSG91_AUTH_KEY + MSG91_TEMPLATE_ID) to validate end to end.
 */
@Component
@ConditionalOnProperty(name = ["itcabs.otp.provider"], havingValue = "msg91")
class Msg91OtpSender(
    @Value("\${itcabs.otp.msg91.auth-key}") private val authKey: String,
    @Value("\${itcabs.otp.msg91.template-id}") private val templateId: String,
) : OtpSender {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newHttpClient()

    override fun send(phone: String, code: String) {
        val mobile = phone.filter(Char::isDigit) // MSG91 wants digits incl. country code
        val body = """{"template_id":"$templateId","recipients":[{"mobiles":"$mobile","otp":"$code"}]}"""
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://control.msg91.com/api/v5/flow/"))
            .header("authkey", authKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            log.error("MSG91 send failed: {} {}", resp.statusCode(), resp.body())
            error("SMS send failed (${resp.statusCode()})")
        }
    }
}
