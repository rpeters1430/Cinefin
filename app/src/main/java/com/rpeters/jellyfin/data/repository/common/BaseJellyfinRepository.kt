package com.rpeters.jellyfin.data.repository.common

import com.rpeters.jellyfin.core.LogCategory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.utils.RepositoryUtils
import com.rpeters.jellyfin.di.JellyfinClientFactory
import com.rpeters.jellyfin.ui.utils.RetryManager
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
    private val clientFactory: JellyfinClientFactory,
    protected val cache: JellyfinCache,
) {
    /**
     * Get Jellyfin API client on background thread to avoid StrictMode violations.
     * Client creation involves static initialization that performs file I/O.
     */
    protected suspend fun getClient(serverUrl: String, accessToken: String?): ApiClient =
        clientFactory.getClient(serverUrl, accessToken)

    protected fun validateServer(): JellyfinServer {
        val currentServer = authRepository.getCurrentServer()
        return RepositoryUtils.validateServer(currentServer)
    }

    protected fun parseUuid(id: String, type: String) =
        RepositoryUtils.parseUuid(id, type)

    /**
     * Wraps a suspend block returning [ApiResult]. Any thrown exception
     * is converted to an [ApiResult.Error] with a best-effort error type.
     */
    protected suspend fun <T> execute(
        operationName: String,
        block: suspend () -> T,
    ): ApiResult<T> =
        try {
            ApiResult.Success(block())
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
                ApiResult.Success(block())
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
                ApiResult.Success(block())
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
