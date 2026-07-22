package com.itcabs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.itcabs.data.TokenStore

/**
 * Token storage that survives process death, encrypted at rest.
 *
 * The OkHttp AuthInterceptor calls accessToken() synchronously on the network thread, so the
 * read path must be synchronous — hence SharedPreferences (sync) + a volatile in-memory mirror,
 * rather than DataStore (whose async/Flow API would force runBlocking in the interceptor).
 * EncryptedSharedPreferences keeps that sync contract while encrypting values with an Android
 * KeyStore-backed master key (AES-256-GCM), so tokens aren't readable off a rooted device.
 *
 * ponytail: androidx.security-crypto is deprecated-but-functional — it's the smallest change that
 * encrypts at rest and preserves the sync read. Swap for an app-managed Keystore cipher only if the
 * dependency is dropped; the TokenStore interface stays, only this impl changes.
 */
class PersistentTokenStore(context: Context) : TokenStore {
    private val prefs: SharedPreferences = openEncrypted(context)

    @Volatile private var access: String? = prefs.getString(KEY_ACCESS, null)
    @Volatile private var refresh: String? = prefs.getString(KEY_REFRESH, null)

    init {
        // Drop any tokens left by the earlier plaintext store (pre-encryption builds).
        context.deleteSharedPreferences(LEGACY_FILE)
    }

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
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "itcabs_auth_enc"
        const val LEGACY_FILE = "itcabs_auth"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"

        fun openEncrypted(context: Context): SharedPreferences {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            fun build() = EncryptedSharedPreferences.create(
                context,
                FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return try {
                build()
            } catch (e: Exception) {
                // File corrupted (or key mismatch) → wipe and recreate; the user re-logs in once
                // rather than the app crash-looping on every launch.
                context.deleteSharedPreferences(FILE)
                build()
            }
        }
    }
}
