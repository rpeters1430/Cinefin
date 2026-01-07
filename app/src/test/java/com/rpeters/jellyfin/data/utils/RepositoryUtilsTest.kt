package com.rpeters.jellyfin.data.utils

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.security.PinningValidationException
import com.rpeters.jellyfin.utils.SecureLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RepositoryUtilsTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `extractStatusCode parses formatted and fallback status messages`() {
        mockkObject(SecureLogger)
        val formattedException = invalidStatusException("Invalid HTTP status in response: 401")
        val fallbackException = invalidStatusException("Upstream returned ??? 503 ???")

        val formattedCode = RepositoryUtils.extractStatusCode(formattedException)
        val fallbackCode = RepositoryUtils.extractStatusCode(fallbackException)

        assertEquals(401, formattedCode)
        assertEquals(503, fallbackCode)
    }

    @Test
    fun `extractStatusCode logs and returns null when parsing fails`() {
        mockkObject(SecureLogger)
        val failure = IllegalStateException("boom")
        val exception = mockk<InvalidStatusException> {
            every { message } throws failure
        }
        every { SecureLogger.w(any(), any(), any()) } returns Unit

        val statusCode = RepositoryUtils.extractStatusCode(exception)

        assertNull(statusCode)
        verify(exactly = 1) {
            SecureLogger.w("RepositoryUtils", "Failed to extract status code from exception", failure)
        }
    }

    @Test
    fun `getErrorType maps common exception types`() {
        val network = RepositoryUtils.getErrorType(UnknownHostException("no network"))
        val timeout = RepositoryUtils.getErrorType(SocketTimeoutException("timeout"))
        val cancelled = RepositoryUtils.getErrorType(CancellationException("cancelled"))
        val http = RepositoryUtils.getErrorType(httpException(500))
        val invalidStatus = RepositoryUtils.getErrorType(invalidStatusException("Invalid HTTP status in response: 401"))
        val pinning = RepositoryUtils.getErrorType(
            PinningValidationException.PinMismatch(
                hostname = "example.com",
                pinRecord = null,
                attemptedPins = emptyList(),
                certificateDetails = emptyList(),
            ),
        )
        val unknown = RepositoryUtils.getErrorType(IllegalArgumentException("oops"))

        assertEquals(ErrorType.NETWORK, network)
        assertEquals(ErrorType.NETWORK, timeout)
        assertEquals(ErrorType.OPERATION_CANCELLED, cancelled)
        assertEquals(ErrorType.SERVER_ERROR, http)
        assertEquals(ErrorType.UNAUTHORIZED, invalidStatus)
        assertEquals(ErrorType.PINNING, pinning)
        assertEquals(ErrorType.UNKNOWN, unknown)
    }

    @Test
    fun `validateServer throws descriptive errors and logs warnings`() {
        mockkObject(SecureLogger)
        every { SecureLogger.w(any(), any(), any()) } returns Unit

        val nullServer = kotlin.runCatching { RepositoryUtils.validateServer(null) }.exceptionOrNull()
        val missingToken = kotlin.runCatching {
            RepositoryUtils.validateServer(
                JellyfinServer(
                    id = "id",
                    name = "name",
                    url = "url",
                    userId = "userId",
                    accessToken = null,
                ),
            )
        }.exceptionOrNull()
        val missingUserId = kotlin.runCatching {
            RepositoryUtils.validateServer(
                JellyfinServer(
                    id = "id",
                    name = "name",
                    url = "url",
                    accessToken = "token",
                    userId = null,
                ),
            )
        }.exceptionOrNull()

        assertEquals(
            "Server is not available. Please check your connection and try logging in again.",
            nullServer?.message,
        )
        assertEquals("Authentication token is missing. Please log in again.", missingToken?.message)
        assertEquals("User authentication is incomplete. Please log in again.", missingUserId?.message)
        verify(exactly = 3) { SecureLogger.w(any(), any(), any()) }
    }

    @Test
    fun `validateServer returns server and logs success when valid`() {
        mockkObject(SecureLogger)
        every { SecureLogger.v(any(), any(), any()) } returns Unit
        every { SecureLogger.w(any(), any(), any()) } returns Unit
        val server = JellyfinServer(
            id = "id",
            name = "name",
            url = "url",
            accessToken = "token",
            userId = "userId",
            username = "user",
        )

        val result = RepositoryUtils.validateServer(server)

        assertSame(server, result)
        verify(exactly = 1) { SecureLogger.v("RepositoryUtils", match { it.contains("Server validation passed") }, any()) }
        verify(exactly = 0) { SecureLogger.w(any(), any(), any()) }
    }

    @Test
    fun `isRetryableException and is401Error reflect retry behavior`() {
        val networkRetry = RepositoryUtils.isRetryableException(ConnectException("connect"))
        val serverRetry = RepositoryUtils.isRetryableException(httpException(500))
        val unauthorizedRetry = RepositoryUtils.isRetryableException(httpException(401))
        val nonRetryable = RepositoryUtils.isRetryableException(IllegalArgumentException("bad request"))

        val http401 = RepositoryUtils.is401Error(httpException(401))
        val http500 = RepositoryUtils.is401Error(httpException(500))
        val invalidStatus401 = RepositoryUtils.is401Error(invalidStatusException("Response status 401"))
        val invalidStatusOther = RepositoryUtils.is401Error(invalidStatusException("Server error 500"))

        assertTrue(networkRetry)
        assertTrue(serverRetry)
        assertTrue(unauthorizedRetry)
        assertFalse(nonRetryable)

        assertTrue(http401)
        assertFalse(http500)
        assertTrue(invalidStatus401)
        assertFalse(invalidStatusOther)
    }

    private fun httpException(code: Int): HttpException {
        val response = Response.error<Any>(
            code,
            "error".toResponseBody("text/plain".toMediaType()),
        )
        return HttpException(response)
    }

    private fun invalidStatusException(message: String?): InvalidStatusException {
        return mockk {
            every { this@mockk.message } returns message
        }
    }
}
