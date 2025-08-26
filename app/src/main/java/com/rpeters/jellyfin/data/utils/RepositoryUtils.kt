package com.rpeters.jellyfin.data.utils

import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.repository.common.ErrorType
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import retrofit2.HttpException
import java.util.*

/**
 * ✅ PHASE 4: Utility functions extracted from JellyfinRepository
 * Centralized helper functions for error handling, validation, and parsing
 */
object RepositoryUtils {
    private const val TAG = "RepositoryUtils"

    /**
     * Extracts HTTP status code from InvalidStatusException using multiple patterns
     */
    fun extractStatusCode(e: InvalidStatusException): Int? {
        return try {
            val message = e.message ?: return null

            // Pattern 1: "Invalid HTTP status in response: 401"
            val pattern1 = """Invalid HTTP status in response:\s*(\d{3})""".toRegex()
            pattern1.find(message)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

            // Pattern 2: Any 3-digit number that looks like an HTTP status
            val pattern2 = """\b([4-5]\d{2})\b""".toRegex()
            pattern2.find(message)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

            // Pattern 3: Generic 3-digit number extraction
            val pattern3 = """\b(\d{3})\b""".toRegex()
            val matches = pattern3.findAll(message).map { it.groupValues[1].toIntOrNull() }.filterNotNull()

            // Return the first match that looks like an HTTP status code
            matches.firstOrNull { it in 400..599 }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract status code from exception", e)
            null
        }
    }

    /**
     * Determines error type from various exception types
     */
    fun getErrorType(e: Throwable): ErrorType {
        return when (e) {
            is java.util.concurrent.CancellationException,
            is kotlinx.coroutines.CancellationException,
            -> ErrorType.OPERATION_CANCELLED

            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            -> ErrorType.NETWORK

            is HttpException -> when (e.code()) {
                401 -> ErrorType.UNAUTHORIZED
                403 -> ErrorType.FORBIDDEN
                404 -> ErrorType.NOT_FOUND
                in 500..599 -> ErrorType.SERVER_ERROR
                else -> ErrorType.UNKNOWN
            }

            is InvalidStatusException -> {
                val statusCode = extractStatusCode(e)
                when (statusCode) {
                    401 -> ErrorType.UNAUTHORIZED
                    403 -> ErrorType.FORBIDDEN
                    404 -> ErrorType.NOT_FOUND
                    in 500..599 -> ErrorType.SERVER_ERROR
                    else -> {
                        // Fallback: check message content for 401
                        if (e.message?.contains("401") == true) {
                            ErrorType.UNAUTHORIZED
                        } else {
                            ErrorType.UNKNOWN
                        }
                    }
                }
            }

            else -> ErrorType.UNKNOWN
        }
    }

    /**
     * Validates server state and throws descriptive exceptions
     */
    fun validateServer(server: JellyfinServer?): JellyfinServer {
        if (server == null) {
            Log.w(TAG, "validateServer: Server is null - user may not be authenticated or connection was lost")
            throw IllegalStateException("Server is not available. Please check your connection and try logging in again.")
        }

        if (server.accessToken == null) {
            Log.w(TAG, "validateServer: Server has no access token - authentication may have expired")
            throw IllegalStateException("Authentication token is missing. Please log in again.")
        }

        if (server.userId == null) {
            Log.w(TAG, "validateServer: Server has no user ID - authentication may be incomplete")
            throw IllegalStateException("User authentication is incomplete. Please log in again.")
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "validateServer: Server validation passed for user ${server.username} on ${server.url}")
        }

        return server
    }

    /**
     * Safely parses UUID with descriptive error messages
     */
    fun parseUuid(id: String, idType: String): UUID {
        return runCatching { UUID.fromString(id) }.getOrElse {
            throw IllegalArgumentException("Invalid $idType ID: $id")
        }
    }

    /**
     * Checks if exception should be retried based on type
     */
    fun isRetryableException(e: Exception): Boolean {
        return when (getErrorType(e)) {
            ErrorType.NETWORK -> true
            ErrorType.SERVER_ERROR -> true
            ErrorType.UNAUTHORIZED -> true // Allow retry for token refresh
            else -> false
        }
    }

    /**
     * Determines if exception is a 401 authentication error
     */
    fun is401Error(e: Exception): Boolean {
        return (e is HttpException && e.code() == 401) ||
            (e is InvalidStatusException && e.message?.contains("401") == true)
    }
}
