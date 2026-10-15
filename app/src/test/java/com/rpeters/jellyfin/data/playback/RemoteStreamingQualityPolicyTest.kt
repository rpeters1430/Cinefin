package com.rpeters.jellyfin.data.playback

import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.preferences.PlaybackPreferences
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteStreamingQualityPolicyTest {
    @Test
    fun `local Emby connection ignores remote bitrate limit`() {
        val result = RemoteStreamingQualityPolicy.resolveLimit(
            serverType = ServerType.EMBY,
            serverUrl = "http://192.168.1.20:8096",
            localAddress = "http://192.168.1.20:8096",
            remoteBitrateLimit = 5_000_000,
            preferences = PlaybackPreferences.DEFAULT,
        )

        assertNull(result)
    }

    @Test
    fun `remote Emby connection uses server bitrate limit by default`() {
        val result = RemoteStreamingQualityPolicy.resolveLimit(
            serverType = ServerType.EMBY,
            serverUrl = "https://media.example.com",
            localAddress = "http://192.168.1.20:8096",
            remoteBitrateLimit = 5_000_000,
            preferences = PlaybackPreferences.DEFAULT,
        )

        assertEquals(5_000_000, result)
    }

    @Test
    fun `explicit Cinefin quality preference overrides server bitrate limit`() {
        val result = RemoteStreamingQualityPolicy.resolveLimit(
            serverType = ServerType.EMBY,
            serverUrl = "https://media.example.com",
            localAddress = "http://192.168.1.20:8096",
            remoteBitrateLimit = 5_000_000,
            preferences = PlaybackPreferences.DEFAULT.copy(maxBitrateWifi = 30_000_000),
        )

        assertNull(result)
    }

    @Test
    fun `explicit Cinefin transcoding quality overrides server bitrate limit`() {
        val result = RemoteStreamingQualityPolicy.resolveLimit(
            serverType = ServerType.EMBY,
            serverUrl = "https://media.example.com",
            localAddress = "http://192.168.1.20:8096",
            remoteBitrateLimit = 5_000_000,
            preferences = PlaybackPreferences.DEFAULT.copy(transcodingQuality = TranscodingQuality.HIGH),
        )

        assertNull(result)
    }
}
