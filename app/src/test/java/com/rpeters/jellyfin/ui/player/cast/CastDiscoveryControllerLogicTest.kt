package com.rpeters.jellyfin.ui.player.cast

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.preferences.CastPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class CastDiscoveryControllerLogicTest {

    @Test
    fun `prioritizedDevices moves remembered device to top`() {
        val devices = listOf("Bedroom TV", "Living Room TV", "Office TV")

        val prioritized = CastDiscoveryController.prioritizedDevices(
            devices = devices,
            preferredDeviceName = "Living Room TV",
        )

        assertEquals("Living Room TV", prioritized.first())
    }

    @Test
    fun `shouldAutoReconnect only when preferred device is present and not yet attempted`() {
        assertTrue(
            CastDiscoveryController.shouldAutoReconnect(
                preferredDeviceName = "Living Room TV",
                autoReconnectEnabled = true,
                autoReconnectAttempted = false,
                devices = listOf("Living Room TV"),
            ),
        )
        assertFalse(
            CastDiscoveryController.shouldAutoReconnect(
                preferredDeviceName = "Living Room TV",
                autoReconnectEnabled = true,
                autoReconnectAttempted = true,
                devices = listOf("Living Room TV"),
            ),
        )
    }

    @Test
    fun `rememberedDeviceName requires remember flag and fresh session`() {
        val nowMs = 10_000L
        val prefs = CastPreferences(
            lastDeviceName = "Living Room TV",
            lastCastTimestamp = nowMs - 1_000L,
            rememberLastDevice = true,
        )

        assertEquals("Living Room TV", CastDiscoveryController.rememberedDeviceName(prefs, nowMs))
        assertNull(
            CastDiscoveryController.rememberedDeviceName(
                prefs.copy(rememberLastDevice = false),
                nowMs,
            ),
        )
    }

    @Test
    fun `autoReconnectTarget requires auto reconnect opt in`() {
        val nowMs = 10_000L
        val prefs = CastPreferences(
            lastDeviceName = "Living Room TV",
            lastCastTimestamp = nowMs - 1_000L,
            autoReconnect = true,
            rememberLastDevice = true,
        )

        assertEquals("Living Room TV", CastDiscoveryController.autoReconnectTarget(prefs, nowMs))
        assertNull(
            CastDiscoveryController.autoReconnectTarget(
                prefs.copy(autoReconnect = false),
                nowMs,
            ),
        )
    }
}
