package com.itcabs.identity

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Delivers an OTP code to a phone. Prod wires a real SMS gateway (MSG91) behind this seam. */
interface OtpSender {
    fun send(phone: String, code: String)
}

/**
 * Default sender: logs the code instead of sending SMS (dev only). The single
 * OtpSender bean today.
 * ponytail: no SMS provider yet — add a second @Component (Msg91OtpSender) gated on
 * config when one is wired; nothing else changes. In prod set dev-log-code=false so
 * codes are never logged.
 */
@Component
class DevLogOtpSender(
    @Value("\${itcabs.otp.dev-log-code}") private val devLogCode: Boolean,
) : OtpSender {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun send(phone: String, code: String) {
        if (devLogCode) log.info("OTP for {} = {}", phone, code)
    }
}
