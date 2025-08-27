package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.common.ApiResult
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Intelligent retry strategy with exponential backoff and error-specific handling.
 * Provides optimized retry logic for different types of network errors.
 */
@Singleton
class RetryStrategy @Inject constructor() {
    companion object {
        private const val TAG = "RetryStrategy"
        private const val DEFAULT_MAX_RETRIES = 3
        private const val MAX_RETRY_DELAY_MS = 10000L
    }

    /**
     * Execute with intelligent retry based on error type and network conditions
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        operation: suspend () -> T,
    ): ApiResult<T> {
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                val result = operation()
                if (attempt > 0) {
                    logDebug("Operation succeeded on attempt ${attempt + 1}")
                }
                return ApiResult.Success(result)
            } catch (e: Exception) {
                lastException = e

                if (attempt < maxRetries && shouldRetry(e, attempt)) {
                    val delay = calculateRetryDelay(e, attempt)
                    logDebug("Retrying operation (attempt ${attempt + 1}/${maxRetries + 1}) after ${delay}ms delay. Error: ${e.message}")
                    delay(delay)
                } else {
                    logDebug("Operation failed after ${attempt + 1} attempts. Final error: ${e.message}")
                    break
                }
            }
        }

        return ApiResult.Error(
            message = "Operation failed after ${maxRetries + 1} attempts",
            cause = lastException,
        )
    }

    /**
     * Determine if operation should be retried based on error type
     */
    private fun shouldRetry(exception: Exception, attempt: Int): Boolean {
        return when (exception) {
            is HttpException -> {
                val statusCode = exception.code()
                when (statusCode) {
                    408, 429, 500, 502, 503, 504 -> {
                        logDebug("Retrying HTTP error $statusCode (attempt $attempt)")
                        true // Retryable status codes
                    }
                    401, 403, 404 -> {
                        logDebug("Not retrying HTTP error $statusCode (auth/not found)")
                        false // Don't retry auth/not found errors
                    }
                    else -> {
                        val shouldRetry = attempt < 2
                        logDebug("Limited retry for HTTP error $statusCode: $shouldRetry")
                        shouldRetry // Limited retries for other errors
                    }
                }
            }
            is SocketTimeoutException -> {
                logDebug("Retrying socket timeout (attempt $attempt)")
                true
            }
            is ConnectException -> {
                logDebug("Retrying connection exception (attempt $attempt)")
                true
            }
            is UnknownHostException -> {
                logDebug("Not retrying DNS failure: ${exception.message}")
                false // Don't retry DNS failures
            }
            else -> {
                val shouldRetry = attempt < 1
                logDebug("Limited retry for unknown error: $shouldRetry")
                shouldRetry // Limited retries for unknown errors
            }
        }
    }

    /**
     * Calculate retry delay with exponential backoff and jitter
     */
    private fun calculateRetryDelay(exception: Exception, attempt: Int): Long {
        val baseDelay = when (exception) {
            is HttpException -> when (exception.code()) {
                429 -> 5000L // Rate limited - longer delay
                503 -> 2000L // Service unavailable
                else -> 1000L // Other server errors
            }
            else -> 1000L // Network errors
        }

        val exponentialDelay = baseDelay * (2.0.pow(attempt.toDouble())).toLong()
        val jitter = (Math.random() * 0.1 * exponentialDelay).toLong() // 10% jitter

        return minOf(exponentialDelay + jitter, MAX_RETRY_DELAY_MS)
    }

    /**
     * Execute with custom retry configuration
     */
    suspend fun <T> executeWithCustomRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        baseDelayMs: Long = 1000L,
        maxDelayMs: Long = MAX_RETRY_DELAY_MS,
        shouldRetryPredicate: (Exception, Int) -> Boolean = { _, _ -> true },
        operation: suspend () -> T,
    ): ApiResult<T> {
        var lastException: Exception? = null

        repeat(maxRetries + 1) { attempt ->
            try {
                val result = operation()
                if (attempt > 0) {
                    logDebug("Custom retry operation succeeded on attempt ${attempt + 1}")
                }
                return ApiResult.Success(result)
            } catch (e: Exception) {
                lastException = e

                if (attempt < maxRetries && shouldRetryPredicate(e, attempt)) {
                    val delay = calculateCustomRetryDelay(baseDelayMs, maxDelayMs, attempt)
                    logDebug("Custom retry (attempt ${attempt + 1}/${maxRetries + 1}) after ${delay}ms delay")
                    delay(delay)
                } else {
                    logDebug("Custom retry failed after ${attempt + 1} attempts")
                    break
                }
            }
        }

        return ApiResult.Error(
            message = "Custom retry operation failed after ${maxRetries + 1} attempts",
            cause = lastException,
        )
    }

    /**
     * Calculate custom retry delay
     */
    private fun calculateCustomRetryDelay(baseDelayMs: Long, maxDelayMs: Long, attempt: Int): Long {
        val exponentialDelay = baseDelayMs * (2.0.pow(attempt.toDouble())).toLong()
        val jitter = (Math.random() * 0.1 * exponentialDelay).toLong()
        return minOf(exponentialDelay + jitter, maxDelayMs)
    }

    /**
     * Helper function for debug logging
     */
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
