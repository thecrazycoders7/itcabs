package com.itcabs.data

import com.itcabs.core.network.TokenSession

/**
 * Stores auth tokens: feeds the access token to the Authorization header (via TokenProvider),
 * supplies the refresh token + accepts refreshed tokens (via TokenSession, for auto-refresh on 401).
 */
interface TokenStore : TokenSession {
    fun clear()
}

/**
 * ponytail: in-memory store for the first M2 slice — tokens do NOT survive process death.
 * Replace with an encrypted-DataStore impl (:core:datastore) before this ships to a device;
 * the interface stays, only the impl changes.
 */
class InMemoryTokenStore : TokenStore {
    @Volatile private var access: String? = null
    @Volatile private var refresh: String? = null

    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh
    override fun save(access: String, refresh: String) {
        this.access = access
        this.refresh = refresh
    }
    override fun clear() {
        access = null
        refresh = null
    }
}
