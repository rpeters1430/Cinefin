package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.network.DeviceIdentityProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexTvClientTest {

    private val identity = mockk<DeviceIdentityProvider> {
        every { plexClientIdentifier() } returns CLIENT_ID
        every { clientVersion() } returns VERSION
        every { deviceName() } returns DEVICE
    }
    private val server = MockWebServer().apply { start() }
    private val client = PlexTvClient(identity, server.url("/").toString())

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun createPin_realCapture_readsIdCodeAndExpiry() = runTest {
        server.enqueue(fixture("pin_create.json", code = 201))

        val pin = (client.createPin() as ApiResult.Success).data

        assertEquals(1297906816L, pin.id)
        assertEquals("QPRV", pin.code)
        assertEquals(900, pin.expiresIn)
        assertNull(pin.authToken)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/pins?strong=false", request.path)
    }

    @Test
    fun requests_sendPlexIdentityHeadersAndNoTokenForPins() = runTest {
        server.enqueue(fixture("pin_poll_pending.json"))

        client.getPin(1297906816L)

        val request = server.takeRequest()
        assertEquals(CLIENT_ID, request.getHeader(PlexClientHeaders.CLIENT_IDENTIFIER))
        assertEquals("Cinefin", request.getHeader(PlexClientHeaders.PRODUCT))
        assertEquals(VERSION, request.getHeader(PlexClientHeaders.VERSION))
        assertEquals("Android", request.getHeader(PlexClientHeaders.PLATFORM))
        assertEquals(DEVICE, request.getHeader(PlexClientHeaders.DEVICE))
        assertEquals(JSON, request.getHeader("Accept"))
        assertNull(request.getHeader(PlexClientHeaders.TOKEN))
        assertNull(request.getHeader("X-Emby-Token"))
    }

    @Test
    fun getPin_approved_returnsAccountToken() = runTest {
        server.enqueue(fixture("pin_poll_approved_standin.json"))

        val pin = (client.getPin(1297906816L) as ApiResult.Success).data

        assertEquals(ACCOUNT_TOKEN, pin.authToken)
        assertFalse(pin.toString().contains(ACCOUNT_TOKEN))
    }

    @Test
    fun getPin_expiredRealCapture_isNotFoundWithPlexMessage() = runTest {
        server.enqueue(fixture("pin_not_found.json", code = 404))

        val error = client.getPin(1L) as ApiResult.Error

        assertEquals(ErrorType.NOT_FOUND, error.errorType)
        assertEquals("Code not found or expired", error.message)
    }

    @Test
    fun getUser_sendsAccountTokenAndReadsUser() = runTest {
        server.enqueue(fixture("user_standin.json"))

        val user = (client.getUser(ACCOUNT_TOKEN) as ApiResult.Success).data

        assertEquals(12345678L, user.id)
        assertEquals("dana", user.displayName)
        val request = server.takeRequest()
        assertEquals("/api/v2/user", request.path)
        assertEquals(ACCOUNT_TOKEN, request.getHeader(PlexClientHeaders.TOKEN))
    }

    @Test
    fun getUser_badTokenRealCapture_isUnauthorized() = runTest {
        server.enqueue(fixture("unauthenticated.json", code = 401))

        val error = client.getUser("expired") as ApiResult.Error

        assertEquals(ErrorType.UNAUTHORIZED, error.errorType)
        assertEquals("User could not be authenticated", error.message)
    }

    @Test
    fun getServers_keepsOwnedAndSharedServersAndDropsPlayers() = runTest {
        server.enqueue(fixture("resources_standin.json"))

        val servers = (client.getServers(ACCOUNT_TOKEN) as ApiResult.Success).data

        assertEquals(listOf("machine-owned-1", "machine-shared-2"), servers.map { it.clientIdentifier })
        val owned = servers[0]
        assertTrue(owned.owned)
        assertEquals("owned-server-token", owned.accessToken)
        assertEquals(4, owned.connections.size)
        assertTrue(owned.connections.single { it.uri == "https://10-0-0-5.abc123.plex.direct:32400" }.local)
        assertTrue(owned.connections.single { it.uri.startsWith("https://198-51-100-20") }.relay)
        val shared = servers[1]
        assertFalse(shared.owned)
        assertEquals("alex", shared.sourceTitle)
        assertEquals(SHARED_TOKEN, shared.accessToken)
        assertFalse(shared.toString().contains(SHARED_TOKEN))
        val request = server.takeRequest()
        assertEquals("/api/v2/resources?includeHttps=1&includeRelay=1", request.path)
        assertEquals(ACCOUNT_TOKEN, request.getHeader(PlexClientHeaders.TOKEN))
    }

    @Test
    fun call_unreadableBody_isServerErrorNotCrash() = runTest {
        server.enqueue(MockResponse().setBody("<html>maintenance</html>"))

        val error = client.getUser(ACCOUNT_TOKEN) as ApiResult.Error

        assertEquals(ErrorType.SERVER_ERROR, error.errorType)
    }

    @Test
    fun call_redirect_isNotFollowed() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/steal"))

        val error = client.getUser(ACCOUNT_TOKEN) as ApiResult.Error

        assertEquals(1, server.requestCount)
        assertEquals(ErrorType.UNKNOWN, error.errorType)
    }

    @Test
    fun call_serverUnreachable_isNetworkError() = runTest {
        server.shutdown()

        val error = client.createPin() as ApiResult.Error

        assertEquals(ErrorType.NETWORK, error.errorType)
    }

    private fun fixture(name: String, code: Int = 200): MockResponse {
        val body = requireNotNull(javaClass.classLoader?.getResource("plex/plextv/$name")) { "Missing fixture $name" }
            .readText()
        return MockResponse().setResponseCode(code).setHeader("Content-Type", JSON).setBody(body)
    }

    private companion object {
        const val CLIENT_ID = "cinefin-test-client"
        const val ACCOUNT_TOKEN = "account-token-standin"
        const val DEVICE = "Pixel 9"
        const val VERSION = "15.104"
        const val JSON = "application/json"
        const val SHARED_TOKEN = "shared-server-token"
    }
}
