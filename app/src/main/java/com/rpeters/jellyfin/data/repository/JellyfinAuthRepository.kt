package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.model.QuickConnectResult
import com.rpeters.jellyfin.data.model.QuickConnectState
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.di.JellyfinClientFactory
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.QuickConnectDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository component responsible for authentication and server connection management.
 * Extracted from JellyfinRepository to improve code organization and maintainability.
 */
@Singleton
class JellyfinAuthRepository @Inject constructor(
    private val clientFactory: JellyfinClientFactory,
    private val secureCredentialManager: SecureCredentialManager,
) {
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: Flow<JellyfinServer?> = _currentServer.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: Flow<Boolean> = _isConnected.asStateFlow()

    // Mutex to prevent race conditions in authentication
    private val authMutex = Mutex()

    companion object {
        // Token validity constants
        private const val TOKEN_VALIDITY_DURATION_MINUTES = 50
        private const val TOKEN_VALIDITY_DURATION_MS = TOKEN_VALIDITY_DURATION_MINUTES * 60 * 1000L
    }

    private fun getClient(serverUrl: String, accessToken: String? = null): ApiClient {
        return clientFactory.getClient(serverUrl, accessToken)
    }

    /**
     * Test connection to a Jellyfin server
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            val client = getClient(serverUrl)
            val response = client.systemApi.getPublicSystemInfo()
            ApiResult.Success(response.content)
        } catch (e: Exception) {
            ApiResult.Error("Failed to connect to server: ${e.message}", e, getErrorType(e))
        }
    }

    /**
     * Authenticate user with username and password
     */
    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> = authMutex.withLock {
        return try {
            val client = getClient(serverUrl)
            val request = AuthenticateUserByName(
                username = username,
                pw = password,
            )
            val response = client.userApi.authenticateUserByName(request)
            val authResult = response.content

            // Fetch public system info to get server name and version
            val systemInfo = try {
                getClient(serverUrl, authResult.accessToken)
                    .systemApi.getPublicSystemInfo().content
            } catch (e: Exception) {
                Log.e("JellyfinAuthRepository", "Failed to fetch public system info: ${e.message}", e)
                PublicSystemInfo(serverName = "Unknown Server", version = "Unknown Version")
            }

            // Update current server state with real name and version
            val server = JellyfinServer(
                id = authResult.serverId.toString(),
                name = systemInfo.serverName ?: "Unknown Server",
                url = serverUrl.trimEnd('/'),
                isConnected = true,
                version = systemInfo.version,
                userId = authResult.user?.id.toString(),
                username = authResult.user?.name,
                accessToken = authResult.accessToken,
                loginTimestamp = System.currentTimeMillis(),
            )

            _currentServer.value = server
            _isConnected.value = true

            ApiResult.Success(authResult)
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Authentication failed: ${e.message}", e, errorType)
        }
    }

    /**
     * Start a new Quick Connect session
     */
    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> {
        return try {
            val client = getClient(serverUrl)
            val response = client.quickConnectApi.initiateQuickConnect()
            val result = response.content

            ApiResult.Success(
                QuickConnectResult(
                    code = result.code,
                    secret = result.secret,
                ),
            )
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to initiate Quick Connect: ${e.message}", e, errorType)
        }
    }

    /**
     * Get Quick Connect state
     */
    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> {
        return try {
            val client = getClient(serverUrl)
            val response = client.quickConnectApi.getQuickConnectState(secret)
            val result = response.content

            val state = if (result.authenticated) "Approved" else "Pending"
            ApiResult.Success(QuickConnectState(state = state))
        } catch (e: Exception) {
            val errorType = getErrorType(e)

            if (errorType == ErrorType.NOT_FOUND) {
                ApiResult.Success(QuickConnectState(state = "Expired"))
            } else {
                ApiResult.Error("Failed to get Quick Connect state: ${e.message}", e, errorType)
            }
        }
    }

    /**
     * Authenticate with Quick Connect
     */
    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String,
    ): ApiResult<AuthenticationResult> = authMutex.withLock {
        return try {
            val client = getClient(serverUrl)
            val request = QuickConnectDto(secret = secret)
            val response = client.userApi.authenticateWithQuickConnect(request)
            val authResult = response.content

            val systemInfo = try {
                getClient(serverUrl, authResult.accessToken)
                    .systemApi.getPublicSystemInfo().content
            } catch (e: Exception) {
                Log.e("JellyfinAuthRepository", "Failed to fetch public system info: ${e.message}", e)
                null
            }

            if (systemInfo == null) {
                return ApiResult.Error("Failed to fetch public system info")
            }

            val server = JellyfinServer(
                id = authResult.serverId ?: "",
                name = systemInfo.serverName ?: "Unknown Server",
                url = serverUrl.trimEnd('/'),
                isConnected = true,
                version = systemInfo.version,
                userId = authResult.user?.id?.toString(),
                username = authResult.user?.name,
                accessToken = authResult.accessToken,
                loginTimestamp = System.currentTimeMillis(),
            )

            _currentServer.value = server
            _isConnected.value = true

            ApiResult.Success(authResult)
        } catch (e: Exception) {
            var errorType = getErrorType(e)
            if (errorType == ErrorType.UNAUTHORIZED) {
                errorType = ErrorType.AUTHENTICATION
            }
            ApiResult.Error("Quick Connect authentication failed: ${e.message}", e, errorType)
        }
    }

    /**
     * Re-authenticate using saved credentials
     */
    suspend fun reAuthenticate(): Boolean = authMutex.withLock {
        val server = _currentServer.value ?: return@withLock false

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinAuthRepository", "reAuthenticate: Starting re-authentication for user ${server.username} on ${server.url}")
        }

        try {
            // Clear any cached clients before re-authenticating
            clientFactory.invalidateClient()

            // Get saved password for the current server and username
            val savedPassword = secureCredentialManager.getPassword(server.url, server.username ?: "")
            if (savedPassword == null) {
                Log.w("JellyfinAuthRepository", "reAuthenticate: No saved password found for user ${server.username}")
                // If we can't re-authenticate, logout the user
                logout()
                return@withLock false
            }

            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "reAuthenticate: Found saved credentials, attempting authentication")
            }

            // Re-authenticate using saved credentials
            when (val authResult = authenticateUser(server.url, server.username ?: "", savedPassword)) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinAuthRepository", "reAuthenticate: Successfully re-authenticated user ${server.username}")
                    }
                    // Update the current server with the new token and login timestamp
                    val updatedServer = server.copy(
                        accessToken = authResult.data.accessToken,
                        loginTimestamp = System.currentTimeMillis(),
                    )
                    _currentServer.value = updatedServer
                    _isConnected.value = true

                    // Clear client factory again to ensure fresh token is used
                    clientFactory.invalidateClient()

                    return@withLock true
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinAuthRepository", "reAuthenticate: Failed to re-authenticate: ${authResult.message}")
                    // If re-authentication fails, logout the user
                    logout()
                    return@withLock false
                }
                is ApiResult.Loading -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinAuthRepository", "reAuthenticate: Authentication in progress")
                    }
                    return@withLock false
                }
            }
        } catch (e: Exception) {
            Log.e("JellyfinAuthRepository", "reAuthenticate: Exception during re-authentication", e)
            // If there's an exception during re-auth, logout to prevent further errors
            logout()
            return@withLock false
        }
    }

    /**
     * Check if the current token is expired
     */
    fun isTokenExpired(): Boolean {
        val server = _currentServer.value ?: return true
        val loginTimestamp = server.loginTimestamp ?: return true
        val currentTime = System.currentTimeMillis()

        // Consider token expired after 50 minutes (10 minutes before actual expiry)
        val isExpired = (currentTime - loginTimestamp) > TOKEN_VALIDITY_DURATION_MS

        if (isExpired) {
            SecureLogger.w("JellyfinAuthRepository", "Token expired after ${(currentTime - loginTimestamp) / 1000 / 60} minutes")
        }

        return isExpired
    }

    /**
     * Validate and refresh token if needed
     */
    suspend fun validateAndRefreshToken() {
        if (isTokenExpired()) {
            SecureLogger.w("JellyfinAuthRepository", "Token expired, attempting proactive refresh")
            if (reAuthenticate()) {
                SecureLogger.auth("JellyfinAuthRepository", "Proactive token refresh successful", true)
            } else {
                SecureLogger.auth("JellyfinAuthRepository", "Proactive token refresh failed, user will be logged out", false)
            }
        }
    }

    /**
     * Logout current user
     */
    suspend fun logout() {
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinAuthRepository", "Logging out user")
        }
        clientFactory.invalidateClient()
        _currentServer.value = null
        _isConnected.value = false
        secureCredentialManager.clearCredentials()
    }

    /**
     * Get current server
     */
    fun getCurrentServer(): JellyfinServer? = _currentServer.value

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null

    /**
     * Update current server state
     */
    fun updateCurrentServer(server: JellyfinServer) {
        _currentServer.value = server
        _isConnected.value = server.isConnected
    }

    private fun getErrorType(e: Throwable): ErrorType {
        return when (e) {
            is java.util.concurrent.CancellationException, is kotlinx.coroutines.CancellationException -> ErrorType.OPERATION_CANCELLED
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> ErrorType.NETWORK
            is retrofit2.HttpException -> when (e.code()) {
                401 -> ErrorType.UNAUTHORIZED
                403 -> ErrorType.FORBIDDEN
                404 -> ErrorType.NOT_FOUND
                in 500..599 -> ErrorType.SERVER_ERROR
                else -> ErrorType.UNKNOWN
            }
            is org.jellyfin.sdk.api.client.exception.InvalidStatusException -> {
                val statusCode = extractStatusCode(e)
                when (statusCode) {
                    401 -> ErrorType.UNAUTHORIZED
                    403 -> ErrorType.FORBIDDEN
                    404 -> ErrorType.NOT_FOUND
                    in 500..599 -> ErrorType.SERVER_ERROR
                    else -> {
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

    private fun extractStatusCode(e: org.jellyfin.sdk.api.client.exception.InvalidStatusException): Int? {
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
            Log.w("JellyfinAuthRepository", "Failed to extract status code from exception", e)
            null
        }
    }
}
