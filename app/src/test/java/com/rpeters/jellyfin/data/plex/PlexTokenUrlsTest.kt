package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.ServerType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexTokenUrlsTest {

    private val server = JellyfinServer(
        id = "machine-1",
        name = "Plex",
        url = "https://10-0-0-5.abc.plex.direct:32400",
        serverType = ServerType.PLEX,
        alternateServerUrls = listOf("https://203-0-113-9.abc.plex.direct:32400"),
    )

    @Test
    fun withToken_serverUrl_addsTokenQueryParameter() {
        val url = PlexTokenUrls.withToken("https://10-0-0-5.abc.plex.direct:32400/library/parts/7/file.mkv", "tok", server)

        assertEquals("https://10-0-0-5.abc.plex.direct:32400/library/parts/7/file.mkv?X-Plex-Token=tok", url)
    }

    @Test
    fun withToken_existingQuery_keepsItAndReplacesOldToken() {
        val url = PlexTokenUrls.withToken(
            "https://10-0-0-5.abc.plex.direct:32400/photo/:/transcode?width=300&X-Plex-Token=old",
            "new",
            server,
        )

        assertEquals("https://10-0-0-5.abc.plex.direct:32400/photo/:/transcode?width=300&X-Plex-Token=new", url)
    }

    @Test
    fun withToken_alternateAddress_addsToken() {
        val url = PlexTokenUrls.withToken("https://203-0-113-9.abc.plex.direct:32400/x", "tok", server)

        assertTrue(url.endsWith("?X-Plex-Token=tok"))
    }

    @Test
    fun withToken_otherHostOrPort_leavesUrlAlone() {
        listOf(
            "https://jellyfin.example.com/Items/1/Images/Primary",
            "https://10-0-0-5.abc.plex.direct:8096/x",
            "not a url",
        ).forEach { assertEquals(it, PlexTokenUrls.withToken(it, "tok", server)) }
    }

    @Test
    fun isServerHost_matchesOnlyServerAddresses() {
        assertTrue(PlexTokenUrls.isServerHost("https://10-0-0-5.ABC.plex.direct:32400/a".toHttpUrl(), server))
        assertFalse(PlexTokenUrls.isServerHost("https://plex.tv/api/v2/user".toHttpUrl(), server))
    }
}
