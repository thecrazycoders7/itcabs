package com.itcabs.identity

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URI
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Verifies Supabase-issued access tokens (ES256, asymmetric). Public keys are fetched, cached, and
 * rotated automatically from the project's JWKS endpoint — no shared secret to hold or leak.
 * Checks signature + issuer + audience(authenticated) + expiry, and returns the Supabase identity.
 */
@Component
class SupabaseJwtVerifier(
    @Value("\${itcabs.supabase.url}") supabaseUrl: String,
) {
    private val issuer = supabaseUrl.trimEnd('/') + "/auth/v1"

    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        val jwkSource = JWKSourceBuilder
            .create<SecurityContext>(URI.create("$issuer/.well-known/jwks.json").toURL())
            .build()
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.ES256, jwkSource)
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            "authenticated", // required audience
            JWTClaimsSet.Builder().issuer(issuer).build(),
            setOf("sub", "exp"),
        )
    }

    data class SupabaseUser(val authId: String, val email: String?)

    /** The verified Supabase user, or null if the token is missing/invalid/expired. */
    fun verify(token: String): SupabaseUser? = runCatching {
        val claims = processor.process(token, null)
        SupabaseUser(authId = claims.subject, email = claims.getStringClaim("email"))
    }.getOrNull()
}
