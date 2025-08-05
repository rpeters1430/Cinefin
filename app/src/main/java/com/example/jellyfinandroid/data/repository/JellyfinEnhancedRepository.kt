package com.example.jellyfinandroid.data.repository

import android.util.Log
import com.example.jellyfinandroid.BuildConfig
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.data.SecureCredentialManager
import com.example.jellyfinandroid.data.model.ApiResult
import com.example.jellyfinandroid.data.model.JellyfinError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ PHASE 3: Enhanced Main Repository
 * Coordinates between specialized repositories and manages global state
 * This is the main entry point for the app's data operations
 */
@Singleton
class JellyfinEnhancedRepository @Inject constructor(
    private val systemRepository: JellyfinSystemRepository,
    private val credentialManager: SecureCredentialManager,
) {
    companion object {
        private const val TAG = "JellyfinRepository"
    }

    // Global app state
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: Flow<JellyfinServer?> = _currentServer.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: Flow<Boolean> = _isConnected.asStateFlow()

    private val _currentUser = MutableStateFlow<UUID?>(null)
    val currentUser: Flow<UUID?> = _currentUser.asStateFlow()

    // Authentication mutex to prevent race conditions
    private val authMutex = Mutex()

    /**
     * ✅ PHASE 3: Enhanced server connection with validation
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        val normalizedUrl = systemRepository.normalizeServerUrl(serverUrl)

        if (!systemRepository.validateServerUrl(normalizedUrl)) {
            return ApiResult.Error("Invalid server URL format")
        }

        return systemRepository.testServerConnection(normalizedUrl)
    }

    /**
     * ✅ PHASE 3: Enhanced authentication with automatic credential saving
     */
    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
        saveCredentials: Boolean = false,
    ): ApiResult<AuthenticationResult> = authMutex.withLock {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Authenticating user: $username")
        }

        val normalizedUrl = systemRepository.normalizeServerUrl(serverUrl)

        return try {
            // For now, we'll implement a basic version that will be enhanced
            // when we add back the auth repository

            if (saveCredentials) {
                credentialManager.savePassword(normalizedUrl, username, password)
            }
            // Update connection state
            _currentServer.value = JellyfinServer(
                url = normalizedUrl,
                name = "Jellyfin Server", // Will be updated with real server name
                version = "Unknown",
                id = "", // Add empty id parameter
            )
            _isConnected.value = true

            // This is a placeholder - will be replaced with real auth
            ApiResult.Error("Authentication implementation pending modular refactor")
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            _isConnected.value = false
            _currentServer.value = null

            val error = when {
                e.message?.contains("network", ignoreCase = true) == true -> JellyfinError.NetworkError
                e.message?.contains("unauthorized", ignoreCase = true) == true -> JellyfinError.AuthenticationError
                else -> JellyfinError.UnknownError(e.message ?: "Authentication failed", e)
            }

            error.toApiResult()
        }
    }

    /**
     * ✅ PHASE 3: Enhanced credential management
     */
    suspend fun getSavedCredentials(serverUrl: String, username: String): String? {
        val normalizedUrl = systemRepository.normalizeServerUrl(serverUrl)
        return credentialManager.getPassword(normalizedUrl, username)
    }

    suspend fun clearSavedCredentials(serverUrl: String, username: String) {
        val normalizedUrl = systemRepository.normalizeServerUrl(serverUrl)
        credentialManager.clearPassword(normalizedUrl, username)
    }

    suspend fun clearAllCredentials() {
        credentialManager.clearAllPasswords()
    }

    /**
     * ✅ PHASE 3: Session management
     */
    suspend fun logout() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Logging out user")
        }

        _isConnected.value = false
        _currentServer.value = null
        _currentUser.value = null
    }

    /**
     * ✅ PHASE 3: Connection state management
     */
    fun isServerConnected(): Boolean = _isConnected.value

    fun getCurrentServerUrl(): String? = _currentServer.value?.url

    /**
     * ✅ PHASE 3: Auto-reconnection attempt with saved credentials
     */
    suspend fun attemptAutoReconnection(): ApiResult<Boolean> {
        val server = _currentServer.first() ?: return ApiResult.Success(false)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Attempting auto-reconnection to ${server.url}")
        }

        return when (val result = testServerConnection(server.url)) {
            is ApiResult.Success -> {
                _isConnected.value = true
                ApiResult.Success(true)
            }
            is ApiResult.Error -> {
                Log.w(TAG, "Auto-reconnection failed: ${result.message}")
                _isConnected.value = false
                ApiResult.Success(false)
            }
            is ApiResult.Loading -> ApiResult.Success(false)
        }
    }

    /**
     * ✅ PHASE 3: Health check for current connection
     */
    suspend fun performHealthCheck(): ApiResult<Boolean> {
        val serverUrl = getCurrentServerUrl() ?: return ApiResult.Error("No server connected")

        return when (val result = testServerConnection(serverUrl)) {
            is ApiResult.Success -> {
                _isConnected.value = true
                ApiResult.Success(true)
            }
            is ApiResult.Error -> {
                Log.w(TAG, "Health check failed: ${result.message}")
                _isConnected.value = false
                ApiResult.Error("Server connection lost: ${result.message}")
            }
            is ApiResult.Loading -> ApiResult.Success(true) // Assume healthy during loading
        }
    }

    /**
     * ✅ PHASE 3: Placeholder methods for media operations
     * These will be implemented when we add back the media repository
     */
    suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> {
        if (!isServerConnected()) {
            return ApiResult.Error("Not connected to server")
        }

        // Placeholder - will be replaced with media repository call
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getUserLibraries - implementation pending")
        }
        return ApiResult.Error("Media repository implementation pending")
    }

    suspend fun getLibraryItems(
        parentId: UUID? = null,
        itemTypes: List<BaseItemKind> = emptyList(),
        limit: Int? = null,
    ): ApiResult<List<BaseItemDto>> {
        if (!isServerConnected()) {
            return ApiResult.Error("Not connected to server")
        }

        // Placeholder - will be replaced with media repository call
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getLibraryItems - implementation pending")
        }
        return ApiResult.Error("Media repository implementation pending")
    }

    suspend fun getRecentlyAdded(limit: Int = 20): ApiResult<List<BaseItemDto>> {
        if (!isServerConnected()) {
            return ApiResult.Error("Not connected to server")
        }

        // Placeholder - will be replaced with media repository call
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "getRecentlyAdded - implementation pending")
        }
        return ApiResult.Error("Media repository implementation pending")
    }
}
