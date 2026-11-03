package com.example.smartdashboard

import org.json.JSONObject

data class MusicState(
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val source: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject?): MusicState {
            if (json == null) return MusicState()
            return MusicState(
                playing = json.optBoolean("playing", false),
                title = json.optString("title", ""),
                artist = json.optString("artist", ""),
                source = json.optString("source", "")
            )
        }
    }
}

data class CallState(
    val status: String = "ended", // "ringing", "active", "ended"
    val caller: String = ""
) {
    val isCallActive: Boolean
        get() = status == "ringing" || status == "active"

    companion object {
        fun fromJson(json: JSONObject?): CallState {
            if (json == null) return CallState()
            return CallState(
                status = json.optString("status", "ended"),
                caller = json.optString("caller", "")
            )
        }
    }
}

data class NotificationState(
    val active: Boolean = false,
    val app: String = "",
    val sender: String = "",
    val text: String = "",
    val expires: Double = 0.0
) {
    companion object {
        fun fromJson(json: JSONObject?): NotificationState {
            if (json == null) return NotificationState()
            return NotificationState(
                active = json.optBoolean("active", false),
                app = json.optString("app", ""),
                sender = json.optString("sender", ""),
                text = json.optString("text", ""),
                expires = json.optDouble("expires", 0.0)
            )
        }
    }
}

data class DashboardState(
    val screen: String = "clock", // "clock", "music", "notification", "call"
    val time: Long = 0L,
    val music: MusicState = MusicState(),
    val call: CallState = CallState(),
    val notification: NotificationState = NotificationState()
) {
    companion object {
        fun fromJson(json: JSONObject): DashboardState {
            return DashboardState(
                screen = json.optString("screen", "clock"),
                time = json.optLong("time", 0L),
                music = MusicState.fromJson(json.optJSONObject("music")),
                call = CallState.fromJson(json.optJSONObject("call")),
                notification = NotificationState.fromJson(json.optJSONObject("notification"))
            )
        }
    }
}

data class RecentEvent(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "music", "notification", "call", "test"
    val summary: String,
    val details: String,
    val success: Boolean,
    val errorMessage: String? = null
)

data class ConnectionStatus(
    val isConnected: Boolean = false,
    val lastChecked: Long = 0L,
    val lastError: String? = null,
    val responseTimeMs: Long = 0L
)

