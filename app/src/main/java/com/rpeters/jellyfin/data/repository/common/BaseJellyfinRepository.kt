package com.rpeters.jellyfin.data.repository.common

import com.rpeters.jellyfin.core.LogCategory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.session.JellyfinSessionManager
import com.rpeters.jellyfin.data.utils.RepositoryUtils
import com.rpeters.jellyfin.ui.utils.RetryManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * Lightweight base class shared by repository implementations.
 * It centralizes common helpers like client creation and safe
 * execution wrappers so individual repositories can stay focused
 * on their domain logic.
 */
open class BaseJellyfinRepository @Inject constructor(
    protected val authRepository: JellyfinAuthRepository,
    protected val sessionManager: JellyfinSessionManager,
    protected val cache: JellyfinCache,
) {
    // Mutex to prevent race conditions in token refresh
    private val tokenRefreshMutex = Mutex()

    /**
     * Get Jellyfin API client on background thread to avoid StrictMode violations.
     * Client creation involves static initialization that performs file I/O.
     */
    protected suspend fun getClient(serverUrl: String, accessToken: String?): ApiClient =
        sessionManager.getClientForUrl(serverUrl)

    fun validateServer(): JellyfinServer {
        val currentServer = authRepository.getCurrentServer()
        return RepositoryUtils.validateServer(currentServer)
    }

    protected fun parseUuid(id: String, type: String) =
        RepositoryUtils.parseUuid(id, type)

    /**
     * Checks if token needs refresh and proactively refreshes it
     */
    protected suspend fun validateTokenAndRefreshIfNeeded() {
        if (authRepository.isTokenExpired()) {
            tokenRefreshMutex.withLock {
                // Double-check after acquiring lock (another thread might have refreshed)
                if (authRepository.isTokenExpired()) {
                    Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Token expired, proactively refreshing with force")

                    val refreshResult = authRepository.forceReAuthenticate()
                    if (refreshResult) {
                        Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Proactive force token refresh successful")
                        sessionManager.invalidateClients()
                    } else {
                        Logger.w(LogCategory.NETWORK, javaClass.simpleName, "Proactive force token refresh failed")
                        // Don't throw here - let the subsequent API call handle the 401
                    }
                }
            }
        }
    }

    /**
     * Execute operation with 401-aware authentication handling.
     * This eliminates stale token issues and properly handles token refresh for 401 errors.
     */
    protected suspend fun <T> executeWithClient(
        operationName: String,
        operation: suspend (ApiClient) -> T,
    ): T {
        return executeWithTokenRefresh {
            // Delegate to session manager for token/401 handling
            sessionManager.executeWithAuth(operationName) { _, client ->
                operation(client)
            }
        }
    }

    /**
     * Execute operation with 401-aware authentication handling.
     * Implements single-flight reauth with mutex to prevent concurrent authentication attempts.
     */
    protected suspend fun <T> executeWithTokenRefresh(
        operation: suspend () -> T,
    ): T {
        // Proactively check and refresh token if expired
        validateTokenAndRefreshIfNeeded()

        return try {
            operation()
        } catch (e: Exception) {
            if (RepositoryUtils.is401Error(e)) {
                return tokenRefreshMutex.withLock {
                    Logger.d(LogCategory.NETWORK, javaClass.simpleName, "HTTP 401 detected, attempting force token refresh")

                    // Double-check if another thread already refreshed the token
                    val currentServer = authRepository.getCurrentServer()
                    if (currentServer?.accessToken != null && !authRepository.isTokenExpired()) {
                        Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Token already refreshed by another thread, retrying operation")
                        sessionManager.invalidateClients()
                        // Add small delay to ensure client cache is properly cleared
                        kotlinx.coroutines.delay(50)
                        return@withLock operation()
                    }

                    // Check if authentication is already in progress to prevent concurrent attempts
                    if (authRepository.isAuthenticating.first()) {
                        Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Authentication already in progress, waiting for completion")
                        // Wait for authentication to complete, with timeout
                        val maxWaitMs = 10000L // 10 seconds timeout
                        val pollIntervalMs = 100L
                        var waitedMs = 0L
                        while (authRepository.isAuthenticating.first() && waitedMs < maxWaitMs) {
                            kotlinx.coroutines.delay(pollIntervalMs)
                            waitedMs += pollIntervalMs
                        }

                        // Authentication completed, retry operation
                        Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Authentication completed by another thread, retrying operation")
                        sessionManager.invalidateClients()
                        // Add small delay to ensure client cache is properly cleared
                        kotlinx.coroutines.delay(50)
                        return@withLock operation()
                    }

                    val refreshResult = authRepository.forceReAuthenticate()
                    if (refreshResult) {
                        Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Force token refresh successful, retrying operation")
                        // Invalidate clients to ensure new token is used
                        sessionManager.invalidateClients()

                        // Retry the operation with refreshed token
                        // Note: The operation closure should re-fetch server state for updated tokens
                        operation()
                    } else {
                        Logger.e(LogCategory.NETWORK, javaClass.simpleName, "Force token refresh failed")
                        throw Exception("Authentication failed: Unable to refresh token")
                    }
                }
            } else {
                throw e
            }
        }
    }

    /**
     * ✅ NEW: Wraps a client-based operation returning [ApiResult].
     * Uses the new TokenProvider approach to eliminate stale token issues.
     */
    protected suspend fun <T> execute(
        operationName: String,
        block: suspend (ApiClient) -> T,
    ): ApiResult<T> =
        try {
            val result = executeWithClient(operationName, block)
            ApiResult.Success(result)
        } catch (e: Exception) {
            Logger.e(
                LogCategory.NETWORK,
                javaClass.simpleName,
                "Error executing $operationName",
                e,
            )
            val error = RepositoryUtils.getErrorType(e)
            ApiResult.Error(e.message ?: "Unknown error", e, error)
        }

    /**
     * Helper method to make the server/client pattern easier to use safely.
     * This ensures fresh server state and client are created inside the execution block.
     *
     * Usage example:
     * ```kotlin
     * return withServerClient("getLibraryItems") { server, client ->
     *     val response = client.itemsApi.getItems(
     *         userId = server.userId,
     *         // ...
     *     )
     *     response.content.items ?: emptyList()
     * }
     * ```
     */
    protected suspend inline fun <T> withServerClient(
        operationName: String,
        crossinline block: suspend (server: JellyfinServer, client: ApiClient) -> T,
    ): ApiResult<T> = execute(operationName) { client ->
        val server = validateServer()
        block(server, client)
    }

    /**
     * Legacy method for backward compatibility during transition.
     * Wraps a suspend block returning [ApiResult]. Any thrown exception
     * is converted to an [ApiResult.Error] with a best-effort error type.
     */
    protected suspend fun <T> executeLegacy(
        operationName: String,
        block: suspend () -> T,
    ): ApiResult<T> =
        try {
            // ensure proactive check + 401-aware retry
            val result = executeWithTokenRefresh { block() }
            ApiResult.Success(result)
        } catch (e: Exception) {
            Logger.e(
                LogCategory.NETWORK,
                javaClass.simpleName,
                "Error executing $operationName",
                e,
            )
            val error = RepositoryUtils.getErrorType(e)
            ApiResult.Error(e.message ?: "Unknown error", e, error)
        }

    /**
     * Executes a block with automatic retry logic and error handling.
     * This provides smart retry behavior for all repository operations.
     */
    protected suspend fun <T> executeWithRetry(
        operationName: String,
        maxAttempts: Int = 3,
        block: suspend () -> T,
    ): ApiResult<T> {
        return RetryManager.withRetry(maxAttempts, operationName) { attempt ->
            try {
                val result = executeWithTokenRefresh { block() }
                ApiResult.Success(result)
            } catch (e: Exception) {
                Logger.e(
                    LogCategory.NETWORK,
                    javaClass.simpleName,
                    "Error executing $operationName on attempt $attempt",
                    e,
                )
                val error = RepositoryUtils.getErrorType(e)
                ApiResult.Error(e.message ?: "Unknown error", e, error)
            }
        }
    }

    /**
     * Executes a block with both retry logic and circuit breaker protection.
     * Use this for critical operations that should be protected from cascading failures.
     */
    protected suspend fun <T> executeWithRetryAndCircuitBreaker(
        operationName: String,
        maxAttempts: Int = 3,
        circuitBreakerKey: String = operationName,
        block: suspend () -> T,
    ): ApiResult<T> {
        return RetryManager.withRetryAndCircuitBreaker(maxAttempts, operationName, circuitBreakerKey) { attempt ->
            try {
                val result = executeWithTokenRefresh { block() }
                ApiResult.Success(result)
            } catch (e: Exception) {
                Logger.e(
                    LogCategory.NETWORK,
                    javaClass.simpleName,
                    "Error executing $operationName on attempt $attempt",
                    e,
                )
                val error = RepositoryUtils.getErrorType(e)
                ApiResult.Error(e.message ?: "Unknown error", e, error)
            }
        }
    }

    /**
     * Executes an operation with cache-first strategy and automatic fallback.
     * First checks cache, then tries network with retry, and caches successful results.
     */
    protected suspend fun executeWithCache(
        operationName: String,
        cacheKey: String,
        maxAttempts: Int = 3,
        cacheTtlMs: Long = 30 * 60 * 1000L, // 30 minutes default
        block: suspend () -> List<BaseItemDto>,
    ): ApiResult<List<BaseItemDto>> {
        // Try cache first
        val cachedData = cache.getCachedItems(cacheKey)
        if (cachedData != null) {
            return ApiResult.Success(cachedData)
        }

        // Cache miss, try network with retry
        return executeWithRetryAndCircuitBreaker(operationName, maxAttempts, cacheKey) {
            val result = block()
            // Cache successful results
            cache.cacheItems(cacheKey, result, cacheTtlMs)
            result
        }
    }

    /**
     * Force refresh data and update cache.
     */
    protected suspend fun executeRefreshWithCache(
        operationName: String,
        cacheKey: String,
        maxAttempts: Int = 3,
        cacheTtlMs: Long = 30 * 60 * 1000L,
        block: suspend () -> List<BaseItemDto>,
    ): ApiResult<List<BaseItemDto>> {
        // Invalidate existing cache
        cache.invalidateCache(cacheKey)

        // Execute with retry and cache result
        return executeWithRetryAndCircuitBreaker(operationName, maxAttempts, cacheKey) {
            val result = block()
            // Cache the fresh results
            cache.cacheItems(cacheKey, result, cacheTtlMs)
            result
        }
    }
}
