package com.rpeters.jellyfin.network

import com.rpeters.jellyfin.data.repository.IJellyfinAuthRefreshManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JellyfinAuthInterceptorTest {

    private lateinit var authRefreshManager: IJellyfinAuthRefreshManager
    private lateinit var deviceIdentityProvider: DeviceIdentityProvider
    private lateinit var chain: Interceptor.Chain
    private lateinit var interceptor: JellyfinAuthInterceptor

    @Before
    fun setup() {
        authRefreshManager = mockk(relaxed = true)
        deviceIdentityProvider = mockk {
            every { clientName() } returns "Cinefin Android"
            every { clientVersion() } returns "1.0"
            every { deviceName() } returns "Pixel"
            every { deviceId() } returns "device-123"
        }
        chain = mockk()
        interceptor = JellyfinAuthInterceptor(authRefreshManager, deviceIdentityProvider)
    }

    @Test
    fun intercept_withToken_attachesAllAuthHeaders() {
        every { authRefreshManager.currentAccessToken() } returns "secret-token"
        val sent = captureProceededRequest(request("https://server/Items"))

        interceptor.intercept(chain)

        val headers = sent.captured.headers
        assertEquals("secret-token", headers["X-Emby-Token"])
        assertEquals("MediaBrowser Token=\"secret-token\"", headers["Authorization"])
        assertEquals("Cinefin Android/1.0", headers["User-Agent"])
        assertEquals("keep-alive", headers["Connection"])
        assertEquals(
            "MediaBrowser Client=\"Cinefin Android\", Device=\"Pixel\", DeviceId=\"device-123\", " +
                "Version=\"1.0\", Token=\"secret-token\"",
            headers["X-Emby-Authorization"],
        )
        verify { authRefreshManager.scheduleProactiveRefreshIfNeeded() }
    }

    @Test
    fun intercept_withoutToken_sendsIdentityHeaderOnly() {
        every { authRefreshManager.currentAccessToken() } returns null
        val sent = captureProceededRequest(request("https://server/Items"))

        interceptor.intercept(chain)

        val headers = sent.captured.headers
        assertNull(headers["X-Emby-Token"])
        assertNull(headers["Authorization"])
        val identity = headers["X-Emby-Authorization"]
        assertNotNull(identity)
        assertFalse(identity!!.contains("Token="))
    }

    @Test
    fun intercept_authenticationRequest_doesNotAttachOrRefreshToken() {
        every { authRefreshManager.currentAccessToken() } returns "secret-token"
        val sent = captureProceededRequest(request("https://server/Users/AuthenticateByName"))

        interceptor.intercept(chain)

        val headers = sent.captured.headers
        assertNull(headers["X-Emby-Token"])
        assertFalse(headers["X-Emby-Authorization"]!!.contains("Token="))
        verify(exactly = 0) { authRefreshManager.currentAccessToken() }
        verify(exactly = 0) { authRefreshManager.scheduleProactiveRefreshIfNeeded() }
    }

    @Test
    fun authenticate_on401_retriesWithRefreshedToken() {
        every { authRefreshManager.refreshAfterUnauthorized(1) } returns "new-token"

        val retry = interceptor.authenticate(null, response(request("https://server/Items"), 401))

        assertNotNull(retry)
        assertEquals("new-token", retry!!.header("X-Emby-Token"))
        assertTrue(retry.header("X-Emby-Authorization")!!.contains("Token=\"new-token\""))
    }

    @Test
    fun authenticate_refreshFails_returnsNull() {
        every { authRefreshManager.refreshAfterUnauthorized(any()) } returns null

        val retry = interceptor.authenticate(null, response(request("https://server/Items"), 401))

        assertNull(retry)
    }

    @Test
    fun authenticate_non401_doesNotRefresh() {
        val retry = interceptor.authenticate(null, response(request("https://server/Items"), 403))

        assertNull(retry)
        verify(exactly = 0) { authRefreshManager.refreshAfterUnauthorized(any()) }
    }

    @Test
    fun authenticate_authenticationRequest_doesNotRefresh() {
        val retry = interceptor.authenticate(
            null,
            response(request("https://server/Users/AuthenticateByName"), 401),
        )

        assertNull(retry)
        verify(exactly = 0) { authRefreshManager.refreshAfterUnauthorized(any()) }
    }

    @Test
    fun authenticate_afterMaxRetries_givesUp() {
        val req = request("https://server/Items")
        val first = response(req, 401)
        val second = response(req, 401, prior = first)
        val third = response(req, 401, prior = second)

        val retry = interceptor.authenticate(null, third)

        assertNull(retry)
        verify(exactly = 0) { authRefreshManager.refreshAfterUnauthorized(any()) }
    }

    @Test
    fun authenticate_passesAttemptCountToRefreshManager() {
        every { authRefreshManager.refreshAfterUnauthorized(2) } returns "new-token"
        val req = request("https://server/Items")
        val second = response(req, 401, prior = response(req, 401))

        val retry = interceptor.authenticate(null, second)

        assertNotNull(retry)
        verify { authRefreshManager.refreshAfterUnauthorized(2) }
    }

    private fun request(url: String): Request = Request.Builder().url(url).build()

    private fun response(request: Request, code: Int, prior: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status $code")
            .priorResponse(prior)
            .build()

    private fun captureProceededRequest(original: Request): io.mockk.CapturingSlot<Request> {
        val sent = slot<Request>()
        every { chain.request() } returns original
        every { chain.proceed(capture(sent)) } answers { response(sent.captured, 200) }
        return sent
    }
}
