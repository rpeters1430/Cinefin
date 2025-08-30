package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.model.QuickConnectResult
import com.rpeters.jellyfin.data.model.QuickConnectState
import com.rpeters.jellyfin.data.network.TokenProvider
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.utils.RepositoryUtils
import com.rpeters.jellyfin.di.JellyfinClientFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.PublicSystemInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinAuthRepository @Inject constructor(
    private val clientFactory: JellyfinClientFactory,
    private val secureCredentialManager: SecureCredentialManager,
) : TokenProvider {
    private val authMutex = Mutex()
    
    // Token state for TokenProvider implementation
    private val _tokenState = MutableStateFlow<String?>(null)
    
    // State flows for server connection status
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: StateFlow<JellyfinServer?> = _currentServer.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    companion object {
        private const val TAG = "JellyfinAuthRepository"
        private const val TOKEN_VALIDITY_DURATION_MS = 50 * 60 * 1000L // 50 minutes (10 min buffer)
    }
    
    // TokenProvider implementation
    override fun token(): String? = _tokenState.value
    
    private fun saveNewToken(token: String?) {
        _tokenState.value = token
        // Server state is also updated in authenticateUser method
    }

    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            val client = clientFactory.getClient(serverUrl, null)
            val response = client.systemApi.getPublicSystemInfo()
            ApiResult.Success(response.content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to server: $serverUrl", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to connect to server: ${e.message}", e, errorType)
        }
    }

    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> {
        return authMutex.withLock {
            _isAuthenticating.value = true
            try {
                Log.d(TAG, "authenticateUser: Attempting authentication for user '$username'")
                
                val client = clientFactory.getClient(serverUrl, null)
                val response = client.userApi.authenticateUserByName(
                    AuthenticateUserByName(
                        username = username,
                        pw = password,
                    )
                )
                
                val authResult = response.content
                Log.d(TAG, "authenticateUser: Authentication successful for user '$username'")
                
                // Update server state
                val server = JellyfinServer(
                    id = authResult.serverId ?: "",
                    name = authResult.user?.name ?: username,
                    url = serverUrl,
                    isConnected = true,
                    userId = authResult.user?.id?.toString(),
                    username = username,
                    accessToken = authResult.accessToken,
                    loginTimestamp = System.currentTimeMillis()
                )
                
                _currentServer.value = server
                _isConnected.value = true
                
                // Update token state for TokenProvider
                saveNewToken(authResult.accessToken)
                
                // Save credentials for token refresh
                try {
                    secureCredentialManager.savePassword(serverUrl, username, password)
                    Log.d(TAG, "authenticateUser: Saved credentials for user '$username'")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save credentials for token refresh", e)
                }
                
                ApiResult.Success(authResult)
                
            } catch (e: Exception) {
                Log.e(TAG, "authenticateUser: Authentication failed", e)
                val errorType = RepositoryUtils.getErrorType(e)
                ApiResult.Error("Authentication failed: ${e.message}", e, errorType)
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    suspend fun reAuthenticate(): Boolean {
        val server = _currentServer.value ?: return false
        val username = server.username ?: return false
        val serverUrl = server.url
        
        return try {
            val password = secureCredentialManager.getPassword(serverUrl, username)
            if (password == null) {
                Log.w(TAG, "reAuthenticate: No saved password found for user $username")
                return false
            }
            
            Log.d(TAG, "reAuthenticate: Found saved credentials for $serverUrl, attempting authentication")
            
            val result = authenticateUser(serverUrl, username, password)
            if (result is ApiResult.Success) {
                Log.d(TAG, "reAuthenticate: Successfully re-authenticated user $username")
                true
            } else {
                Log.w(TAG, "reAuthenticate: Failed to re-authenticate user $username")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "reAuthenticate: Exception during re-authentication", e)
            false
        }
    }
    
    suspend fun forceReAuthenticate(): Boolean {
        Log.d(TAG, "forceReAuthenticate: Force refresh requested, will re-authenticate even if token appears valid")
        return reAuthenticate()
    }

    fun getCurrentServer(): JellyfinServer? = _currentServer.value
    
    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null
    
    fun isTokenExpired(): Boolean {
        val server = _currentServer.value ?: return true
        val loginTimestamp = server.loginTimestamp ?: return true
        val currentTime = System.currentTimeMillis()
        
        return (currentTime - loginTimestamp) > TOKEN_VALIDITY_DURATION_MS
    }

    suspend fun logout() {
        authMutex.withLock {
            val server = _currentServer.value
            if (server != null && server.username != null) {
                try {
                    secureCredentialManager.clearPassword(server.url, server.username)
                    Log.d(TAG, "logout: Cleared saved credentials for user ${server.username}")
                } catch (e: Exception) {
                    Log.w(TAG, "logout: Failed to clear credentials", e)
                }
            }
            
            // Clear token state
            saveNewToken(null)
            _currentServer.value = null
            _isConnected.value = false
            Log.d(TAG, "logout: User logged out successfully")
        }
    }

    // Quick Connect methods (stubs for now)
    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> {
        return ApiResult.Error("Quick Connect not implemented yet", errorType = ErrorType.UNKNOWN)
    }

    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> {
        return ApiResult.Error("Quick Connect not implemented yet", errorType = ErrorType.UNKNOWN)
    }

    suspend fun authenticateWithQuickConnect(serverUrl: String, secret: String): ApiResult<AuthenticationResult> {
        return ApiResult.Error("Quick Connect not implemented yet", errorType = ErrorType.UNKNOWN)
    }
}
