package com.rpeters.jellyfin.network

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.repository.IJellyfinAuthRefreshManager
import com.rpeters.jellyfin.utils.SecureLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class JellyfinAuthInterceptorServerTypeTest {

    private var server: JellyfinServer? = null
    private val refreshManager = object : IJellyfinAuthRefreshManager {
        override fun currentAccessToken(): String? = server?.accessToken
        override fun currentServer(): JellyfinServer? = server
        override fun scheduleProactiveRefreshIfNeeded() = Unit
        override fun refreshAfterUnauthorized(attempt: Int): String? = null
    }
    private val identity = mockk<DeviceIdentityProvider> {
        every { clientName() } returns "Cinefin Android"
        every { clientVersion() } returns "1.0"
        every { deviceName() } returns "Pixel"
        every { deviceId() } returns "device-1"
        every { plexClientIdentifier() } returns "plex-client-1"
    }
    private val interceptor = JellyfinAuthInterceptor(refreshManager, identity)

    private val plexServer = JellyfinServer(
        id = "machine-1",
        name = "Plex",
        url = "https://10-0-0-5.abc.plex.direct:32400",
        accessToken = "plex-server-token",
        serverType = ServerType.PLEX,
        alternateServerUrls = listOf("https://203-0-113-9.abc.plex.direct:32400"),
    )
    private val jellyfinServer = JellyfinServer(
        id = "jf",
        name = "Jellyfin",
        url = "https://jellyfin.example.com",
        accessToken = "jellyfin-token",
        serverType = ServerType.JELLYFIN,
    )

    @Before
    fun setUp() {
        mockkObject(SecureLogger)
        every { SecureLogger.w(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun plexProfile_sendsPlexHeadersAndNoEmbyHeaders() {
        server = plexServer

        val sent = send("https://10-0-0-5.abc.plex.direct:32400/library/sections")

        assertEquals("plex-server-token", sent.header("X-Plex-Token"))
        assertEquals("plex-client-1", sent.header("X-Plex-Client-Identifier"))
        assertEquals("Cinefin", sent.header("X-Plex-Product"))
        assertEquals("1.0", sent.header("X-Plex-Version"))
        assertEquals("Android", sent.header("X-Plex-Platform"))
        assertEquals("Pixel", sent.header("X-Plex-Device"))
        assertEquals("application/json", sent.header("Accept"))
        assertNull(sent.header("X-Emby-Token"))
        assertNull(sent.header("X-Emby-Authorization"))
        assertNull(sent.header("Authorization"))
    }

    @Test
    fun plexProfile_alternateAddressGetsToken() {
        server = plexServer

        assertEquals("plex-server-token", send("https://203-0-113-9.abc.plex.direct:32400/identity").header("X-Plex-Token"))
    }

    @Test
    fun plexProfile_tokenNeverSentToAnotherHost() {
        server = plexServer

        val sent = send("https://jellyfin.example.com/Items")

        assertNull(sent.header("X-Plex-Token"))
        assertNull(sent.header("X-Emby-Token"))
        assertNull(sent.header("X-Emby-Authorization"))
    }

    @Test
    fun plexProfile_decodesPlexIdsInUrl() {
        server = plexServer
        val ratingKey = ServerIdCodec.PLEX.encode("4410")

        val sent = send("https://10-0-0-5.abc.plex.direct:32400/library/metadata/$ratingKey")

        assertEquals("/library/metadata/4410", sent.url.encodedPath)
    }

    @Test
    fun jellyfinProfile_sendsEmbyHeadersAndNoPlexHeaders() {
        server = jellyfinServer

        val sent = send("https://jellyfin.example.com/Items")

        assertEquals("jellyfin-token", sent.header("X-Emby-Token"))
        assertNotNull(sent.header("X-Emby-Authorization"))
        assertNull(sent.header("X-Plex-Token"))
        assertNull(sent.header("X-Plex-Client-Identifier"))
    }

    @Test
    fun embyProfile_sendsNoPlexHeaders() {
        server = jellyfinServer.copy(serverType = ServerType.EMBY, accessToken = "emby-token")

        val sent = send("https://jellyfin.example.com/Items")

        assertEquals("emby-token", sent.header("X-Emby-Token"))
        assertNull(sent.header("X-Plex-Token"))
    }

    private fun send(url: String): Request {
        val captured = slot<Request>()
        val request = Request.Builder().url(url).build()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(capture(captured)) } answers {
                Response.Builder().request(captured.captured).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
            }
        }
        interceptor.intercept(chain)
        return captured.captured
    }
}
