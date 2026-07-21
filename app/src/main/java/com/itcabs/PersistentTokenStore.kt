package com.itcabs

import android.content.Context
import com.itcabs.data.TokenStore

/**
 * Token storage that survives process death, so a user isn't logged out on restart.
 *
 * The OkHttp AuthInterceptor calls accessToken() synchronously on the network thread, so the
 * read path must be synchronous — hence SharedPreferences (sync) + a volatile in-memory mirror,
 * rather than DataStore (whose async/Flow API would force runBlocking in the interceptor).
 *
 * ponytail: app-private storage (Android sandbox), NOT encrypted at rest. Good enough pre-users.
 * Harden with an Android KeyStore-backed cipher (Tink/EncryptedFile) when the threat model
 * includes rooted devices; the TokenStore interface stays, only this impl changes.
 */
class PersistentTokenStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("itcabs_auth", Context.MODE_PRIVATE)

    @Volatile private var access: String? = prefs.getString(KEY_ACCESS, null)
    @Volatile private var refresh: String? = prefs.getString(KEY_REFRESH, null)

    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh

    override fun save(access: String, refresh: String) {
        this.access = access
        this.refresh = refresh
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply()
    }

    override fun clear() {
        access = null
        refresh = null
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
    }

    private companion object {
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
    }
}
