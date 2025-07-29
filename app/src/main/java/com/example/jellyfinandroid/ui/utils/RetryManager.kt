package com.example.jellyfinandroid.ui.utils

import com.example.jellyfinandroid.BuildConfig
import android.util.Log
import com.example.jellyfinandroid.data.repository.ApiResult
import com.example.jellyfinandroid.data.repository.ErrorType
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * Utility for handling automatic retries with exponential backoff and intelligent retry logic.
 * 
 * Provides sophisticated retry mechanisms for network operations, API calls,
 * and other operations that may fail due to transient issues.
 */
object RetryManager {
    
    private const val TAG = "RetryManager"
    
    /**
     * Executes an operation with automatic retry logic.
     * 
     * @param operation The suspended function to execute
     * @param maxAttempts Maximum number of attempts (including initial attempt)
     * @param operationName Name of the operation for logging
     * @return The result of the operation
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        operationName: String = "Operation",
        operation: suspend (attempt: Int) -> ApiResult<T>
    ): ApiResult<T> {
        
        var lastResult: ApiResult<T>? = null
        
        for (attempt in 1..maxAttempts) {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "$operationName: Attempt $attempt/$maxAttempts")
                }
                
                val result = operation(attempt)
                
                when (result) {
                    is ApiResult.Success -> {
                        if (attempt > 1) {
                            if (BuildConfig.DEBUG) {
                                Log.i(TAG, "$operationName: Succeeded on attempt $attempt")
                            }
                        }
                        return result
                    }
                    
                    is ApiResult.Error -> {
                        lastResult = result
                        
                        if (attempt == maxAttempts) {
                            Log.w(TAG, "$operationName: Failed after $maxAttempts attempts")
                            return result
                        }
                        
                        if (!ErrorHandler.shouldRetry(result.errorType, attempt, maxAttempts)) {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "$operationName: Error type ${result.errorType} is not retryable")
                            }
                            return result
                        }
                        
                        val delay = ErrorHandler.getRetryDelay(result.errorType, attempt)
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "$operationName: Attempt $attempt failed (${result.errorType}), retrying in ${delay}ms")
                        }
                        delay(delay)
                    }
                    
                    is ApiResult.Loading -> {
                        // This shouldn't happen in our retry context, but handle it gracefully
                        Log.w(TAG, "$operationName: Received Loading result on attempt $attempt")
                        if (attempt == maxAttempts) {
                            return ApiResult.Error("Operation did not complete", errorType = ErrorType.UNKNOWN)
                        }
                        delay(1000) // Wait a bit before retrying
                    }
                }
                
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "$operationName: Operation was cancelled on attempt $attempt")
                }
                throw e // Always rethrow cancellation exceptions
                
            } catch (e: Exception) {
                Log.e(TAG, "$operationName: Unexpected exception on attempt $attempt", e)
                
                if (attempt == maxAttempts) {
                    return ApiResult.Error(
                        "Failed after $maxAttempts attempts: ${e.message}",
                        e,
                        ErrorType.UNKNOWN
                    )
                }
                
                val delay = ErrorHandler.getRetryDelay(ErrorType.UNKNOWN, attempt)
                delay(delay)
            }
        }
        
        // This should never be reached, but provide a fallback
        return lastResult ?: ApiResult.Error("Retry logic failed", errorType = ErrorType.UNKNOWN)
    }
    
    /**
     * Executes an operation with circuit breaker pattern.
     * Temporarily stops retrying if too many failures occur in a short time.
     * 
     * @param operation The operation to execute
     * @param operationName Name for logging
     * @param circuitBreakerKey Unique key for this operation's circuit breaker
     * @return The result of the operation
     */
    suspend fun <T> withCircuitBreaker(
        operationName: String = "Operation",
        circuitBreakerKey: String = "default",
        operation: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        
        val circuitState = CircuitBreakerState.getState(circuitBreakerKey)
        
        when (circuitState.state) {
            CircuitState.OPEN -> {
                if (circuitState.shouldAttemptReset()) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "$operationName: Circuit breaker attempting reset")
                    }
                    circuitState.state = CircuitState.HALF_OPEN
                } else {
                    Log.w(TAG, "$operationName: Circuit breaker is OPEN, rejecting request")
                    return ApiResult.Error(
                        "Service temporarily unavailable. Please try again later.",
                        errorType = ErrorType.SERVER_ERROR
                    )
                }
            }
            
            CircuitState.HALF_OPEN -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "$operationName: Circuit breaker is HALF_OPEN, testing service")
                }
            }
            
            CircuitState.CLOSED -> {
                // Normal operation
            }
        }
        
        return try {
            val result = operation()
            
            when (result) {
                is ApiResult.Success -> {
                    circuitState.recordSuccess()
                    result
                }
                
                is ApiResult.Error -> {
                    circuitState.recordFailure()
                    result
                }
                
                is ApiResult.Loading -> result
            }
            
        } catch (e: Exception) {
            circuitState.recordFailure()
            throw e
        }
    }
    
    /**
     * Combines retry logic with circuit breaker for robust error handling.
     * 
     * @param operation The operation to execute
     * @param maxAttempts Maximum retry attempts
     * @param operationName Name for logging
     * @param circuitBreakerKey Unique key for circuit breaker
     * @return The result of the operation
     */
    suspend fun <T> withRetryAndCircuitBreaker(
        maxAttempts: Int = 3,
        operationName: String = "Operation",
        circuitBreakerKey: String = "default",
        operation: suspend (attempt: Int) -> ApiResult<T>
    ): ApiResult<T> {
        
        return withCircuitBreaker(operationName, circuitBreakerKey) {
            withRetry(maxAttempts, operationName, operation)
        }
    }
}

/**
 * Circuit breaker state management for preventing cascading failures.
 */
private object CircuitBreakerState {
    private val states = mutableMapOf<String, CircuitBreakerInfo>()
    
    fun getState(key: String): CircuitBreakerInfo {
        return states.getOrPut(key) { CircuitBreakerInfo() }
    }
}

private data class CircuitBreakerInfo(
    var state: CircuitState = CircuitState.CLOSED,
    var failureCount: Int = 0,
    var lastFailureTime: Long = 0,
    var lastSuccessTime: Long = System.currentTimeMillis()
) {
    companion object {
        private const val FAILURE_THRESHOLD = 5
        private const val TIMEOUT_MS = 60_000L // 1 minute
        private const val HALF_OPEN_MAX_CALLS = 3
    }
    
    private var halfOpenCalls = 0
    
    fun recordSuccess() {
        lastSuccessTime = System.currentTimeMillis()
        when (state) {
            CircuitState.HALF_OPEN -> {
                state = CircuitState.CLOSED
                failureCount = 0
                halfOpenCalls = 0
            }
            CircuitState.CLOSED -> {
                failureCount = 0
            }
            CircuitState.OPEN -> {
                // Success while open, reset to closed
                state = CircuitState.CLOSED
                failureCount = 0
            }
        }
    }
    
    fun recordFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        when (state) {
            CircuitState.CLOSED -> {
                if (failureCount >= FAILURE_THRESHOLD) {
                    state = CircuitState.OPEN
                    Log.w("CircuitBreaker", "Circuit breaker opened due to $failureCount failures")
                }
            }
            CircuitState.HALF_OPEN -> {
                state = CircuitState.OPEN
                Log.w("CircuitBreaker", "Circuit breaker reopened due to failure during half-open")
            }
            CircuitState.OPEN -> {
                // Already open, just update failure time
            }
        }
    }
    
    fun shouldAttemptReset(): Boolean {
        return state == CircuitState.OPEN && 
               (System.currentTimeMillis() - lastFailureTime) > TIMEOUT_MS
    }
}

private enum class CircuitState {
    CLOSED,   // Normal operation
    OPEN,     // Failing, reject requests
    HALF_OPEN // Testing if service has recovered
}

/**
 * Extension functions for common retry patterns.
 */

/**
 * Retry an API call with standard retry logic.
 */
suspend fun <T> retryApiCall(
    operationName: String,
    maxAttempts: Int = 3,
    operation: suspend () -> ApiResult<T>
): ApiResult<T> {
    return RetryManager.withRetry(maxAttempts, operationName) { operation() }
}

/**
 * Retry a network operation with circuit breaker protection.
 */
suspend fun <T> retryNetworkCall(
    operationName: String,
    maxAttempts: Int = 3,
    operation: suspend () -> ApiResult<T>
): ApiResult<T> {
    return RetryManager.withRetryAndCircuitBreaker(maxAttempts, operationName) { operation() }
}