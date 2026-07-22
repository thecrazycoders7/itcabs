package com.itcabs.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMessageTest {

    @Test fun extracts_and_capitalizes_the_error_field() {
        assertEquals("Otp expired", errorMessage("""{"error":"otp expired"}""", 400))
        assertEquals("Leg already taken", errorMessage("""{"error":"leg already taken"}""", 409))
    }

    @Test fun uses_plain_text_body_when_not_json() {
        // The ConnectivityInterceptor's offline message is plain text.
        assertEquals("Network unavailable. Check your connection.", errorMessage("Network unavailable. Check your connection.", 503))
    }

    @Test fun falls_back_to_generic_for_empty_or_null() {
        assertEquals("Something went wrong (HTTP 500).", errorMessage(null, 500))
        assertEquals("Something went wrong (HTTP 500).", errorMessage("", 500))
    }
}
