package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import com.rpeters.jellyfin.data.model.ServerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexUrlBuilderTest {

    private val server = JellyfinServer(
        id = "plex-server-1",
        name = "Test PMS",
        url = "https://192-168-50-114.plex.direct:32400",
        serverType = ServerType.PLEX,
        accessToken = "test-token-xyz",
    )

    @Test
    fun buildDirectPlayStreamUrl_withPartKey_includesTokenAndDownloadZero() {
        val itemId = ServerIdCodec.PLEX.encode("1001").toString()
        val url = PlexUrlBuilder.buildDirectPlayStreamUrl(server, itemId, "/library/parts/8001/file.mkv")

        assertNotNull(url)
        assertEquals(
            "https://192-168-50-114.plex.direct:32400/library/parts/8001/file.mkv?download=0&X-Plex-Token=test-token-xyz",
            url,
        )
    }

    @Test
    fun buildDirectPlayStreamUrl_withoutPartKey_usesMetadataPath() {
        val itemId = ServerIdCodec.PLEX.encode("1001").toString()
        val url = PlexUrlBuilder.buildDirectPlayStreamUrl(server, itemId)

        assertNotNull(url)
        assertEquals(
            "https://192-168-50-114.plex.direct:32400/library/metadata/1001?download=0&X-Plex-Token=test-token-xyz",
            url,
        )
    }

    @Test
    fun buildTranscodeStreamUrl_generatesValidStartM3u8WithParameters() {
        val itemId = ServerIdCodec.PLEX.encode("1001").toString()
        val url = PlexUrlBuilder.buildTranscodeStreamUrl(
            server = server,
            itemId = itemId,
            session = "test-session-uuid",
            maxBitrate = 8_000_000,
            maxWidth = 1920,
            maxHeight = 1080,
            audioChannels = 6,
            allowVideoStreamCopy = true,
        )

        assertNotNull(url)
        assertTrue(url!!.startsWith("https://192-168-50-114.plex.direct:32400/video/:/transcode/universal/start.m3u8?"))
        assertTrue(url.contains("path=%2Flibrary%2Fmetadata%2F1001"))
        assertTrue(url.contains("session=test-session-uuid"))
        assertTrue(url.contains("protocol=hls"))
        assertTrue(url.contains("maxVideoBitrate=8000"))
        assertTrue(url.contains("videoResolution=1920x1080"))
        assertTrue(url.contains("audioChannels=6"))
        assertTrue(url.contains("directStream=1"))
        assertTrue(url.contains("X-Plex-Token=test-token-xyz"))
    }

    @Test
    fun buildDownloadUrl_includesTokenAndDownloadOne() {
        val itemId = ServerIdCodec.PLEX.encode("1001").toString()
        val url = PlexUrlBuilder.buildDownloadUrl(server, itemId, "/library/parts/8001/file.mkv")

        assertNotNull(url)
        assertEquals(
            "https://192-168-50-114.plex.direct:32400/library/parts/8001/file.mkv?download=1&X-Plex-Token=test-token-xyz",
            url,
        )
    }

    @Test
    fun buildTranscodeStopUrl_includesSessionAndToken() {
        val url = PlexUrlBuilder.buildTranscodeStopUrl(server, "test-session-uuid")

        assertNotNull(url)
        assertEquals(
            "https://192-168-50-114.plex.direct:32400/video/:/transcode/universal/stop?session=test-session-uuid&X-Plex-Token=test-token-xyz",
            url,
        )
    }
}
