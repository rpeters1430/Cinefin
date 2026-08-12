package com.rpeters.jellyfin.data.utils

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.security.PinningValidationException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
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

    @Test
    fun `extractStatusCode parses formatted and fallback status messages`() {
        val formattedException = InvalidStatusException(401)
        val fallbackException = InvalidStatusException(503)

        val formattedCode = RepositoryUtils.extractStatusCode(formattedException)
        val fallbackCode = RepositoryUtils.extractStatusCode(fallbackException)

        assertEquals(401, formattedCode)
        assertEquals(503, fallbackCode)
    }

    @Test
    fun `extractStatusCode logs and returns null when parsing fails`() {
        val failure = IllegalStateException("boom")
        val exception = mockk<InvalidStatusException> {
            every { message } throws failure
        }

        val statusCode = RepositoryUtils.extractStatusCode(exception)

        assertNull(statusCode)
    }

    @Test
    fun `getErrorType maps common exception types`() {
        val dnsUnknownHost = RepositoryUtils.getErrorType(UnknownHostException("no network"))
        val timeout = RepositoryUtils.getErrorType(SocketTimeoutException("timeout"))
        val cancelled = RepositoryUtils.getErrorType(CancellationException("cancelled"))
        val http = RepositoryUtils.getErrorType(httpException(500))
        val invalidStatus = RepositoryUtils.getErrorType(InvalidStatusException(401))
        val pinning = RepositoryUtils.getErrorType(
            PinningValidationException.PinMismatch(
                hostname = "example.com",
                pinRecord = null,
                attemptedPins = emptyList(),
                certificateDetails = emptyList(),
            ),
        )
        val authIllegalState = RepositoryUtils.getErrorType(IllegalStateException("No authenticated server available"))
        val tokenIllegalState = RepositoryUtils.getErrorType(IllegalStateException("Authentication token is missing"))
        val genericIllegalState = RepositoryUtils.getErrorType(IllegalStateException("Some other error"))
        val unknown = RepositoryUtils.getErrorType(IllegalArgumentException("oops"))

        assertEquals(ErrorType.DNS_RESOLUTION, dnsUnknownHost)
        assertEquals(ErrorType.NETWORK, timeout)
        assertEquals(ErrorType.OPERATION_CANCELLED, cancelled)
        assertEquals(ErrorType.SERVER_ERROR, http)
        assertEquals(ErrorType.UNAUTHORIZED, invalidStatus)
        assertEquals(ErrorType.PINNING, pinning)
        assertEquals(ErrorType.AUTHENTICATION, authIllegalState)
        assertEquals(ErrorType.AUTHENTICATION, tokenIllegalState)
        assertEquals(ErrorType.UNKNOWN, genericIllegalState)
        assertEquals(ErrorType.UNKNOWN, unknown)
    }

    @Test
    fun `getErrorType detects DNS errors from GaiException messages`() {
        // Simulate GaiException wrapped in IOException
        val eaiNoData = java.io.IOException("android.system.GaiException: EAI_NODATA (No address associated with hostname)")
        val eaiNoName = java.io.IOException("android.system.GaiException: EAI_NONAME (Name or service not known)")
        val unableToResolve = java.io.IOException("Unable to resolve host \"example.com\": No address associated with hostname")

        val errorType1 = RepositoryUtils.getErrorType(eaiNoData)
        val errorType2 = RepositoryUtils.getErrorType(eaiNoName)
        val errorType3 = RepositoryUtils.getErrorType(unableToResolve)

        assertEquals(ErrorType.DNS_RESOLUTION, errorType1)
        assertEquals(ErrorType.DNS_RESOLUTION, errorType2)
        assertEquals(ErrorType.DNS_RESOLUTION, errorType3)
    }

    @Test
    fun `getErrorType detects nested DNS errors`() {
        // Simulate nested exception with GaiException cause
        val cause = RuntimeException("android.system.GaiException: EAI_NODATA")
        val wrapper = java.io.IOException("Connection failed", cause)

        val errorType = RepositoryUtils.getErrorType(wrapper)

        assertEquals(ErrorType.DNS_RESOLUTION, errorType)
    }

    @Test
    fun `validateServer throws descriptive errors and logs warnings`() {
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
    }

    @Test
    fun `validateServer returns server and logs success when valid`() {
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
    }

    @Test
    fun `isRetryableException and is401Error reflect retry behavior`() {
        val networkRetry = RepositoryUtils.isRetryableException(ConnectException("connect"))
        val serverRetry = RepositoryUtils.isRetryableException(httpException(500))
        val unauthorizedRetry = RepositoryUtils.isRetryableException(httpException(401))
        val nonRetryable = RepositoryUtils.isRetryableException(IllegalArgumentException("bad request"))

        val http401 = RepositoryUtils.is401Error(httpException(401))
        val http500 = RepositoryUtils.is401Error(httpException(500))
        val invalidStatus401 = RepositoryUtils.is401Error(InvalidStatusException(401))
        val invalidStatusOther = RepositoryUtils.is401Error(InvalidStatusException(500))

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
}
