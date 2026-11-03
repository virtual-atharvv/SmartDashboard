package com.example.smartdashboard

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

class MediaSessionMonitor(private val context: Context) {

    private val repository = DashboardRepository.getInstance(context)
    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val componentName = ComponentName(context, NotificationRelayService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val controllers = mutableListOf<MediaController>()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private var isStarted = false

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { activeControllers ->
            mainHandler.post {
                updateControllers(activeControllers)
                evaluateActiveSession()
            }
        }

    fun start() {
        if (isStarted) return
        val mgr = sessionManager ?: return

        try {
            mgr.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            val currentControllers = mgr.getActiveSessions(componentName)
            updateControllers(currentControllers)
            evaluateActiveSession()
            isStarted = true
            repository.setMediaListenerActive(true)
        } catch (e: SecurityException) {
            // Notification access not granted yet
            isStarted = false
            repository.setMediaListenerActive(false)
        } catch (e: Exception) {
            isStarted = false
            repository.setMediaListenerActive(false)
        }
    }

    fun stop() {
        if (!isStarted) return
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (_: Exception) {}

        clearCallbacks()
        isStarted = false
        repository.setMediaListenerActive(false)
    }

    private fun updateControllers(newControllers: List<MediaController>?) {
        clearCallbacks()
        controllers.clear()

        if (newControllers != null) {
            controllers.addAll(newControllers)
            for (controller in controllers) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        evaluateActiveSession()
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        evaluateActiveSession()
                    }

                    override fun onSessionDestroyed() {
                        mainHandler.post {
                            controllers.remove(controller)
                            controllerCallbacks.remove(controller)
                            evaluateActiveSession()
                        }
                    }
                }
                controllerCallbacks[controller] = callback
                controller.registerCallback(callback, mainHandler)
            }
        }
    }

    private fun clearCallbacks() {
        for ((controller, callback) in controllerCallbacks) {
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {}
        }
        controllerCallbacks.clear()
    }

    private fun evaluateActiveSession() {
        if (controllers.isEmpty()) {
            return
        }

        // Deterministic sorting:
        // 1. STATE_PLAYING first
        // 2. Known apps (Spotify, YouTube Music, YouTube) next
        // 3. Other playing apps
        // 4. Paused sessions with valid metadata
        val sorted = controllers.sortedWith(
            compareByDescending<MediaController> { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                .thenByDescending { isPreferredApp(it.packageName) }
                .thenByDescending { it.playbackState?.lastPositionUpdateTime ?: 0L }
        )

        val bestController = sorted.firstOrNull() ?: return
        val playbackState = bestController.playbackState
        val metadata = bestController.metadata

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""

        val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""

        val source = resolveSourceApp(bestController.packageName)

        // Only forward if we have at least a title or it's actively playing/paused with context
        if (rawTitle.isNotBlank() || isPlaying) {
            repository.postMusicEvent(
                playing = isPlaying,
                title = if (rawTitle.isNotBlank()) rawTitle else "Unknown Track",
                artist = if (rawArtist.isNotBlank()) rawArtist else source,
                source = source
            )
        }
    }

    private fun isPreferredApp(packageName: String?): Boolean {
        return when (packageName) {
            "com.spotify.music" -> true
            "com.google.android.apps.youtube.music" -> true
            "com.google.android.youtube" -> true
            else -> false
        }
    }

    private fun resolveSourceApp(packageName: String?): String {
        return when (packageName) {
            "com.spotify.music" -> "Spotify"
            "com.google.android.apps.youtube.music" -> "YouTube Music"
            "com.google.android.youtube" -> "YouTube"
            null -> "Media Player"
            else -> {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }
        }
    }
}

