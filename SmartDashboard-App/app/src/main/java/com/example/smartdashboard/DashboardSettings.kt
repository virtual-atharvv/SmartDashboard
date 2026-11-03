package com.example.smartdashboard

import android.content.Context
import android.content.SharedPreferences

class DashboardSettings private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            val normalized = normalizeUrl(value)
            prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        }

    var redactSensitive: Boolean
        get() = prefs.getBoolean(KEY_REDACT_SENSITIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_REDACT_SENSITIVE, value).apply()

    var relayNotifications: Boolean
        get() = prefs.getBoolean(KEY_RELAY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_RELAY_NOTIFICATIONS, value).apply()

    var relayMusic: Boolean
        get() = prefs.getBoolean(KEY_RELAY_MUSIC, true)
        set(value) = prefs.edit().putBoolean(KEY_RELAY_MUSIC, value).apply()

    var relayCalls: Boolean
        get() = prefs.getBoolean(KEY_RELAY_CALLS, true)
        set(value) = prefs.edit().putBoolean(KEY_RELAY_CALLS, value).apply()

    companion object {
        const val PREFS_NAME = "athrvv_dashboard_prefs"
        const val DEFAULT_SERVER_URL = "http://10.24.63.141:5000"

        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_REDACT_SENSITIVE = "redact_sensitive"
        private const val KEY_RELAY_NOTIFICATIONS = "relay_notifications"
        private const val KEY_RELAY_MUSIC = "relay_music"
        private const val KEY_RELAY_CALLS = "relay_calls"

        @Volatile
        private var instance: DashboardSettings? = null

        fun getInstance(context: Context): DashboardSettings {
            return instance ?: synchronized(this) {
                instance ?: DashboardSettings(context).also { instance = it }
            }
        }

        fun normalizeUrl(url: String): String {
            var trimmed = url.trim()
            if (trimmed.isEmpty()) return DEFAULT_SERVER_URL
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "http://$trimmed"
            }
            return trimmed.trimEnd('/')
        }
    }
}

