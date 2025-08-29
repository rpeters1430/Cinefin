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
import com.rpeters.jellyfin.utils.NetworkDebugger
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.ServerUrlValidator
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
import java.net.URI
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
    private val connectionOptimizer: ConnectionOptimizer, // Add optimized connection
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: Flow<JellyfinServer?> = _currentServer.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: Flow<Boolean> = _isConnected.asStateFlow()

    // ✅ FIX: Track authentication status to prevent concurrent authentication calls
    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: Flow<Boolean> = _isAuthenticating.asStateFlow()

    // Mutex to prevent race conditions in authentication
    private val authMutex = Mutex()

    companion object {
        // Token validity constants
        private const val TOKEN_VALIDITY_DURATION_MINUTES = 50
        private const val TOKEN_VALIDITY_DURATION_MS = TOKEN_VALIDITY_DURATION_MINUTES * 60 * 1000L
    }

    /**
     * Get Jellyfin API client on background thread to avoid StrictMode violations.
     */
    private suspend fun getClient(serverUrl: String, accessToken: String? = null): ApiClient {
        return clientFactory.getClient(serverUrl, accessToken)
    }

    /**
     * Data class to hold connection test results including the working URL
     */
    data class ConnectionTestResult(
        val systemInfo: PublicSystemInfo,
        val workingUrl: String,
    )

    /**
     * Test connection to a Jellyfin server using optimized parallel discovery
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        // Log network diagnostics before attempting connection
        NetworkDebugger.logNetworkDiagnostics(context, serverUrl)

        // Use optimized connection testing
        val result = connectionOptimizer.testServerConnection(serverUrl)

        // If the initial attempt failed and the URL doesn't already end with /jellyfin,
        // try with /jellyfin appended for reverse proxy setups
        if (result is ApiResult.Error && serverUrl.isNotBlank() && !serverUrl.endsWith("/jellyfin")) {
            val jellyfinUrl = "$serverUrl/jellyfin"
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "Retrying connection with /jellyfin path: $jellyfinUrl")
            }
            return connectionOptimizer.testServerConnection(jellyfinUrl)
        }

        return result
    }

    /**
     * Test connection to a Jellyfin server with automatic fallback, returning both result and working URL
     */
    private suspend fun testServerConnectionWithUrl(serverUrl: String): ApiResult<ConnectionTestResult> {
        // First, try the direct connection using the optimized connection testing
        val directResult = connectionOptimizer.testServerConnection(serverUrl)

        if (directResult is ApiResult.Success) {
            return ApiResult.Success(ConnectionTestResult(directResult.data, serverUrl))
        }

        // If the initial attempt failed and the URL doesn't already end with /jellyfin,
        // try with /jellyfin appended for reverse proxy setups
        if (serverUrl.isNotBlank() && !serverUrl.endsWith("/jellyfin")) {
            val jellyfinUrl = "$serverUrl/jellyfin"
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "Retrying connection with /jellyfin path: $jellyfinUrl")
            }
            val retryResult = connectionOptimizer.testServerConnection(jellyfinUrl)

            if (retryResult is ApiResult.Success) {
                return ApiResult.Success(ConnectionTestResult(retryResult.data, jellyfinUrl))
            }
        }

        // If we get here, all attempts failed. Return the error from the first attempt
        val errorType = getErrorType((directResult as? ApiResult.Error)?.cause ?: Exception("Unknown error"))

        // Don't log cancellation exceptions as errors
        if (errorType != ErrorType.OPERATION_CANCELLED) {
            Log.e("JellyfinAuthRepository", "All server connection attempts failed for: $serverUrl", (directResult as? ApiResult.Error)?.cause)
        }

        val host = try {
            ServerUrlValidator.extractBaseUrl(serverUrl)?.let { URI(it).host }
        } catch (e: Exception) {
            null
        } ?: "server"

        val message = when (errorType) {
            ErrorType.NETWORK -> {
                buildString {
                    append("Cannot connect to Jellyfin server '$host'.\n\n")
                    append("Please check:\n")
                    append("• Jellyfin server is running and accessible\n")
                    append("• Network connection is working\n")
                    append("• Firewall allows connections to Jellyfin ports\n")
                    append("• Server URL and port are correct\n")
                    append("• Contact your server administrator if issues persist")
                }
            }
            ErrorType.SERVER_ERROR -> "Server '$host' is reachable but returned an error. Please check if Jellyfin is running properly."
            ErrorType.UNAUTHORIZED -> "Server '$host' requires authentication to access system information."
            ErrorType.FORBIDDEN -> "Access to server '$host' is denied. Check server permissions."
            ErrorType.TIMEOUT -> "Connection to server '$host' timed out. The server may be overloaded or have network issues."
            else -> "Failed to connect to server '$host': ${(directResult as? ApiResult.Error)?.message ?: "Unknown error"}"
        }

        Log.e("JellyfinAuthRepository", "All server connection attempts failed for: $serverUrl", (directResult as? ApiResult.Error)?.cause)
        return ApiResult.Error(message, (directResult as? ApiResult.Error)?.cause, errorType)
    }

    /**
     * Authenticate user with username and password, with automatic URL fallback
     */
    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> = authMutex.withLock {
        return try {
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "authenticateUser: Attempting authentication for user '$username' on server '$serverUrl'")
            }

            // First test connection to find working URL
            when (val connectionResult = testServerConnectionWithUrl(serverUrl)) {
                is ApiResult.Success -> {
                    val workingUrl = connectionResult.data.workingUrl
                    val systemInfo = connectionResult.data.systemInfo

                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinAuthRepository", "authenticateUser: Authenticating user '$username' on '$workingUrl'")
                    }

                    val client = getClient(workingUrl)
                    val request = AuthenticateUserByName(
                        username = username,
                        pw = password,
                    )
                    val response = client.userApi.authenticateUserByName(request)
                    val authResult = response.content

                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinAuthRepository", "authenticateUser: Authentication successful for user '$username'")
                        Log.d("JellyfinAuthRepository", "authenticateUser: New access token: ${authResult.accessToken?.substring(0, 10)}...")
                    }

                    // Update current server state with real name and version
                    val server = JellyfinServer(
                        id = authResult.serverId.toString(),
                        name = systemInfo.serverName ?: "Unknown Server",
                        url = workingUrl.trimEnd('/'),
                        isConnected = true,
                        version = systemInfo.version,
                        userId = authResult.user?.id.toString(),
                        username = authResult.user?.name,
                        accessToken = authResult.accessToken,
                        loginTimestamp = System.currentTimeMillis(),
                        originalServerUrl = normalizeServerUrl(serverUrl), // Store normalized original URL for credential lookups
                    )

                    _currentServer.value = server
                    _isConnected.value = true

                    // Save credentials for token refresh
                    // Use a normalized version of the original URL to ensure consistent lookups
                    val normalizedUrl = normalizeServerUrl(serverUrl)
                    try {
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinAuthRepository", "authenticateUser: Saving credentials for user '$username' on server '$normalizedUrl'")
                        }
                        secureCredentialManager.savePassword(normalizedUrl, username, password)
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinAuthRepository", "authenticateUser: Saved credentials for user '$username' on server '$normalizedUrl'")
                        }
                    } catch (e: Exception) {
                        Log.w("JellyfinAuthRepository", "authenticateUser: Failed to save credentials for token refresh", e)
                    }

                    ApiResult.Success(authResult)
                }
                is ApiResult.Error -> {
                    // Connection test failed, return the connection error
                    Log.e("JellyfinAuthRepository", "authenticateUser: Server connection failed during authentication", connectionResult.cause)
                    return ApiResult.Error(connectionResult.message, connectionResult.cause, connectionResult.errorType)
                }
                is ApiResult.Loading -> {
                    return ApiResult.Error("authenticateUser: Unexpected loading state during authentication", null, ErrorType.UNKNOWN)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Don't log cancellation exceptions - these are expected during navigation/lifecycle changes
            throw e
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            val message = when (errorType) {
                ErrorType.UNAUTHORIZED -> "Invalid username or password."
                ErrorType.NETWORK -> "Cannot reach server. Check network connection."
                ErrorType.SERVER_ERROR -> "Server error during authentication."
                ErrorType.FORBIDDEN -> "Access forbidden. Check user permissions."
                else -> "Authentication failed: ${e.message}"
            }

            Log.e("JellyfinAuthRepository", "authenticateUser: Authentication failed for user '$username'", e)
            ApiResult.Error(message, e, errorType)
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
                originalServerUrl = normalizeServerUrl(serverUrl), // Store normalized original URL for credential lookups
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
     * ✅ ENHANCED: Re-authenticate using saved credentials with early validation
     * Improved to prevent concurrent authentication attempts and better handle race conditions
     */
    suspend fun reAuthenticate(): Boolean = reAuthenticateInternal(false)

    /**
     * ✅ NEW: Force re-authentication even if client thinks token is valid
     * Used when server returns 401 errors indicating the token is actually expired
     */
    suspend fun forceReAuthenticate(): Boolean = reAuthenticateInternal(true)

    /**
     * Internal re-authentication method with force option
     */
    private suspend fun reAuthenticateInternal(forceRefresh: Boolean): Boolean = authMutex.withLock {
        val server = _currentServer.value ?: return@withLock false

        // ✅ FIX: Early check - if token is already valid and not forcing, don't re-authenticate
        if (!forceRefresh && !isTokenExpired()) {
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "reAuthenticate: Token is already valid, skipping re-authentication")
            }
            return@withLock true
        }

        if (forceRefresh && BuildConfig.DEBUG) {
            Log.d("JellyfinAuthRepository", "reAuthenticate: Force refresh requested, will re-authenticate even if token appears valid")
        }

        // ✅ FIX: Check if already authenticating - wait for completion
        if (_isAuthenticating.value) {
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "reAuthenticate: Already authenticating, waiting for completion")
            }

            // Wait for authentication to complete with timeout
            val maxWaitMs = 10000L // 10 seconds timeout
            val pollIntervalMs = 100L
            var waitedMs = 0L
            while (_isAuthenticating.value && waitedMs < maxWaitMs) {
                kotlinx.coroutines.delay(pollIntervalMs)
                waitedMs += pollIntervalMs
            }

            // After waiting, check if token is now valid
            if (!isTokenExpired()) {
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinAuthRepository", "reAuthenticate: Authentication completed by another thread, token is valid")
                }
                return@withLock true
            } else {
                if (BuildConfig.DEBUG) {
                    Log.w("JellyfinAuthRepository", "reAuthenticate: Authentication completed by another thread, but token is still expired")
                }
                return@withLock false
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinAuthRepository", "reAuthenticate: Starting re-authentication for user ${server.username} on ${server.url}")
        }

        try {
            // ✅ FIX: Set authentication status to prevent concurrent calls
            _isAuthenticating.value = true

            // Clear any cached clients before re-authenticating
            clientFactory.invalidateClient()

            // Get saved password for the current server and username
            // Use the stored original server URL for credential lookup, fallback to extracting from current URL
            val credentialUrl = server.originalServerUrl ?: normalizeServerUrl(server.url)
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "reAuthenticate: Looking for saved password for user '${server.username}' on '$credentialUrl'")
                Log.d("JellyfinAuthRepository", "reAuthenticate: Server URL: '${server.url}'")
                Log.d("JellyfinAuthRepository", "reAuthenticate: Original server URL: '${server.originalServerUrl}'")
            }

            // Try to get the password with detailed logging
            val savedPassword = try {
                val password = secureCredentialManager.getPassword(credentialUrl, server.username ?: "")
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinAuthRepository", "reAuthenticate: getPassword returned ${if (password != null) "a password" else "null"}")
                }
                password
            } catch (e: Exception) {
                Log.e("JellyfinAuthRepository", "reAuthenticate: Exception while getting password from credential manager", e)
                null
            }

            if (savedPassword == null) {
                Log.w("JellyfinAuthRepository", "reAuthenticate: No saved password found for user '${server.username}' on '$credentialUrl'")
                // Try with the server URL directly as a fallback
                val fallbackUrl = server.url
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinAuthRepository", "reAuthenticate: Trying fallback URL: '$fallbackUrl'")
                }
                val fallbackPassword = try {
                    val password = secureCredentialManager.getPassword(fallbackUrl, server.username ?: "")
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinAuthRepository", "reAuthenticate: getPassword with fallback URL returned ${if (password != null) "a password" else "null"}")
                    }
                    password
                } catch (e: Exception) {
                    Log.e("JellyfinAuthRepository", "reAuthenticate: Exception while getting password from credential manager with fallback URL", e)
                    null
                }

                if (fallbackPassword == null) {
                    Log.w("JellyfinAuthRepository", "reAuthenticate: No saved password found for user '${server.username}' on fallback URL '$fallbackUrl'")
                    // If we can't re-authenticate, logout the user
                    logout()
                    return@withLock false
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinAuthRepository", "reAuthenticate: Found saved credentials with fallback URL: '$fallbackUrl'")
                    }
                    // Re-authenticate using saved credentials with fallback URL
                    when (val authResult = authenticateUser(fallbackUrl, server.username ?: "", fallbackPassword)) {
                        is ApiResult.Success -> {
                            if (BuildConfig.DEBUG) {
                                Log.d("JellyfinAuthRepository", "reAuthenticate: Successfully re-authenticated user '${server.username}' with fallback URL")
                            }
                            // Update the current server with the new token and login timestamp
                            val updatedServer = server.copy(
                                accessToken = authResult.data.accessToken,
                                loginTimestamp = System.currentTimeMillis(),
                                originalServerUrl = fallbackUrl, // Update originalServerUrl to match the working URL
                            )
                            _currentServer.value = updatedServer
                            _isConnected.value = true

                            // Clear client factory again to ensure fresh token is used
                            clientFactory.refreshClient(updatedServer.url, updatedServer.accessToken)

                            // Verify that the token is actually updated
                            if (BuildConfig.DEBUG) {
                                Log.d("JellyfinAuthRepository", "reAuthenticate: Token updated to '${updatedServer.accessToken?.substring(0, 10)}...'")
                            }

                            return@withLock true
                        }
                        is ApiResult.Error -> {
                            Log.w("JellyfinAuthRepository", "reAuthenticate: Failed to re-authenticate with fallback URL: ${authResult.message}")
                            // If re-authentication fails, logout the user
                            logout()
                            return@withLock false
                        }
                        is ApiResult.Loading -> {
                            if (BuildConfig.DEBUG) {
                                Log.d("JellyfinAuthRepository", "reAuthenticate: Authentication in progress with fallback URL")
                            }
                            return@withLock false
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d("JellyfinAuthRepository", "reAuthenticate: Found saved credentials for $credentialUrl, attempting authentication")
            }

            // Re-authenticate using saved credentials (use the credential URL for consistency)
            when (val authResult = authenticateUser(credentialUrl, server.username ?: "", savedPassword)) {
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
                    clientFactory.refreshClient(updatedServer.url, updatedServer.accessToken)

                    // Verify that the token is actually updated
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinAuthRepository", "reAuthenticate: Token updated to ${updatedServer.accessToken?.substring(0, 10)}...")
                    }

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
        } finally {
            // ✅ FIX: Always clear authentication status when done
            _isAuthenticating.value = false
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
        val server = _currentServer.value
        if (server != null) {
            clientFactory.refreshClient(server.url, null)
        } else {
            clientFactory.invalidateClient()
        }
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

    /**
     * Normalize server URL for consistent credential storage and lookup
     * This ensures that URLs with different ports (like 8920 -> 443 redirects) are handled consistently
     */
    private fun normalizeServerUrl(serverUrl: String): String {
        return try {
            val uri = URI(serverUrl.trimEnd('/'))
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return serverUrl
            val path = uri.path?.takeIf { it.isNotEmpty() && it != "/" } ?: ""

            // Normalize to use the hostname without port for credential storage
            // This handles cases where the server URL changes ports during connection resolution
            "$scheme://$host$path"
        } catch (e: Exception) {
            Log.w("JellyfinAuthRepository", "Failed to normalize server URL: $serverUrl", e)
            serverUrl
        }
    }

    /**
     * Extracts the base server URL without the /jellyfin path for consistent credential storage
     */
    private fun extractBaseServerUrl(fullUrl: String): String {
        return if (fullUrl.endsWith("/jellyfin")) {
            fullUrl.removeSuffix("/jellyfin")
        } else {
            fullUrl
        }
    }

    private fun getErrorType(e: Throwable): ErrorType {
        return when (e) {
            is java.util.concurrent.CancellationException,
            is kotlinx.coroutines.CancellationException,
            -> {
                // Don't log cancellation as an error - it's expected during navigation
                ErrorType.OPERATION_CANCELLED
            }
            is java.net.UnknownHostException -> ErrorType.NETWORK // DNS resolution failed
            is java.net.ConnectException -> ErrorType.NETWORK // Connection refused
            is java.net.SocketTimeoutException -> ErrorType.TIMEOUT // Connection timeout
            is java.net.NoRouteToHostException -> ErrorType.NETWORK // No route to host
            is java.security.cert.CertificateException -> ErrorType.SERVER_ERROR // SSL certificate issues
            is javax.net.ssl.SSLException -> ErrorType.SERVER_ERROR // SSL handshake issues
            is org.jellyfin.sdk.api.client.exception.TimeoutException -> ErrorType.TIMEOUT // Jellyfin SDK timeout
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
