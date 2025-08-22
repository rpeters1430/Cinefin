package com.rpeters.jellyfin.ui.viewmodel.common

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.utils.ErrorHandler
import com.rpeters.jellyfin.ui.utils.ProcessedError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Enhanced base ViewModel that provides common functionality for error handling,
 * loading states, and retry mechanisms across all Jellyfin ViewModels.
 *
 * This is part of Phase 2: Enhanced Error Handling & User Experience improvements.
 */
abstract class BaseJellyfinViewModel : ViewModel() {

    companion object {
        private const val TAG = "BaseJellyfinViewModel"
    }

    // Common UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorState = MutableStateFlow<ProcessedError?>(null)
    val errorState: StateFlow<ProcessedError?> = _errorState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Retry tracking
    private val retryOperations = mutableMapOf<String, suspend () -> Unit>()

    /**
     * Executes an operation with automatic loading state management and error handling.
     *
     * @param operationName Unique name for this operation (for retry tracking)
     * @param showLoading Whether to show loading state during operation
     * @param operation The operation to execute
     */
    protected fun <T> executeOperation(
        operationName: String,
        showLoading: Boolean = true,
        operation: suspend () -> ApiResult<T>,
        onSuccess: (T) -> Unit = {},
        onError: (ProcessedError) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                if (showLoading) {
                    _isLoading.value = true
                }
                _errorState.value = null

                // Store for potential retry
                retryOperations[operationName] = {
                    executeOperation(operationName, showLoading, operation, onSuccess, onError)
                }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Starting operation: $operationName")
                }

                when (val result = operation()) {
                    is ApiResult.Success -> {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Operation succeeded: $operationName")
                        }
                        onSuccess(result.data)
                        retryOperations.remove(operationName) // Clear successful operations
                    }

                    is ApiResult.Error -> {
                        val processedError = ErrorHandler.processError(
                            result.cause ?: Exception(result.message),
                            operation = operationName,
                        )

                        Log.w(TAG, "Operation failed: $operationName - ${processedError.userMessage}")

                        _errorState.value = processedError
                        onError(processedError)

                        // Log error analytics
                        ErrorHandler.logErrorAnalytics(
                            error = processedError,
                            operation = operationName,
                        )
                    }

                    is ApiResult.Loading -> {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Operation loading: $operationName")
                        }
                        // Loading state already handled above
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Exception in operation: $operationName", e)
                }

                val processedError = ErrorHandler.processError(e, operation = operationName)
                _errorState.value = processedError
                onError(processedError)
            } finally {
                if (showLoading) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Executes a refresh operation with special refresh state management.
     */
    protected fun <T> executeRefresh(
        operationName: String,
        operation: suspend () -> ApiResult<T>,
        onSuccess: (T) -> Unit = {},
        onError: (ProcessedError) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _errorState.value = null

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Starting refresh: $operationName")
                }

                when (val result = operation()) {
                    is ApiResult.Success -> {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Refresh succeeded: $operationName")
                        }
                        onSuccess(result.data)
                    }

                    is ApiResult.Error -> {
                        val processedError = ErrorHandler.processError(
                            result.cause ?: Exception(result.message),
                            operation = operationName,
                        )

                        Log.w(TAG, "Refresh failed: $operationName - ${processedError.userMessage}")
                        onError(processedError)
                    }

                    is ApiResult.Loading -> {
                        // Refresh state already handled
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Exception in refresh: $operationName", e)
                }

                val processedError = ErrorHandler.processError(e, operation = operationName)
                onError(processedError)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Retries the last failed operation for the given operation name.
     */
    fun retryOperation(operationName: String) {
        val operation = retryOperations[operationName]
        if (operation != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Retrying operation: $operationName")
            }
            viewModelScope.launch {
                operation()
            }
        } else {
            Log.w(TAG, "No retry operation found for: $operationName")
        }
    }

    /**
     * Retries the last failed operation (if any).
     */
    fun retryLastOperation() {
        val lastOperation = retryOperations.values.lastOrNull()
        if (lastOperation != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Retrying last operation")
            }
            viewModelScope.launch {
                lastOperation()
            }
        }
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _errorState.value = null
    }

    /**
     * Determines if the current error is retryable.
     */
    fun isCurrentErrorRetryable(): Boolean {
        return _errorState.value?.isRetryable == true
    }

    /**
     * Gets the suggested action for the current error.
     */
    fun getCurrentErrorSuggestedAction(): String? {
        return _errorState.value?.suggestedAction
    }

    /**
     * Extension function to easily handle common loading and error patterns.
     */
    protected suspend fun <T> ApiResult<T>.handleResult(
        onSuccess: (T) -> Unit,
        onError: (ProcessedError) -> Unit = { _errorState.value = it },
    ) {
        when (this) {
            is ApiResult.Success -> onSuccess(data)
            is ApiResult.Error -> {
                val processedError = ErrorHandler.processError(
                    cause ?: Exception(message),
                    operation = "API Call",
                )
                onError(processedError)
            }
            is ApiResult.Loading -> {
                // Loading handled by calling code
            }
        }
    }
}

/**
 * Extension functions for common ViewModel patterns.
 */

/**
 * Safely executes a suspend function with error handling.
 */
suspend fun <T> safeCall(
    operation: suspend () -> T,
    onError: (Exception) -> Unit = {},
): T? {
    return try {
        operation()
    } catch (e: Exception) {
        onError(e)
        null
    }
}

/**
 * Executes multiple operations in parallel and collects results.
 */
suspend fun <T> executeParallel(
    operations: List<suspend () -> ApiResult<T>>,
): List<ApiResult<T>> {
    return coroutineScope {
        operations.map { operation ->
            async { operation() }
        }.map { it.await() }
    }
}
