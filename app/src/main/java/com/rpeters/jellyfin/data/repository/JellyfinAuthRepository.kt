package com.rpeters.jellyfin.data.repository

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.model.QuickConnectResult
import com.rpeters.jellyfin.data.model.QuickConnectState
import com.rpeters.jellyfin.data.network.TokenProvider
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.utils.RepositoryUtils
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.normalizeServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.jellyfin.sdk.model.api.QuickConnectResult as SdkQuickConnectResult

@Singleton
class JellyfinAuthRepository @Inject constructor(
    private val jellyfin: Jellyfin,
    private val secureCredentialManager: SecureCredentialManager,
    private val timeProvider: () -> Long = System::currentTimeMillis,
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
        private const val TOKEN_REFRESH_BUFFER_MS = 5 * 60 * 1000L // Refresh 5 minutes before expiration
    }

    // TokenProvider implementation
    override suspend fun token(): String? = _tokenState.value

    private fun saveNewToken(token: String?) {
        // SECURITY: Never log actual token values, even partially
        // Tokens should only be logged in extreme debugging scenarios using secure logging
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Saving new token: ${if (token != null) "[PRESENT]" else "[NULL]"}")
        }
        _tokenState.update { token }
        // Server state is also updated in authenticateUser method
    }

    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            SecureLogger.d(TAG, "testServerConnection: Attempting to connect to server")
            val client = createApiClient(serverUrl)
            val response = client.systemApi.getPublicSystemInfo()
            SecureLogger.d(TAG, "testServerConnection: Successfully connected to server")
            ApiResult.Success(response.content)
        } catch (e: InvalidStatusException) {
            Log.e(TAG, "Server returned error status", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Server error: ${e.message}", e, errorType)
        } catch (e: IOException) {
            Log.e(TAG, "I/O error while testing server connection", e)
            val errorType = RepositoryUtils.getErrorType(e)
            val message = if (errorType == ErrorType.DNS_RESOLUTION) {
                "Could not resolve server hostname. Please check the server address for typos, or try using an IP address (e.g., 192.168.1.100)"
            } else {
                "Network error: ${e.message}"
            }
            ApiResult.Error(message, e, errorType)
        }
    }

    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> {
        return authMutex.withLock {
            authenticateUserInternal(serverUrl, username, password)
        }
    }

    private suspend fun authenticateUserInternal(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> {
        _isAuthenticating.update { true }
        try {
            SecureLogger.d(TAG, "authenticateUser: Attempting authentication")
            val normalizedServerUrl = normalizeServerUrl(serverUrl)

            val client = createApiClient(serverUrl)
            val response = client.userApi.authenticateUserByName(
                AuthenticateUserByName(
                    username = username,
                    pw = password,
                ),
            )

            val authResult = response.content
            SecureLogger.d(TAG, "authenticateUser: Authentication successful")

            persistAuthenticationState(
                serverUrl = serverUrl,
                normalizedServerUrl = normalizedServerUrl,
                authResult = authResult,
                usernameHint = username,
            )

            return ApiResult.Success(authResult)
        } catch (e: InvalidStatusException) {
            Log.e(TAG, "authenticateUser: Server returned error status", e)
            val errorType = RepositoryUtils.getErrorType(e)
            return ApiResult.Error("Authentication failed: ${e.message}", e, errorType)
        } catch (e: IOException) {
            Log.e(TAG, "authenticateUser: I/O error during authentication", e)
            val errorType = RepositoryUtils.getErrorType(e)
            val message = if (errorType == ErrorType.DNS_RESOLUTION) {
                "Could not resolve server hostname. Please check the server address for typos, or try using an IP address (e.g., 192.168.1.100)"
            } else {
                "Network error during authentication: ${e.message}"
            }
            return ApiResult.Error(message, e, errorType)
        } finally {
            _isAuthenticating.update { false }
        }
    }

    suspend fun reAuthenticate(): Boolean {
        return authMutex.withLock {
            reAuthenticateInternal()
        }
    }

    suspend fun forceReAuthenticate(): Boolean {
        return authMutex.withLock {
            Log.d(TAG, "forceReAuthenticate: Force refresh requested, checking if re-auth still needed")

            // Double-check: if another thread just successfully re-authenticated,
            // we might not need to do it again
            val currentServer = _currentServer.value
            if (currentServer?.accessToken != null && !isTokenExpired()) {
                Log.d(TAG, "forceReAuthenticate: Token is now valid, skipping re-authentication")
                return@withLock true
            }

            // Token is still invalid, proceed with re-authentication
            Log.d(TAG, "forceReAuthenticate: Token still invalid, proceeding with re-authentication")
            reAuthenticateInternal()
        }
    }

    private suspend fun reAuthenticateInternal(): Boolean {
        val server = _currentServer.value ?: return false
        val username = server.username ?: return false
        val serverUrl = server.url

        try {
            val password = secureCredentialManager.getPassword(serverUrl, username)
            if (password == null) {
                Log.w(TAG, "reAuthenticate: No saved password found for user $username")
                return false
            }

            Log.d(TAG, "reAuthenticate: Found saved credentials for $serverUrl, attempting authentication")

            val result = authenticateUserInternal(serverUrl, username, password)
            return if (result is ApiResult.Success) {
                Log.d(TAG, "reAuthenticate: Successfully re-authenticated user $username")
                true
            } else {
                Log.w(TAG, "reAuthenticate: Failed to re-authenticate user $username")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "reAuthenticate: I/O error during re-authentication", e)
            return false
        }
    }

    fun getCurrentServer(): JellyfinServer? = _currentServer.value

    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null

    fun isTokenExpired(): Boolean {
        val server = _currentServer.value ?: return true
        val loginTimestamp = server.loginTimestamp ?: return true
        val currentTime = timeProvider()

        return (currentTime - loginTimestamp) > TOKEN_VALIDITY_DURATION_MS
    }

    /**
     * Checks if the token is approaching expiration and should be refreshed proactively.
     * Returns true if the token is within the refresh threshold (5 minutes before expiration).
     * This allows the interceptor to refresh tokens before they expire, reducing blocking.
     */
    fun shouldRefreshToken(): Boolean {
        val server = _currentServer.value ?: return false
        val loginTimestamp = server.loginTimestamp ?: return false
        val currentTime = timeProvider()

        val tokenAge = currentTime - loginTimestamp
        val refreshThreshold = TOKEN_VALIDITY_DURATION_MS - TOKEN_REFRESH_BUFFER_MS

        return tokenAge >= refreshThreshold
    }

    @VisibleForTesting
    fun seedCurrentServer(server: JellyfinServer?) {
        _currentServer.update { server }
        _isConnected.update { server?.isConnected == true }
    }

    suspend fun logout() {
        authMutex.withLock {
            val server = _currentServer.value
            if (server != null && server.username != null) {
                try {
                    secureCredentialManager.clearPassword(server.url, server.username)
                    Log.d(TAG, "logout: Cleared saved credentials for user ${server.username}")
                } catch (e: IOException) {
                    Log.w(TAG, "logout: I/O error clearing credentials", e)
                }
            }

            // Clear token state
            saveNewToken(null)
            _currentServer.update { null }
            _isConnected.update { false }
            Log.d(TAG, "logout: User logged out successfully")
        }
    }

    private fun createApiClient(serverUrl: String, accessToken: String? = null): ApiClient {
        return jellyfin.createApi(
            baseUrl = serverUrl,
            accessToken = accessToken,
        )
    }

    private suspend fun persistAuthenticationState(
        serverUrl: String,
        normalizedServerUrl: String = normalizeServerUrl(serverUrl),
        authResult: AuthenticationResult,
        usernameHint: String? = null,
    ) {
        val resolvedUsername = usernameHint ?: authResult.user?.name
        val server = JellyfinServer(
            id = authResult.serverId ?: "",
            name = authResult.user?.name ?: resolvedUsername ?: serverUrl,
            url = serverUrl,
            isConnected = true,
            userId = authResult.user?.id?.toString(),
            username = resolvedUsername,
            accessToken = authResult.accessToken,
            loginTimestamp = System.currentTimeMillis(),
            normalizedUrl = normalizedServerUrl,
        )

        _currentServer.update { server }
        _isConnected.update { true }
        saveNewToken(authResult.accessToken)
    }

    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> {
        return try {
            val client = createApiClient(serverUrl)
            val response = client.quickConnectApi.initiateQuickConnect()
            ApiResult.Success(response.content.toDomainQuickConnectResult())
        } catch (e: InvalidStatusException) {
            Log.e(TAG, "initiateQuickConnect: Server returned error status", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Quick Connect error: ${e.message}", e, errorType)
        }
    }

    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> {
        return try {
            val client = createApiClient(serverUrl)
            val response = client.quickConnectApi.getQuickConnectState(secret)
            val state = if (response.content.authenticated) {
                QuickConnectState(state = "Approved")
            } else {
                QuickConnectState(state = "Pending")
            }
            ApiResult.Success(state)
        } catch (e: InvalidStatusException) {
            when (e.status) {
                401, 403 -> ApiResult.Success(QuickConnectState(state = "Denied"))
                404 -> ApiResult.Success(QuickConnectState(state = "Expired"))
                else -> {
                    Log.e(TAG, "getQuickConnectState: Failed with unexpected status", e)
                    val errorType = RepositoryUtils.getErrorType(e)
                    ApiResult.Error("Failed to check Quick Connect state: ${e.message}", e, errorType)
                }
            }
        }
    }

    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String,
    ): ApiResult<AuthenticationResult> {
        return authMutex.withLock {
            _isAuthenticating.update { true }
            try {
                val client = createApiClient(serverUrl)
                val response = client.userApi.authenticateWithQuickConnect(
                    org.jellyfin.sdk.model.api.QuickConnectDto(secret = secret),
                )
                val authResult = response.content

                persistAuthenticationState(
                    serverUrl = serverUrl,
                    authResult = authResult,
                )

                ApiResult.Success(authResult)
            } catch (e: InvalidStatusException) {
                Log.e(TAG, "authenticateWithQuickConnect: Server returned error status", e)
                val errorType = RepositoryUtils.getErrorType(e)
                ApiResult.Error("Quick Connect authentication failed: ${e.message}", e, errorType)
            } finally {
                _isAuthenticating.update { false }
            }
        }
    }

    private fun SdkQuickConnectResult.toDomainQuickConnectResult(): QuickConnectResult {
        return QuickConnectResult(
            code = code,
            secret = secret,
        )
    }
}
