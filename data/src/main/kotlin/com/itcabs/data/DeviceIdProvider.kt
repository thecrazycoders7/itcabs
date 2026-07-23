package com.itcabs.data

/** Supplies a stable per-install device id, sent on sign-in so a banned device can be blocked (M4). */
fun interface DeviceIdProvider {
    fun deviceId(): String
}
