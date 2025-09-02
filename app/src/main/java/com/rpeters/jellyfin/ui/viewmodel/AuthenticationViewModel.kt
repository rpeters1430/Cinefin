package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AuthenticationState(
    val isAuthenticated: Boolean = false,
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Dedicated ViewModel for authentication operations.
 * Extracted from MainAppViewModel to reduce complexity and prevent merge conflicts.
 */
@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val userRepository: JellyfinUserRepository,
    private val credentialManager: SecureCredentialManager,
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthenticationState())
    val authState: StateFlow<AuthenticationState> = _authState.asStateFlow()

    val currentServer = authRepository.currentServer
    val isConnected = authRepository.isConnected

    init {
        // Monitor authentication state
        viewModelScope.launch {
            authRepository.isAuthenticating.collect { isAuthenticating ->
                _authState.value = _authState.value.copy(
                    isAuthenticating = isAuthenticating,
                    isAuthenticated = authRepository.isUserAuthenticated(),
                )
            }
        }
    }

    /**
     * Enhanced token validation that waits for authentication to complete.
     * This prevents race conditions during app startup with remembered credentials.
     */
    suspend fun ensureValidTokenWithWait(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // First check if we have a valid token
                if (!authRepository.isTokenExpired()) {
                    if (BuildConfig.DEBUG) {
                        SecureLogger.d("AuthenticationViewModel", "ensureValidTokenWithWait: Token is valid, proceeding")
                    }
                    return@withContext true
                }

                if (BuildConfig.DEBUG) {
                    SecureLogger.d("AuthenticationViewModel", "ensureValidTokenWithWait: Token expired, attempting re-authentication")
                }

                // Wait for authentication to complete
                val authSuccess = authRepository.reAuthenticate()

                if (authSuccess) {
                    if (BuildConfig.DEBUG) {
                        SecureLogger.d("AuthenticationViewModel", "ensureValidTokenWithWait: Re-authentication successful")
                    }
                    // Additional verification that the token is now valid
                    return@withContext !authRepository.isTokenExpired()
                } else {
                    if (BuildConfig.DEBUG) {
                        SecureLogger.w("AuthenticationViewModel", "ensureValidTokenWithWait: Re-authentication failed")
                    }
                    return@withContext false
                }
            } catch (e: Exception) {
                SecureLogger.e("AuthenticationViewModel", "ensureValidTokenWithWait: Exception during authentication", e)
                return@withContext false
            }
        }
    }

    /**
     * Manually refresh authentication token if expired
     */
    fun refreshAuthentication() {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("AuthenticationViewModel", "Manual authentication refresh requested")
            }

            _authState.value = _authState.value.copy(isAuthenticating = true, errorMessage = null)

            try {
                val success = authRepository.forceReAuthenticate()

                _authState.value = _authState.value.copy(
                    isAuthenticating = false,
                    isAuthenticated = success,
                    errorMessage = if (!success) "Failed to refresh authentication" else null,
                )

                if (BuildConfig.DEBUG) {
                    SecureLogger.d("AuthenticationViewModel", "Authentication refresh completed: $success")
                }
            } catch (e: Exception) {
                SecureLogger.e("AuthenticationViewModel", "Authentication refresh failed", e)
                _authState.value = _authState.value.copy(
                    isAuthenticating = false,
                    isAuthenticated = false,
                    errorMessage = "Failed to refresh authentication: ${e.message}",
                )
            }
        }
    }

    /**
     * Logout user and clear credentials
     */
    fun logout() {
        viewModelScope.launch {
            try {
                userRepository.logout()
                credentialManager.clearCredentials()

                _authState.value = _authState.value.copy(
                    isAuthenticated = false,
                    isAuthenticating = false,
                    errorMessage = null,
                )

                if (BuildConfig.DEBUG) {
                    SecureLogger.d("AuthenticationViewModel", "User logged out successfully")
                }
            } catch (e: Exception) {
                SecureLogger.e("AuthenticationViewModel", "Error during logout", e)
                _authState.value = _authState.value.copy(
                    errorMessage = "Logout failed: ${e.message}",
                )
            }
        }
    }

    /**
     * Clear any authentication error messages
     */
    fun clearError() {
        _authState.value = _authState.value.copy(errorMessage = null)
    }

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return authRepository.isUserAuthenticated()
    }

    /**
     * Check if token is expired
     */
    fun isTokenExpired(): Boolean {
        return authRepository.isTokenExpired()
    }
}
