package com.rpeters.jellyfin.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketException

/**
 * Tests for NetworkStateInterceptor to verify network error handling and context.
 */
class NetworkStateInterceptorTest {

    private lateinit var connectivityChecker: ConnectivityChecker
    private lateinit var interceptor: NetworkStateInterceptor
    private lateinit var chain: Interceptor.Chain

    @Before
    fun setup() {
        connectivityChecker = mockk(relaxed = true)
        interceptor = NetworkStateInterceptor(connectivityChecker)
        chain = mockk(relaxed = true)
    }

    @Test
    fun `intercept throws IOException when no network connectivity`() {
        // Given: No network connectivity
        every { connectivityChecker.isOnline() } returns false
        val request = Request.Builder().url("https://example.com").build()
        every { chain.request() } returns request

        // When/Then: Should throw IOException with appropriate message
        try {
            interceptor.intercept(chain)
            throw AssertionError("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("No internet connection"))
        }
    }

    @Test
    fun `intercept proceeds normally when network is available`() {
        // Given: Network is available
        every { connectivityChecker.isOnline() } returns true
        every { connectivityChecker.getNetworkType() } returns NetworkType.WIFI

        val request = Request.Builder().url("https://example.com").build()
        every { chain.request() } returns request

        val mockResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .build()
        every { chain.proceed(request) } returns mockResponse

        // When: Intercept
        val response = interceptor.intercept(chain)

        // Then: Should proceed and return response
        assertEquals(200, response.code)
        verify { chain.proceed(request) }
    }

    @Test
    fun `intercept enhances SocketException with network context`() {
        // Given: Network is available but connection aborted
        every { connectivityChecker.isOnline() } returns true
        every { connectivityChecker.getNetworkType() } returns NetworkType.CELLULAR

        val request = Request.Builder().url("https://example.com").build()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws SocketException("Software caused connection abort")

        // When/Then: Should throw IOException with enhanced message
        try {
            interceptor.intercept(chain)
            throw AssertionError("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Connection aborted"))
            assertTrue(e.message!!.contains("CELLULAR"))
        }
    }

    @Test
    fun `intercept enhances connection reset with context`() {
        // Given: Network available but connection reset
        every { connectivityChecker.isOnline() } returns true
        every { connectivityChecker.getNetworkType() } returns NetworkType.WIFI

        val request = Request.Builder().url("https://example.com").build()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws SocketException("Connection reset by peer")

        // When/Then: Should throw IOException with enhanced message
        try {
            interceptor.intercept(chain)
            throw AssertionError("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Connection reset"))
            assertTrue(e.message!!.contains("WIFI"))
        }
    }

    @Test
    fun `intercept handles generic SocketException`() {
        // Given: Network available but socket error
        every { connectivityChecker.isOnline() } returns true
        every { connectivityChecker.getNetworkType() } returns NetworkType.WIFI

        val request = Request.Builder().url("https://example.com").build()
        every { chain.request() } returns request
        every { chain.proceed(request) } throws SocketException("Some socket error")

        // When/Then: Should throw IOException with network type
        try {
            interceptor.intercept(chain)
            throw AssertionError("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Network error"))
            assertTrue(e.message!!.contains("WIFI"))
        }
    }
}
