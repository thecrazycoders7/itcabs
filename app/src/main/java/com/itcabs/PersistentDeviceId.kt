package com.itcabs

import android.content.Context
import com.itcabs.data.DeviceIdProvider
import java.util.UUID

/**
 * Stable per-install device id. Generated once and reused — plain prefs (not sensitive; it's an
 * opaque random id, not a hardware identifier). Sent on sign-in so a banned device can be blocked.
 */
class PersistentDeviceId(context: Context) : DeviceIdProvider {
    private val prefs = context.getSharedPreferences("itcabs_device", Context.MODE_PRIVATE)

    override fun deviceId(): String =
        prefs.getString(KEY, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }

    private companion object { const val KEY = "device_id" }
}
