package com.example.smartdashboard

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationRelayService : NotificationListenerService() {

    private lateinit var repository: DashboardRepository
    private var mediaSessionMonitor: MediaSessionMonitor? = null

    override fun onCreate() {
        super.onCreate()
        repository = DashboardRepository.getInstance(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        repository.refreshPermissionStatuses(applicationContext)
        
        // NotificationListenerService grants permission to observe active media sessions
        mediaSessionMonitor = MediaSessionMonitor(applicationContext).apply {
            start()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionMonitor?.stop()
        mediaSessionMonitor = null
        repository.refreshPermissionStatuses(applicationContext)
    }

    override fun onDestroy() {
        mediaSessionMonitor?.stop()
        mediaSessionMonitor = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return

        // 1. Never forward notifications from this app to avoid loops
        if (pkgName == packageName) return

        val notification = sbn.notification ?: return

        // 2. Ignore ongoing transport/playback notifications (MediaSessionMonitor handles music metadata)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val category = notification.category
        if (isOngoing && (category == Notification.CATEGORY_TRANSPORT || category == Notification.CATEGORY_SERVICE)) {
            return
        }

        // 3. Extract text content safely from bundle
        val extras: Bundle = notification.extras ?: Bundle.EMPTY
        val titleCharSequence = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        val textCharSequence = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)

        val rawTitle = titleCharSequence?.toString()?.trim() ?: ""
        val rawText = textCharSequence?.toString()?.trim() ?: ""

        // If both title and text are completely empty, nothing meaningful to forward
        if (rawTitle.isEmpty() && rawText.isEmpty()) return

        // 4. Resolve human-readable application label
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        val sender = if (rawTitle.isNotEmpty()) rawTitle else appName
        val bodyText = if (rawText.isNotEmpty()) rawText else rawTitle

        // 5. Forward event to repository
        repository.postNotificationEvent(
            app = appName,
            sender = sender,
            text = bodyText
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // No explicit action required on removal since the Flask server automatically
        // expires notifications after 5 seconds per dashboard specifications.
    }
}

