package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.util.Log
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.utils.AppResources
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Comprehensive error handling utility for the Jellyfin Android app.
 *
 * Provides centralized error processing, user-friendly error messages,
 * retry logic suggestions, and error analytics.
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * Processes an exception and returns a user-friendly error result.
     *
     * @param e The exception to process
     * @param context Application context for string resources
     * @param operation Description of the operation that failed
     * @return ProcessedError with user message and suggested actions
     */
    fun processError(
        e: Throwable,
        context: Context? = null,
        operation: String = "Operation",
    ): ProcessedError {
        Log.e(TAG, "$operation failed", e)

        return when (e) {
            is UnknownHostException -> ProcessedError(
                userMessage = "Unable to connect to server. Please check your internet connection.",
                errorType = ErrorType.NETWORK,
                isRetryable = true,
                suggestedAction = "Check internet connection and server URL",
            )

            is ConnectException -> ProcessedError(
                userMessage = "Cannot reach the server. Please verify the server address.",
                errorType = ErrorType.NETWORK,
                isRetryable = true,
                suggestedAction = "Verify server URL and network connectivity",
            )

            is SocketTimeoutException -> ProcessedError(
                userMessage = "Request timed out. The server may be overloaded.",
                errorType = ErrorType.NETWORK,
                isRetryable = true,
                suggestedAction = "Try again in a few moments",
            )

            is SSLException -> ProcessedError(
                userMessage = "Secure connection failed. Please check your server's SSL configuration.",
                errorType = ErrorType.NETWORK,
                isRetryable = false,
                suggestedAction = "Contact administrator about SSL certificate",
            )

            is HttpException -> processHttpException(e)

            is SecurityException -> ProcessedError(
                userMessage = "Permission denied. The app needs additional permissions.",
                errorType = ErrorType.FORBIDDEN,
                isRetryable = false,
                suggestedAction = "Grant required permissions in device settings",
            )

            is java.util.concurrent.CancellationException,
            is kotlinx.coroutines.CancellationException,
            -> ProcessedError(
                userMessage = "Operation was cancelled.",
                errorType = ErrorType.OPERATION_CANCELLED,
                isRetryable = true,
                suggestedAction = "Try the operation again",
            )

            is IllegalArgumentException -> ProcessedError(
                userMessage = "Invalid data provided.",
                errorType = ErrorType.UNKNOWN,
                isRetryable = false,
                suggestedAction = "Check your input and try again",
            )

            is OutOfMemoryError -> ProcessedError(
                userMessage = "Not enough memory to complete this operation.",
                errorType = ErrorType.UNKNOWN,
                isRetryable = false,
                suggestedAction = "Close other apps and try again",
            )

            else -> ProcessedError(
                userMessage = "An unexpected error occurred: ${
                    e.message ?: context?.getString(R.string.unknown_error)
                        ?: AppResources.getString(R.string.unknown_error)
                }",
                errorType = ErrorType.UNKNOWN,
                isRetryable = true,
                suggestedAction = "Try again or restart the app",
            )
        }
    }

    /**
     * Processes HTTP exceptions to provide specific error messages.
     */
    private fun processHttpException(e: HttpException): ProcessedError {
        return when (e.code()) {
            400 -> ProcessedError(
                userMessage = "Invalid request. Please check your input.",
                errorType = ErrorType.UNKNOWN,
                isRetryable = false,
                suggestedAction = "Verify your input and try again",
            )

            401 -> ProcessedError(
                userMessage = "Authentication expired. Attempting to refresh session...",
                errorType = ErrorType.UNAUTHORIZED,
                isRetryable = true,
                suggestedAction = "Please wait while we refresh your session",
            )

            403 -> ProcessedError(
                userMessage = "Access denied. You don't have permission for this action.",
                errorType = ErrorType.FORBIDDEN,
                isRetryable = false,
                suggestedAction = "Contact administrator for access",
            )

            404 -> ProcessedError(
                userMessage = "The requested content was not found.",
                errorType = ErrorType.NOT_FOUND,
                isRetryable = false,
                suggestedAction = "Content may have been moved or deleted",
            )

            408 -> ProcessedError(
                userMessage = "Request timed out. Please try again.",
                errorType = ErrorType.NETWORK,
                isRetryable = true,
                suggestedAction = "Check connection and try again",
            )

            429 -> ProcessedError(
                userMessage = "Too many requests. Please wait before trying again.",
                errorType = ErrorType.SERVER_ERROR,
                isRetryable = true,
                suggestedAction = "Wait a few minutes before retrying",
            )

            in 500..599 -> ProcessedError(
                userMessage = "Server error (${e.code()}). Please try again later.",
                errorType = ErrorType.SERVER_ERROR,
                isRetryable = true,
                suggestedAction = "Try again later or contact support",
            )

            else -> ProcessedError(
                userMessage = "HTTP error ${e.code()}: ${e.message()}",
                errorType = ErrorType.UNKNOWN,
                isRetryable = true,
                suggestedAction = "Try again or contact support",
            )
        }
    }

    /**
     * Suggests retry delays based on error type.
     *
     * @param errorType The type of error that occurred
     * @param attemptNumber The current retry attempt number
     * @return Suggested delay in milliseconds
     */
    fun getRetryDelay(errorType: ErrorType, attemptNumber: Int): Long {
        val baseDelay = when (errorType) {
            ErrorType.NETWORK -> 1000L // 1 second for network errors
            ErrorType.SERVER_ERROR -> 2000L // 2 seconds for server errors
            ErrorType.AUTHENTICATION -> 5000L // 5 seconds for auth errors
            ErrorType.UNKNOWN -> 1500L // 1.5 seconds for unknown errors
            else -> 1000L
        }

        // Exponential backoff with jitter
        val exponentialDelay = baseDelay * (1 shl minOf(attemptNumber - 1, 6)) // Cap at 2^6
        val jitter = (Math.random() * 500).toLong() // Add up to 500ms jitter

        return exponentialDelay + jitter
    }

    /**
     * Determines if an error is worth retrying based on type and attempt count.
     *
     * @param errorType The type of error
     * @param attemptNumber Current attempt number
     * @param maxAttempts Maximum allowed attempts
     * @return True if the operation should be retried
     */
    fun shouldRetry(
        errorType: ErrorType,
        attemptNumber: Int,
        maxAttempts: Int = 3,
    ): Boolean {
        if (attemptNumber >= maxAttempts) return false

        return when (errorType) {
            ErrorType.NETWORK -> true
            ErrorType.SERVER_ERROR -> true
            ErrorType.UNKNOWN -> attemptNumber < 2 // Only retry once for unknown errors
            ErrorType.AUTHENTICATION -> false // Don't auto-retry auth errors
            ErrorType.UNAUTHORIZED -> false // ✅ FIX: Don't retry UNAUTHORIZED - let executeWithTokenRefresh handle it
            ErrorType.FORBIDDEN -> false // Don't retry permission errors
            ErrorType.NOT_FOUND -> false // Don't retry 404s
            ErrorType.BAD_REQUEST -> false // Don't retry invalid request parameters
            ErrorType.OPERATION_CANCELLED -> false // User cancelled
            ErrorType.TIMEOUT -> true // Retry timeout errors
            ErrorType.VALIDATION -> false // Don't retry validation errors
        }
    }

    /**
     * Logs error analytics for debugging and monitoring.
     *
     * @param error The processed error
     * @param operation The operation that failed
     * @param userId User ID (if available)
     * @param serverUrl Server URL (if available)
     */
    fun logErrorAnalytics(
        error: ProcessedError,
        operation: String,
        userId: String? = null,
        serverUrl: String? = null,
    ) {
        val errorData = mapOf(
            "operation" to operation,
            "errorType" to error.errorType.name,
            "isRetryable" to error.isRetryable,
            "userId" to (userId ?: "unknown"),
            "serverUrl" to (serverUrl ?: "unknown"),
            "timestamp" to System.currentTimeMillis(),
        )

        Log.w(TAG, "Error Analytics: $errorData")

        // In a production app, you would send this to your analytics service
        // Example: Analytics.track("error_occurred", errorData)
    }
}

/**
 * Data class representing a processed error with user-friendly information.
 */
data class ProcessedError(
    val userMessage: String,
    val errorType: ErrorType,
    val isRetryable: Boolean,
    val suggestedAction: String,
    val originalError: Throwable? = null,
)

/**
 * Extension function to easily process errors in ViewModels and other classes.
 */
fun Throwable.toProcessedError(
    context: Context? = null,
    operation: String = "Operation",
): ProcessedError {
    return ErrorHandler.processError(this, context, operation)
}
