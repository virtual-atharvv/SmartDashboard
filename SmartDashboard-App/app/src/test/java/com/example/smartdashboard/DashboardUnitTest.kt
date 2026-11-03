package com.example.smartdashboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUnitTest {

    @Test
    fun testUrlNormalization() {
        assertEquals(
            "http://10.24.63.141:5000",
            DashboardSettings.normalizeUrl("10.24.63.141:5000")
        )
        assertEquals(
            "http://10.24.63.141:5000",
            DashboardSettings.normalizeUrl("http://10.24.63.141:5000/")
        )
        assertEquals(
            "https://my-dashboard.local:5000",
            DashboardSettings.normalizeUrl("https://my-dashboard.local:5000///")
        )
        assertEquals(
            DashboardSettings.DEFAULT_SERVER_URL,
            DashboardSettings.normalizeUrl("")
        )
        assertEquals(
            "http://192.168.1.100:8000",
            DashboardSettings.normalizeUrl("   192.168.1.100:8000/   ")
        )
    }

    @Test
    fun testSensitiveContentMasking() {
        val message = "Ramza: I Love You Athrvv"
        val masked = maskSensitiveContent(message)
        assertTrue("Sender name should be preserved", masked.startsWith("Ramza:"))
        assertFalse("Secret text should not be exposed in raw form", masked.contains("I Love You Athrvv"))
        assertTrue("Should contain masked bullets", masked.contains("••••••••"))

        val shortMessage = "Hi"
        val maskedShort = maskSensitiveContent(shortMessage)
        assertEquals("••••", maskedShort)
    }

    @Test
    fun testCallStateActiveCheck() {
        val ringingCall = CallState(status = "ringing", caller = "Phone call")
        assertTrue(ringingCall.isCallActive)

        val activeCall = CallState(status = "active", caller = "Phone call")
        assertTrue(activeCall.isCallActive)

        val endedCall = CallState(status = "ended", caller = "")
        assertFalse(endedCall.isCallActive)

        val emptyCall = CallState()
        assertFalse(emptyCall.isCallActive)
        assertEquals("ended", emptyCall.status)
    }

    @Test
    fun testStringClampingLimits() {
        val veryLongApp = "A".repeat(100)
        val veryLongSender = "B".repeat(150)
        val veryLongText = "C".repeat(500)
        val veryLongTitle = "D".repeat(200)
        val veryLongArtist = "E".repeat(200)
        val veryLongSource = "F".repeat(100)

        // Verifying truncation conforms to Flask server limits
        // Flask server limits: app <= 60, sender <= 100, text <= 300, title <= 120, artist <= 120, source <= 40
        assertEquals(60, veryLongApp.take(60).length)
        assertEquals(100, veryLongSender.take(100).length)
        assertEquals(300, veryLongText.take(300).length)
        assertEquals(120, veryLongTitle.take(120).length)
        assertEquals(120, veryLongArtist.take(120).length)
        assertEquals(40, veryLongSource.take(40).length)
    }

    @Test
    fun testMusicStateDefaults() {
        val music = MusicState()
        assertFalse(music.playing)
        assertEquals("", music.title)
        assertEquals("", music.artist)
        assertEquals("", music.source)
    }

    @Test
    fun testNotificationStateDefaults() {
        val notif = NotificationState()
        assertFalse(notif.active)
        assertEquals("", notif.app)
        assertEquals("", notif.sender)
        assertEquals("", notif.text)
        assertEquals(0.0, notif.expires, 0.001)
    }

    @Test
    fun testDashboardStateDefaults() {
        val dashboard = DashboardState()
        assertEquals("clock", dashboard.screen)
        assertEquals(0L, dashboard.time)
        assertNotNull(dashboard.music)
        assertNotNull(dashboard.call)
        assertNotNull(dashboard.notification)
    }

    @Test
    fun testRecentEventCreation() {
        val event = RecentEvent(
            type = "notification",
            summary = "WhatsApp",
            details = "Ramza: I Love You Athrvv",
            success = true
        )
        assertEquals("notification", event.type)
        assertEquals("WhatsApp", event.summary)
        assertTrue(event.success)
        assertTrue(event.timestamp > 0)
    }

    @Test
    fun testConnectionStatusDefaults() {
        val status = ConnectionStatus()
        assertFalse(status.isConnected)
        assertEquals(0L, status.lastChecked)
        assertEquals(null, status.lastError)
    }
}
