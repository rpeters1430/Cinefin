package com.rpeters.jellyfin.ui.utils

import com.rpeters.jellyfin.data.repository.common.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Tests for ErrorHandler to verify error processing and retry logic.
 */
class ErrorHandlerTest {

    @Test
    fun `processError handles UnknownHostException with retryable error`() {
        // Given: UnknownHostException
        val exception = UnknownHostException("Unable to resolve host")

        // When: Process error
        val processedError = ErrorHandler.processError(exception)

        // Then: Should be retryable network error
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertTrue(processedError.isRetryable)
        assertTrue(processedError.userMessage.contains("Unable to connect"))
    }

    @Test
    fun `processError handles ConnectException with retryable error`() {
        // Given: ConnectException
        val exception = ConnectException("Failed to connect to server")

        // When: Process error
        val processedError = ErrorHandler.processError(exception)

        // Then: Should be retryable network error
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertTrue(processedError.isRetryable)
        assertTrue(processedError.userMessage.contains("Cannot reach the server"))
    }

    @Test
    fun `processError handles SocketException with connection abort message`() {
        // Given: SocketException with "connection abort" message
        val exception = SocketException("Software caused connection abort")

        // When: Process error
        val processedError = ErrorHandler.processError(exception)

        // Then: Should be retryable network error with specific message
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertTrue(processedError.isRetryable)
        assertTrue(processedError.userMessage.contains("connection was interrupted"))
        assertTrue(processedError.suggestedAction.contains("stable network"))
    }

    @Test
    fun `processError handles generic SocketException`() {
        // Given: Generic SocketException
        val exception = SocketException("Broken pipe")

        // When: Process error
        val processedError = ErrorHandler.processError(exception)

        // Then: Should be retryable network error
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertTrue(processedError.isRetryable)
        assertTrue(processedError.userMessage.contains("Network error"))
    }

    @Test
    fun `processError handles SocketTimeoutException`() {
        // Given: SocketTimeoutException
        val exception = SocketTimeoutException("Read timed out")

        // When: Process error
        val processedError = ErrorHandler.processError(exception)

        // Then: Should be retryable network error
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertTrue(processedError.isRetryable)
        assertTrue(processedError.userMessage.contains("timed out"))
    }

    @Test
    fun `processError handles SSLException with handshake failure`() {
        // Given: SSLException with handshake message
        val exception = SSLException("SSL handshake failed")

        // When: Process error
        val processedError = ErrorHandler.processError(exception)

        // Then: Should be retryable network error
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertTrue(processedError.isRetryable)
        assertTrue(processedError.userMessage.contains("handshake failed"))
    }

    @Test
    fun `processError handles SSLException with certificate failure`() {
        // Given: SSLException with certificate message
        val exception = SSLException("Certificate validation failed")

        // When: Process error
        val processedError = ErrorHandler.processError(exception)

        // Then: Should not be retryable
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertFalse(processedError.isRetryable)
        assertTrue(processedError.userMessage.contains("certificate validation"))
    }

    @Test
    fun `shouldRetry returns true for network errors within attempt limit`() {
        // Given: Network error with attempt 1
        val shouldRetry = ErrorHandler.shouldRetry(ErrorType.NETWORK, attemptNumber = 1, maxAttempts = 3)

        // Then: Should retry
        assertTrue(shouldRetry)
    }

    @Test
    fun `shouldRetry returns false for network errors exceeding attempt limit`() {
        // Given: Network error with attempt 3
        val shouldRetry = ErrorHandler.shouldRetry(ErrorType.NETWORK, attemptNumber = 3, maxAttempts = 3)

        // Then: Should not retry
        assertFalse(shouldRetry)
    }

    @Test
    fun `shouldRetry returns false for authentication errors`() {
        // Given: Authentication error
        val shouldRetry = ErrorHandler.shouldRetry(ErrorType.AUTHENTICATION, attemptNumber = 1, maxAttempts = 3)

        // Then: Should not retry
        assertFalse(shouldRetry)
    }

    @Test
    fun `shouldRetry returns false for unauthorized errors`() {
        // Given: Unauthorized error
        val shouldRetry = ErrorHandler.shouldRetry(ErrorType.UNAUTHORIZED, attemptNumber = 1, maxAttempts = 3)

        // Then: Should not retry (handled by executeWithTokenRefresh)
        assertFalse(shouldRetry)
    }

    @Test
    fun `shouldRetry returns true for server errors within attempt limit`() {
        // Given: Server error with attempt 1
        val shouldRetry = ErrorHandler.shouldRetry(ErrorType.SERVER_ERROR, attemptNumber = 1, maxAttempts = 3)

        // Then: Should retry
        assertTrue(shouldRetry)
    }

    @Test
    fun `shouldRetry returns true for timeout errors`() {
        // Given: Timeout error
        val shouldRetry = ErrorHandler.shouldRetry(ErrorType.TIMEOUT, attemptNumber = 1, maxAttempts = 3)

        // Then: Should retry
        assertTrue(shouldRetry)
    }

    @Test
    fun `getRetryDelay returns increasing delays with exponential backoff`() {
        // Given: Multiple retry attempts
        val delay1 = ErrorHandler.getRetryDelay(ErrorType.NETWORK, attemptNumber = 1)
        val delay2 = ErrorHandler.getRetryDelay(ErrorType.NETWORK, attemptNumber = 2)
        val delay3 = ErrorHandler.getRetryDelay(ErrorType.NETWORK, attemptNumber = 3)

        // Then: Delays should increase exponentially (with some jitter tolerance)
        assertTrue(delay2 > delay1)
        assertTrue(delay3 > delay2)
        assertTrue(delay1 >= 1000) // Base delay is 1000ms
    }

    @Test
    fun `getRetryDelay returns different base delays for different error types`() {
        // Given: Different error types
        val networkDelay = ErrorHandler.getRetryDelay(ErrorType.NETWORK, attemptNumber = 1)
        val serverDelay = ErrorHandler.getRetryDelay(ErrorType.SERVER_ERROR, attemptNumber = 1)
        val authDelay = ErrorHandler.getRetryDelay(ErrorType.AUTHENTICATION, attemptNumber = 1)

        // Then: Server errors should have longer base delay than network
        assertTrue(serverDelay >= networkDelay)
        // Auth errors should have longest base delay
        assertTrue(authDelay >= serverDelay)
    }

    @Test
    fun `toProcessedError extension function works correctly`() {
        // Given: An exception
        val exception = SocketException("Software caused connection abort")

        // When: Use extension function
        val processedError = exception.toProcessedError(operation = "Test Operation")

        // Then: Should process correctly
        assertEquals(ErrorType.NETWORK, processedError.errorType)
        assertTrue(processedError.isRetryable)
    }
}
