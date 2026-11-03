package com.example.smartdashboard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class DashboardRepository private constructor(private val appContext: Context) {

    private val settings = DashboardSettings.getInstance(appContext)
    private val api = DashboardApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _currentDashboardState = MutableStateFlow<DashboardState?>(null)
    val currentDashboardState: StateFlow<DashboardState?> = _currentDashboardState.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<RecentEvent>>(emptyList())
    val recentEvents: StateFlow<List<RecentEvent>> = _recentEvents.asStateFlow()

    private val _activeMusic = MutableStateFlow<MusicState?>(null)
    val activeMusic: StateFlow<MusicState?> = _activeMusic.asStateFlow()

    private val _activeCall = MutableStateFlow<CallState?>(null)
    val activeCall: StateFlow<CallState?> = _activeCall.asStateFlow()

    private val _isNotificationAccessGranted = MutableStateFlow(false)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _isPhoneStatePermissionGranted = MutableStateFlow(false)
    val isPhoneStatePermissionGranted: StateFlow<Boolean> = _isPhoneStatePermissionGranted.asStateFlow()

    private val _isMediaListenerActive = MutableStateFlow(false)
    val isMediaListenerActive: StateFlow<Boolean> = _isMediaListenerActive.asStateFlow()

    // Deduplication caches
    private var lastSentMusicState: MusicState? = null
    private var lastSentCallState: String? = null
    private var lastNotificationSignature: String = ""
    private var lastNotificationTime: Long = 0L

    init {
        refreshPermissionStatuses(appContext)
    }

    fun refreshPermissionStatuses(context: Context) {
        // Check Notification Listener Access
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        val myService = ComponentName(context, NotificationRelayService::class.java).flattenToString()
        val isNotifGranted = enabledListeners.contains(myService) || enabledListeners.contains(context.packageName)
        _isNotificationAccessGranted.value = isNotifGranted

        // Check READ_PHONE_STATE permission
        val isPhoneGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        _isPhoneStatePermissionGranted.value = isPhoneGranted
    }

    fun setMediaListenerActive(active: Boolean) {
        _isMediaListenerActive.value = active
    }

    fun checkConnection(onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val startTime = System.currentTimeMillis()
            val result = api.getState(settings.serverUrl)
            val elapsed = System.currentTimeMillis() - startTime
            result.fold(
                onSuccess = { state ->
                    _connectionStatus.value = ConnectionStatus(
                        isConnected = true,
                        lastChecked = System.currentTimeMillis(),
                        responseTimeMs = elapsed,
                        lastError = null
                    )
                    _currentDashboardState.value = state
                    addRecentEvent(
                        type = "connection",
                        summary = "Ping Server",
                        details = "Status 200 OK (${elapsed}ms)",
                        success = true
                    )
                    onComplete?.invoke(true)
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Connection refused / timeout"
                    _connectionStatus.value = ConnectionStatus(
                        isConnected = false,
                        lastChecked = System.currentTimeMillis(),
                        lastError = errorMsg
                    )
                    addRecentEvent(
                        type = "connection",
                        summary = "Ping Server",
                        details = "Failed: $errorMsg",
                        success = false,
                        errorMessage = errorMsg
                    )
                    onComplete?.invoke(false)
                }
            )
        }
    }

    fun postNotificationEvent(app: String, sender: String, text: String, isManualTest: Boolean = false) {
        if (!isManualTest && !settings.relayNotifications) return

        val now = System.currentTimeMillis()
        val signature = "$app|$sender|$text"
        if (!isManualTest && signature == lastNotificationSignature && (now - lastNotificationTime) < 1500L) {
            return // Skip rapid duplicate notification
        }
        lastNotificationSignature = signature
        lastNotificationTime = now

        val cleanApp = app.take(60).ifBlank { "Notification" }
        val cleanSender = sender.take(100).ifBlank { "Unknown" }
        val cleanText = text.take(300).ifBlank { "No message preview" }

        val payload = JSONObject().apply {
            put("event", "notification")
            put("app", cleanApp)
            put("sender", cleanSender)
            put("text", cleanText)
        }

        scope.launch {
            val result = api.postEvent(settings.serverUrl, payload)
            result.fold(
                onSuccess = { dashboard ->
                    _connectionStatus.value = _connectionStatus.value.copy(
                        isConnected = true,
                        lastChecked = System.currentTimeMillis(),
                        lastError = null
                    )
                    _currentDashboardState.value = dashboard
                    addRecentEvent(
                        type = "notification",
                        summary = cleanApp,
                        details = "$cleanSender: $cleanText",
                        success = true
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Failed to post notification"
                    _connectionStatus.value = _connectionStatus.value.copy(
                        isConnected = false,
                        lastChecked = System.currentTimeMillis(),
                        lastError = errorMsg
                    )
                    addRecentEvent(
                        type = "notification",
                        summary = cleanApp,
                        details = "$cleanSender: $cleanText",
                        success = false,
                        errorMessage = errorMsg
                    )
                }
            )
        }
    }

    fun postMusicEvent(
        playing: Boolean,
        title: String,
        artist: String,
        source: String,
        isManualTest: Boolean = false
    ) {
        if (!isManualTest && !settings.relayMusic) return

        val newState = MusicState(
            playing = playing,
            title = title.take(120),
            artist = artist.take(120),
            source = source.take(40)
        )

        if (!isManualTest && newState == lastSentMusicState) {
            return // Deduplicate identical music state
        }
        lastSentMusicState = newState
        _activeMusic.value = newState

        val payload = JSONObject().apply {
            put("event", "music")
            put("playing", newState.playing)
            put("title", newState.title)
            put("artist", newState.artist)
            put("source", newState.source)
        }

        scope.launch {
            val result = api.postEvent(settings.serverUrl, payload)
            result.fold(
                onSuccess = { dashboard ->
                    _connectionStatus.value = _connectionStatus.value.copy(
                        isConnected = true,
                        lastChecked = System.currentTimeMillis(),
                        lastError = null
                    )
                    _currentDashboardState.value = dashboard
                    val statusText = if (newState.playing) "Playing" else "Paused"
                    addRecentEvent(
                        type = "music",
                        summary = "${newState.source} ($statusText)",
                        details = "${newState.title} - ${newState.artist}",
                        success = true
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Failed to post music event"
                    _connectionStatus.value = _connectionStatus.value.copy(
                        isConnected = false,
                        lastChecked = System.currentTimeMillis(),
                        lastError = errorMsg
                    )
                    addRecentEvent(
                        type = "music",
                        summary = "${newState.source} (${if (newState.playing) "Playing" else "Paused"})",
                        details = "${newState.title} - ${newState.artist}",
                        success = false,
                        errorMessage = errorMsg
                    )
                }
            )
        }
    }

    fun postCallEvent(status: String, caller: String = "Phone call", isManualTest: Boolean = false) {
        if (!isManualTest && !settings.relayCalls) return

        val validStatus = when (status.lowercase()) {
            "ringing" -> "ringing"
            "active", "offhook" -> "active"
            else -> "ended"
        }

        if (!isManualTest && validStatus == "ended" && lastSentCallState == "ended") {
            return // Skip repeating idle/ended
        }
        lastSentCallState = validStatus

        val cleanCaller = caller.take(100).ifBlank { "Phone call" }
        _activeCall.value = CallState(status = validStatus, caller = cleanCaller)

        val payload = JSONObject().apply {
            put("event", "call")
            put("status", validStatus)
            put("caller", cleanCaller)
        }

        scope.launch {
            val result = api.postEvent(settings.serverUrl, payload)
            result.fold(
                onSuccess = { dashboard ->
                    _connectionStatus.value = _connectionStatus.value.copy(
                        isConnected = true,
                        lastChecked = System.currentTimeMillis(),
                        lastError = null
                    )
                    _currentDashboardState.value = dashboard
                    addRecentEvent(
                        type = "call",
                        summary = "Call: $validStatus",
                        details = cleanCaller,
                        success = true
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Failed to post call event"
                    _connectionStatus.value = _connectionStatus.value.copy(
                        isConnected = false,
                        lastChecked = System.currentTimeMillis(),
                        lastError = errorMsg
                    )
                    addRecentEvent(
                        type = "call",
                        summary = "Call: $validStatus",
                        details = cleanCaller,
                        success = false,
                        errorMessage = errorMsg
                    )
                }
            )
        }
    }

    private fun addRecentEvent(
        type: String,
        summary: String,
        details: String,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val event = RecentEvent(
            type = type,
            summary = summary,
            details = details,
            success = success,
            errorMessage = errorMessage
        )
        val current = _recentEvents.value.toMutableList()
        current.add(0, event)
        if (current.size > MAX_RECENT_EVENTS) {
            _recentEvents.value = current.take(MAX_RECENT_EVENTS)
        } else {
            _recentEvents.value = current
        }
    }

    fun clearEvents() {
        _recentEvents.value = emptyList()
    }

    companion object {
        private const val MAX_RECENT_EVENTS = 60

        @Volatile
        private var instance: DashboardRepository? = null

        fun getInstance(context: Context): DashboardRepository {
            return instance ?: synchronized(this) {
                instance ?: DashboardRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

