package com.rpeters.jellyfin.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CachePolicyInterceptorTest {

    private lateinit var connectivity: ConnectivityChecker
    private lateinit var chain: Interceptor.Chain
    private lateinit var interceptor: CachePolicyInterceptor

    @Before
    fun setup() {
        connectivity = mockk()
        every { connectivity.isOnline() } returns true
        chain = mockk()
        interceptor = CachePolicyInterceptor(connectivity)
    }

    @Test
    fun intercept_writeRequest_sendsNoCacheAndLeavesResponseUntouched() {
        val post = Request.Builder()
            .url("https://server/Items/1/Favorite")
            .post("{}".toRequestBody())
            .build()
        val sent = proceedWith(post)

        val response = interceptor.intercept(chain)

        assertEquals("no-cache", sent.captured.header("Cache-Control"))
        assertNull(response.header("Cache-Control"))
    }

    @Test
    fun intercept_authRequest_sendsNoCacheAndIsNotCached() {
        val sent = proceedWith(get("https://server/Users/AuthenticateByName"))

        val response = interceptor.intercept(chain)

        assertEquals("no-cache", sent.captured.header("Cache-Control"))
        assertNull(response.header("Cache-Control"))
    }

    @Test
    fun intercept_offlineGet_forcesStaleCacheRead() {
        every { connectivity.isOnline() } returns false
        val sent = proceedWith(get("https://server/Items"))

        interceptor.intercept(chain)

        val cacheControl = sent.captured.cacheControl
        assertTrue(cacheControl.onlyIfCached)
        assertEquals(7 * 24 * 60 * 60, cacheControl.maxStaleSeconds)
    }

    @Test
    fun intercept_onlineGet_doesNotAddRequestCacheControl() {
        val sent = proceedWith(get("https://server/Items"))

        interceptor.intercept(chain)

        assertNull(sent.captured.header("Cache-Control"))
        assertFalse(sent.captured.cacheControl.onlyIfCached)
    }

    @Test
    fun intercept_imageResponseWithoutCacheHeader_cachesForThirtyDays() {
        proceedWith(get("https://server/Items/abc/Images/Primary"))

        val response = interceptor.intercept(chain)

        assertEquals("public, max-age=2592000", response.header("Cache-Control"))
    }

    @Test
    fun intercept_apiResponseWithoutCacheHeader_cachesForOneMinute() {
        proceedWith(get("https://server/Users/u1/Items"))

        val response = interceptor.intercept(chain)

        assertEquals("public, max-age=60", response.header("Cache-Control"))
    }

    @Test
    fun intercept_responseWithServerCacheHeader_keepsServerValue() {
        proceedWith(get("https://server/Items/abc/Images/Primary"), responseCacheControl = "max-age=10")

        val response = interceptor.intercept(chain)

        assertEquals("max-age=10", response.header("Cache-Control"))
    }

    private fun get(url: String): Request = Request.Builder().url(url).build()

    private fun proceedWith(
        request: Request,
        responseCacheControl: String? = null,
    ): io.mockk.CapturingSlot<Request> {
        val sent = slot<Request>()
        every { chain.request() } returns request
        every { chain.proceed(capture(sent)) } answers {
            Response.Builder()
                .request(sent.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .apply { responseCacheControl?.let { header("Cache-Control", it) } }
                .build()
        }
        return sent
    }
}
