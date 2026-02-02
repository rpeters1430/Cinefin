package com.rpeters.jellyfin.data.repository.common

/**
 * Represents a simple result wrapper for repository operations.
 * Moved to common package so multiple repositories can share the same type.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(
        val message: String,
        val cause: Throwable? = null,
        val errorType: ErrorType = ErrorType.UNKNOWN,
    ) : ApiResult<T>()
    class Loading<T>(val message: String = "") : ApiResult<T>()
}

/**
 * Basic error categories that repository calls may surface.
 * Kept minimal for test friendliness.
 */
enum class ErrorType {
    NETWORK,
    DNS_RESOLUTION, // Hostname resolution failures (EAI_NODATA, EAI_NONAME, etc.)
    AUTHENTICATION,
    SERVER_ERROR,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    BAD_REQUEST,
    OPERATION_CANCELLED,
    TIMEOUT,
    VALIDATION,
    PINNING,
    UNKNOWN,
}
