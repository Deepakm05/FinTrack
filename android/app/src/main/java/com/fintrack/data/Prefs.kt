package com.fintrack.data

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** Small settings store: access token, device id, connection mode. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("fintrack", Context.MODE_PRIVATE)

    init {
        if (!sp.contains(KEY_INSTALLED_AT)) {
            sp.edit()
                .putLong(KEY_INSTALLED_AT, System.currentTimeMillis())
                .putString(KEY_DEVICE_ID, UUID.randomUUID().toString())
                .putString(KEY_TOKEN, newToken())
                .apply()
        }
    }

    val installedAt: Long get() = sp.getLong(KEY_INSTALLED_AT, 0)
    val deviceId: String get() = sp.getString(KEY_DEVICE_ID, "")!!
    val token: String get() = sp.getString(KEY_TOKEN, "")!!

    /** true = reachable over Wi-Fi, false = USB only (adb forward, localhost). */
    var wifiMode: Boolean
        get() = sp.getBoolean(KEY_WIFI_MODE, true)
        set(value) = sp.edit().putBoolean(KEY_WIFI_MODE, value).apply()

    fun regenerateToken(): String = newToken().also { sp.edit().putString(KEY_TOKEN, it).apply() }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val PORT = 8765
        private const val KEY_INSTALLED_AT = "installed_at"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_WIFI_MODE = "wifi_mode"
    }
}
