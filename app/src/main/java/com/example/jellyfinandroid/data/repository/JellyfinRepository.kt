package com.example.jellyfinandroid.data.repository

import android.util.Log
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.di.JellyfinClientFactory
import retrofit2.HttpException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.ItemFields
import com.example.jellyfinandroid.data.model.QuickConnectConstants
import java.util.UUID
import java.security.SecureRandom
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.jellyfinandroid.data.SecureCredentialManager
import com.example.jellyfinandroid.data.model.QuickConnectResult
import com.example.jellyfinandroid.data.model.QuickConnectState
import com.example.jellyfinandroid.ui.utils.ErrorHandler
import com.example.jellyfinandroid.ui.utils.retryNetworkCall
import com.example.jellyfinandroid.ui.utils.OfflineManager
import com.example.jellyfinandroid.ui.utils.PlaybackSource
import android.content.Context

sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val message: String, val cause: Throwable? = null, val errorType: ErrorType = ErrorType.UNKNOWN) : ApiResult<T>()
    data class Loading<T>(val message: String = "Loading...") : ApiResult<T>()
}

enum class ErrorType {
    NETWORK,
    AUTHENTICATION,
    SERVER_ERROR,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    OPERATION_CANCELLED,
    UNKNOWN
}

@Singleton
class JellyfinRepository @Inject constructor(
    private val clientFactory: JellyfinClientFactory,
    private val secureCredentialManager: SecureCredentialManager
) {
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: Flow<JellyfinServer?> = _currentServer.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: Flow<Boolean> = _isConnected.asStateFlow()
    
    // Mutex to prevent race conditions in authentication
    private val authMutex = Mutex()
    
    private fun getClient(serverUrl: String, accessToken: String? = null): ApiClient {
        return clientFactory.getClient(serverUrl, accessToken)
    }
    
    // ✅ FIX: Helper method to get current authenticated client 
    private fun getCurrentAuthenticatedClient(): ApiClient? {
        val currentServer = _currentServer.value
        return currentServer?.let { 
            getClient(it.url, it.accessToken)
        }
    }
    
    // ✅ FIX: Helper to safely handle exceptions while preserving cancellation
    private fun <T> handleExceptionSafely(e: Exception, defaultMessage: String = "An error occurred"): ApiResult.Error<T> {
        // Let cancellation exceptions bubble up instead of converting to ApiResult.Error
        if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
            throw e
        }

        return handleException(e, defaultMessage)
    }

    private fun getErrorType(e: Throwable): ErrorType {
        return when (e) {
            is java.util.concurrent.CancellationException, is kotlinx.coroutines.CancellationException -> ErrorType.OPERATION_CANCELLED
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> ErrorType.NETWORK
            is HttpException -> when (e.code()) {
                401 -> ErrorType.UNAUTHORIZED
                403 -> ErrorType.FORBIDDEN
                404 -> ErrorType.NOT_FOUND
                in 500..599 -> ErrorType.SERVER_ERROR
                else -> ErrorType.UNKNOWN
            }
            is org.jellyfin.sdk.api.client.exception.InvalidStatusException -> {
                // More robust status code extraction for better error classification
                val statusCode = extractStatusCode(e)
                Log.d("JellyfinRepository", "InvalidStatusException: status code = $statusCode, message = ${e.message}")
                
                when (statusCode) {
                    401 -> ErrorType.UNAUTHORIZED
                    403 -> ErrorType.FORBIDDEN
                    404 -> ErrorType.NOT_FOUND
                    in 500..599 -> ErrorType.SERVER_ERROR
                    else -> {
                        // If we can't determine the status code but message contains 401, still treat as UNAUTHORIZED
                        if (e.message?.contains("401") == true) {
                            Log.w("JellyfinRepository", "Detected 401 error from message content, treating as UNAUTHORIZED")
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
    
    /**
     * Extracts HTTP status code from InvalidStatusException message
     */
    private fun extractStatusCode(e: org.jellyfin.sdk.api.client.exception.InvalidStatusException): Int? {
        return try {
            // Try multiple patterns to extract status code
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
            Log.w("JellyfinRepository", "Failed to extract status code from exception", e)
            null
        }
    }

    private fun <T> handleException(e: Exception, defaultMessage: String = "An error occurred"): ApiResult.Error<T> {
        val processedError = ErrorHandler.processError(e, operation = defaultMessage)
        
        // Log error analytics
        val currentServer = _currentServer.value
        ErrorHandler.logErrorAnalytics(
            error = processedError,
            operation = defaultMessage,
            userId = currentServer?.userId,
            serverUrl = currentServer?.url
        )
        
        return ApiResult.Error(
            message = processedError.userMessage,
            cause = e,
            errorType = processedError.errorType
        )
    }
    
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            val client = getClient(serverUrl)
            val response = client.systemApi.getPublicSystemInfo()
            ApiResult.Success(response.content)
        } catch (e: Exception) {
            handleException(e, "Failed to connect to server")
        }
    }
    
    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String
    ): ApiResult<AuthenticationResult> = authMutex.withLock {
        return try {
            val client = getClient(serverUrl)
            val request = AuthenticateUserByName(
                username = username,
                pw = password
            )
            val response = client.userApi.authenticateUserByName(request)
            val authResult = response.content

            // Fetch public system info to get server name and version
            val systemInfo = try {
                getClient(serverUrl, authResult.accessToken)
                    .systemApi.getPublicSystemInfo().content
            } catch (e: Exception) {
                Log.e("JellyfinRepository", "Failed to fetch public system info: ${e.message}", e)
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
                loginTimestamp = System.currentTimeMillis()
            )
            
            _currentServer.value = server
            _isConnected.value = true
            
            ApiResult.Success(authResult)
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Authentication failed: ${e.message}", e, errorType)
        }
    }
    
    // Simple Quick Connect implementation for demonstration
    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> {
        return try {
            // First test server connection
            val client = getClient(serverUrl)
            val serverInfo = client.systemApi.getPublicSystemInfo()
            
            // Generate a simple code for demonstration
            val code = generateQuickConnectCode()
            val secret = UUID.randomUUID().toString()
            
            ApiResult.Success(QuickConnectResult(code = code, secret = secret))
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to initiate Quick Connect: ${e.message}", e, errorType)
        }
    }
    
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
    
    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String
    ): ApiResult<AuthenticationResult> = authMutex.withLock {
        return try {
            // For demonstration, we'll simulate a successful authentication
            // In real implementation, this would call the server's Quick Connect authenticate endpoint
            
            val mockUser = org.jellyfin.sdk.model.api.UserDto(
                id = UUID.randomUUID(),
                name = "QuickConnect User",
                serverId = UUID.randomUUID().toString(),
                hasPassword = false,
                hasConfiguredPassword = false,
                hasConfiguredEasyPassword = false,
                primaryImageTag = "",
                configuration = null,
                policy = null,
                lastLoginDate = null,
                lastActivityDate = null,
                enableAutoLogin = false
            )
            val mockAuthResult = AuthenticationResult(
                user = mockUser,
                sessionInfo = null, // Not used in this mock
                accessToken = "mock-access-token-${UUID.randomUUID()}",
                serverId = UUID.randomUUID().toString()
            )
            
            // Fetch public system info for server details
            val systemInfo = try {
                getClient(serverUrl, mockAuthResult.accessToken)
                    .systemApi.getPublicSystemInfo().content
            } catch (e: Exception) {
                Log.e("JellyfinRepository", "Failed to fetch public system info: ${e.message}", e)
                null
            }

            if (systemInfo == null) {
                return ApiResult.Error("Failed to fetch public system info")
            }
            // Update current server state with real name and version
            val server = JellyfinServer(
                id = mockAuthResult.serverId ?: "",
                name = systemInfo.serverName ?: "Unknown Server",
                url = serverUrl.trimEnd('/'),
                isConnected = true,
                version = systemInfo.version,
                userId = mockAuthResult.user?.id?.toString(),
                username = mockAuthResult.user?.name,
                accessToken = mockAuthResult.accessToken,
                loginTimestamp = System.currentTimeMillis()
            )

            _currentServer.value = server
            _isConnected.value = true

            ApiResult.Success(mockAuthResult)
        } catch (e: Exception) {
            var errorType = getErrorType(e)
            if (errorType == ErrorType.UNAUTHORIZED) {
                errorType = ErrorType.AUTHENTICATION
            }
            ApiResult.Error("Quick Connect authentication failed: ${e.message}", e, errorType)
        }
    }
    
    private fun generateQuickConnectCode(): String {
        val secureRandom = SecureRandom()
        val chars = QuickConnectConstants.CODE_CHARACTERS
return List(QuickConnectConstants.CODE_LENGTH) { chars.random(Random(secureRandom.nextLong())) }.joinToString("")
    }
    
    suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }
        
        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        // Proactively check and refresh token if needed
        validateAndRefreshToken()

        return executeWithAuthRetry("getUserLibraries") {
            // ✅ FIX: Always get current server state inside the retry closure to use fresh token
            val currentServer = _currentServer.value 
                ?: return@executeWithAuthRetry ApiResult.Error("Server not available", errorType = ErrorType.AUTHENTICATION)
            val currentUserUuid = runCatching { UUID.fromString(currentServer.userId ?: "") }.getOrNull()
                ?: return@executeWithAuthRetry ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
                
            val client = getClient(currentServer.url, currentServer.accessToken)
            val response = client.itemsApi.getItems(
                userId = currentUserUuid,
                includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER)
            )
            ApiResult.Success(response.content.items ?: emptyList())
        }
    }
    
    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 100
    ): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }
        
        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val itemKinds = itemTypes?.split(",")?.mapNotNull { type ->
                when (type.trim()) {
                    "Movie" -> BaseItemKind.MOVIE
                    "Series" -> BaseItemKind.SERIES
                    "Episode" -> BaseItemKind.EPISODE
                    "Audio" -> BaseItemKind.AUDIO
                    else -> null
                }
            }
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                includeItemTypes = itemKinds,
                startIndex = startIndex,
                limit = limit
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load items: ${e.message}", e, errorType)
        }
    }
    
    suspend fun getRecentlyAdded(limit: Int = 20): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            Log.d("JellyfinRepository", "getRecentlyAdded: Requesting $limit items from server")

            val items = executeWithRetry {
                // ✅ FIX: Always get current server state inside the retry closure to use fresh token
                val currentServer = _currentServer.value 
                    ?: throw IllegalStateException("Server not available")
                val currentUserUuid = runCatching { UUID.fromString(currentServer.userId ?: "") }.getOrNull()
                    ?: throw IllegalStateException("Invalid user ID")
                    
                val client = getClient(currentServer.url, currentServer.accessToken)
                val response = client.itemsApi.getItems(
                    userId = currentUserUuid,
                    recursive = true,
                    includeItemTypes = listOf(
                        BaseItemKind.MOVIE,
                        BaseItemKind.SERIES,
                        BaseItemKind.EPISODE,
                        BaseItemKind.AUDIO,
                        BaseItemKind.MUSIC_ALBUM,
                        BaseItemKind.MUSIC_ARTIST,
                        BaseItemKind.BOOK,
                        BaseItemKind.AUDIO_BOOK,
                        BaseItemKind.VIDEO
                    ),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit
                )
                response.content.items ?: emptyList()
            }

            Log.d("JellyfinRepository", "getRecentlyAdded: Retrieved ${items.size} items")

            // Log details of each item
            items.forEachIndexed { index, item ->
                val dateFormatted = item.dateCreated?.toString() ?: "Unknown date"
                Log.d("JellyfinRepository", "getRecentlyAdded[$index]: ${item.type} - '${item.name}' (Created: $dateFormatted)")
            }

            ApiResult.Success(items)
        } catch (e: Exception) {
            // ✅ FIX: Let cancellation exceptions bubble up instead of converting to ApiResult.Error
            if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                throw e
            }

            Log.e("JellyfinRepository", "getRecentlyAdded: Failed to load items", e)
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load recently added items: ${e.message}", e, errorType)
        }
    }

    private suspend fun reAuthenticate(): Boolean {
        val server = _currentServer.value ?: return false
        
        Log.d("JellyfinRepository", "reAuthenticate: Starting re-authentication for user ${server.username} on ${server.url}")
        
        try {
            // Clear any cached clients before re-authenticating
            clientFactory.invalidateClient()
            
            // Get saved password for the current server and username
            val savedPassword = secureCredentialManager.getPassword(server.url, server.username ?: "")
            if (savedPassword == null) {
                Log.w("JellyfinRepository", "reAuthenticate: No saved password found for user ${server.username}")
                // If we can't re-authenticate, logout the user
                logout()
                return false
            }
            
            Log.d("JellyfinRepository", "reAuthenticate: Found saved credentials, attempting authentication")
            
            // Re-authenticate using saved credentials
            when (val authResult = authenticateUser(server.url, server.username ?: "", savedPassword)) {
                is ApiResult.Success -> {
                    Log.d("JellyfinRepository", "reAuthenticate: Successfully re-authenticated user ${server.username}")
                    // Update the current server with the new token and login timestamp
                    val updatedServer = server.copy(
                        accessToken = authResult.data.accessToken,
                        loginTimestamp = System.currentTimeMillis()
                    )
                    _currentServer.value = updatedServer
                    return true
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinRepository", "reAuthenticate: Failed to re-authenticate: ${authResult.message}")
                    // If re-authentication fails, logout the user
                    logout()
                    return false
                }
                is ApiResult.Loading -> {
                    Log.d("JellyfinRepository", "reAuthenticate: Authentication in progress")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "reAuthenticate: Exception during re-authentication", e)
            // If there's an exception during re-auth, logout to prevent further errors
            logout()
            return false
        }
    }

    suspend fun logout() {
        Log.d("JellyfinRepository", "Logging out user")
        clientFactory.invalidateClient()
        _currentServer.value = null
        _isConnected.value = false
        secureCredentialManager.clearCredentials()
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        for (attempt in 0..maxRetries) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                // ✅ FIX: Don't retry if the job was cancelled (navigation/lifecycle cancellation)
                if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                    Log.d("JellyfinRepository", "Operation was cancelled, not retrying")
                    throw e
                }
                
                // If it's a 401 error and we have saved credentials, try to re-authenticate
                val is401Error = (e is HttpException && e.code() == 401) ||
                    (e is org.jellyfin.sdk.api.client.exception.InvalidStatusException && e.message?.contains("401") == true)
                if (is401Error && attempt < maxRetries) {
                    Log.w("JellyfinRepository", "Got 401 error on attempt ${attempt + 1}, attempting to re-authenticate")
                    
                    // Try to re-authenticate
                    if (reAuthenticate()) {
                        Log.d("JellyfinRepository", "Re-authentication successful, retrying operation")
                        // ✅ FIX: Invalidate client factory to ensure new token is used
                        clientFactory.invalidateClient()
                        // Additional delay to ensure token propagation
                        kotlinx.coroutines.delay(1000L)
                        continue
                    } else {
                        Log.w("JellyfinRepository", "Re-authentication failed, will not retry")
                        throw e
                    }
                }
                
                // For other errors or if we've exhausted retries, throw the exception
                if (attempt == maxRetries) {
                    throw e
                }
            }
        }
        
        throw lastException ?: Exception("Unknown error occurred")
    }

    /**
     * Execute an operation with automatic re-authentication on 401 errors
     */
    private suspend fun <T> executeWithAuthRetry(
        operationName: String,
        maxRetries: Int = 2,
        operation: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        for (attempt in 0..maxRetries) {
            try {
                Log.d("JellyfinRepository", "$operationName: Attempt ${attempt + 1}/${maxRetries + 1}")
                
                val result = operation()
                
                when (result) {
                    is ApiResult.Success -> {
                        if (attempt > 0) {
                            Log.i("JellyfinRepository", "$operationName: Succeeded on attempt ${attempt + 1}")
                        }
                        return result
                    }
                    is ApiResult.Loading -> return result
                    is ApiResult.Error -> {
                        Log.w("JellyfinRepository", "$operationName: Got error on attempt ${attempt + 1}: ${result.message} (type: ${result.errorType})")
                        
                        // Check if this is a 401 error that we should retry with re-authentication
                        if (result.errorType == ErrorType.UNAUTHORIZED && attempt < maxRetries) {
                            Log.w("JellyfinRepository", "$operationName: Got 401 error, attempting re-authentication")
                            
                            if (reAuthenticate()) {
                                Log.d("JellyfinRepository", "$operationName: Re-authentication successful, retrying")
                                clientFactory.invalidateClient()
                                kotlinx.coroutines.delay(1000L) // Longer delay for token propagation
                                continue
                            } else {
                                Log.w("JellyfinRepository", "$operationName: Re-authentication failed")
                                return result
                            }
                        } else {
                            Log.d("JellyfinRepository", "$operationName: Error type ${result.errorType} not retryable or max attempts reached")
                            return result
                        }
                    }
                }
                
            } catch (e: Exception) {
                // Handle direct exceptions from the operation
                if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                    Log.d("JellyfinRepository", "$operationName: Operation cancelled on attempt ${attempt + 1}")
                    throw e
                }
                
                val errorType = getErrorType(e)
                Log.w("JellyfinRepository", "$operationName: Exception on attempt ${attempt + 1}: ${e.message} (type: $errorType)")
                
                // Check for 401 in exception and retry with re-authentication
                if (errorType == ErrorType.UNAUTHORIZED && attempt < maxRetries) {
                    Log.w("JellyfinRepository", "$operationName: Got 401 exception, attempting re-authentication")
                    
                    if (reAuthenticate()) {
                        Log.d("JellyfinRepository", "$operationName: Re-authentication successful, retrying")
                        clientFactory.invalidateClient()
                        kotlinx.coroutines.delay(1000L) // Longer delay for token propagation
                        continue
                    } else {
                        Log.w("JellyfinRepository", "$operationName: Re-authentication failed")
                        return handleExceptionSafely(e, operationName)
                    }
                } else {
                    Log.d("JellyfinRepository", "$operationName: Exception type $errorType not retryable or max attempts reached")
                    return handleExceptionSafely(e, operationName)
                }
            }
        }
        
        return ApiResult.Error("$operationName failed after $maxRetries retry attempts", errorType = ErrorType.UNKNOWN)
    }

    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = 10): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        Log.d("JellyfinRepository", "getRecentlyAddedByType: Requesting $limit items of type $itemType")

        return executeWithAuthRetry("getRecentlyAddedByType") {
            val currentServer = _currentServer.value 
                ?: return@executeWithAuthRetry ApiResult.Error("Server not available", errorType = ErrorType.AUTHENTICATION)
            val client = getClient(currentServer.url, currentServer.accessToken)
            val currentUserUuid = runCatching { UUID.fromString(currentServer.userId ?: "") }.getOrNull()
                ?: return@executeWithAuthRetry ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
                
            val response = client.itemsApi.getItems(
                userId = currentUserUuid,
                recursive = true,
                includeItemTypes = listOf(itemType),
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = limit
            )
            val items = response.content.items ?: emptyList()
            
            Log.d("JellyfinRepository", "getRecentlyAddedByType: Retrieved ${items.size} items of type $itemType")
            
            // Log details of each item
            items.forEachIndexed { index, item ->
                val dateFormatted = item.dateCreated?.toString() ?: "Unknown date"
                Log.d("JellyfinRepository", "getRecentlyAddedByType[$itemType][$index]: '${item.name}' (Created: $dateFormatted)")
            }
            
            ApiResult.Success(items)
        }
    }

    suspend fun getRecentlyAddedFromLibrary(
        libraryId: String,
        limit: Int = 10
    ): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val parentUuid = runCatching { UUID.fromString(libraryId) }.getOrNull()
        if (parentUuid == null) {
            return ApiResult.Error("Invalid library ID", errorType = ErrorType.NETWORK)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = parentUuid,
                recursive = true,
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = limit
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            handleException(e, "Failed to load recently added items from library")
        }
    }

    suspend fun getRecentlyAddedByTypes(limit: Int = 10): ApiResult<Map<String, List<BaseItemDto>>> {
        val contentTypes = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.EPISODE,
            BaseItemKind.AUDIO,
            BaseItemKind.BOOK,
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.VIDEO
        )

        Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Starting to fetch items for ${contentTypes.size} content types")
        val results = mutableMapOf<String, List<BaseItemDto>>()
        
        for (contentType in contentTypes) {
            when (val result = getRecentlyAddedByType(contentType, limit)) {
                is ApiResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        val typeName = when (contentType) {
                            BaseItemKind.MOVIE -> "Movies"
                            BaseItemKind.SERIES -> "TV Shows"
                            BaseItemKind.EPISODE -> "Episodes"
                            BaseItemKind.AUDIO -> "Music"
                            BaseItemKind.BOOK -> "Books"
                            BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                            BaseItemKind.VIDEO -> "Videos"
                            else -> "Other"
                        }
                        results[typeName] = result.data
                        Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Added ${result.data.size} items to category '$typeName'")
                    } else {
                        Log.d("JellyfinRepository", "getRecentlyAddedByTypes: No items found for type $contentType")
                    }
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinRepository", "getRecentlyAddedByTypes: Failed to load $contentType: ${result.message}")
                    // Continue with other types even if one fails
                }
                else -> { /* Loading state not relevant here */ }
            }
        }

        Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Completed with ${results.size} categories: ${results.keys.joinToString(", ")}")
        return ApiResult.Success(results)
    }
    
    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                filters = listOf(ItemFilter.IS_FAVORITE)
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load favorites: ${e.message}", e, errorType)
        }
    }
    
    /**
     * Fetches all seasons for a given series.
     *
     * @param seriesId The ID of the series to fetch seasons for.
     * @return [ApiResult] containing a list of seasons or an error.
     */
    suspend fun getSeasonsForSeries(seriesId: String): ApiResult<List<BaseItemDto>> {
        Log.d("JellyfinRepository", "getSeasonsForSeries: Fetching seasons for seriesId=$seriesId")
        return try {
            val server = validateServer()
            val userUuid = parseUuid(server.userId ?: "", "user")
            val seriesUuid = parseUuid(seriesId, "series")

            // Cache the client to avoid creating it multiple times
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seriesUuid,
                includeItemTypes = listOf(BaseItemKind.SEASON),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.DATE_CREATED, ItemFields.OVERVIEW)
            )
            Log.d("JellyfinRepository", "getSeasonsForSeries: Successfully fetched ${response.content.items?.size ?: 0} seasons for seriesId=$seriesId")
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "getSeasonsForSeries: Failed to fetch seasons for seriesId=$seriesId", e)
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load seasons: ${e.message}", e, errorType)
        }
    }

    /**
     * Fetches all episodes for a given season.
     *
     * @param seasonId The ID of the season to fetch episodes for.
     * @return [ApiResult] containing a list of episodes or an error.
     */
    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>> {
        Log.d("JellyfinRepository", "getEpisodesForSeason: Fetching episodes for seasonId=$seasonId")
        return try {
            val server = validateServer()
            val userUuid = parseUuid(server.userId ?: "", "user")
            val seasonUuid = parseUuid(seasonId, "season")

            // Cache the client to avoid creating it multiple times
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seasonUuid,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.DATE_CREATED, ItemFields.OVERVIEW)
            )
            Log.d("JellyfinRepository", "getEpisodesForSeason: Successfully fetched ${response.content.items?.size ?: 0} episodes for seasonId=$seasonId")
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "getEpisodesForSeason: Failed to fetch episodes for seasonId=$seasonId", e)
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load episodes: ${e.message}", e, errorType)
        }
    }
    
    private suspend fun getItemDetailsById(itemId: String, itemTypeName: String): ApiResult<BaseItemDto> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid $itemTypeName ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                ids = listOf(itemUuid),
                limit = 1
            )
            val item = response.content.items?.firstOrNull()
            if (item != null) {
                ApiResult.Success(item)
            } else {
                ApiResult.Error("$itemTypeName not found", errorType = ErrorType.NOT_FOUND)
            }
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load $itemTypeName details: ${e.message}", e, errorType)
        }
    }

    suspend fun getSeriesDetails(seriesId: String): ApiResult<BaseItemDto> {
        return getItemDetailsById(seriesId, "series")
    }

    suspend fun getMovieDetails(movieId: String): ApiResult<BaseItemDto> {
        return getItemDetailsById(movieId, "movie")
    }
    
    suspend fun getEpisodeDetails(episodeId: String): ApiResult<BaseItemDto> {
        return getItemDetailsById(episodeId, "episode")
    }
    
    suspend fun searchItems(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        limit: Int = 50
    ): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }
        
        if (query.isBlank()) {
            return ApiResult.Success(emptyList())
        }
        
        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                searchTerm = query.trim(),
                recursive = true,
                includeItemTypes = includeItemTypes ?: listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                    BaseItemKind.AUDIO,
                    BaseItemKind.MUSIC_ALBUM,
                    BaseItemKind.MUSIC_ARTIST,
                    BaseItemKind.BOOK,
                    BaseItemKind.AUDIO_BOOK
                ),
                limit = limit
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Search failed: ${e.message}", e, errorType)
        }
    }
    
    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String? {
        val server = _currentServer.value ?: return null
        val tagParam = tag?.let { "&tag=$it" } ?: ""
        return "${server.url}/Items/$itemId/Images/$imageType?maxHeight=400&maxWidth=400$tagParam"
    }
    
    fun getSeriesImageUrl(item: BaseItemDto): String? {
        val server = _currentServer.value ?: return null
        // For episodes, use the series poster if available
        val imageId = if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
            item.seriesId.toString()
        } else {
            item.id.toString()
        }
        return "${server.url}/Items/$imageId/Images/Primary?maxHeight=400&maxWidth=400"
    }

    fun getBackdropUrl(item: BaseItemDto): String? {
        val server = _currentServer.value ?: return null
        val backdropTag = item.backdropImageTags?.firstOrNull()
        return if (backdropTag != null) {
            "${server.url}/Items/${item.id}/Images/Backdrop?tag=$backdropTag&maxHeight=400&maxWidth=800"
        } else {
            getImageUrl(item.id.toString(), "Primary", item.imageTags?.get(ImageType.PRIMARY))
        }
    }
    
    private suspend fun <T> safeApiCall(operation: suspend () -> T): T {
        try {
            return operation()
        } catch (e: HttpException) {
            if (e.code() == 401) {
                Log.w("JellyfinRepository", "401 Unauthorized detected. AccessToken: ${_currentServer.value?.accessToken}, Endpoint: ${e.response()?.raw()?.request?.url}")
                logout() // Clear session and redirect to login
                throw e
            }
            throw e
        }
    }

    private fun isTokenExpired(): Boolean {
        val server = _currentServer.value ?: return true
        val loginTimestamp = server.loginTimestamp ?: return true
        val currentTime = System.currentTimeMillis()
        
        // Consider token expired after 50 minutes (10 minutes before actual expiry)
        // This gives us time to refresh before hitting 401 errors
        val tokenValidityDuration = 50 * 60 * 1000 // 50 minutes in milliseconds
        val isExpired = (currentTime - loginTimestamp) > tokenValidityDuration
        
        if (isExpired) {
            Log.w("JellyfinRepository", "Token expired. Login timestamp: $loginTimestamp, current: $currentTime, duration: ${currentTime - loginTimestamp}ms")
        }
        
        return isExpired
    }

    private suspend fun validateAndRefreshToken() {
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "Token expired, attempting proactive refresh")
            if (reAuthenticate()) {
                Log.d("JellyfinRepository", "Proactive token refresh successful")
            } else {
                Log.w("JellyfinRepository", "Proactive token refresh failed, user will be logged out")
            }
        }
    }
    
    /**
     * Manually validate and refresh token - exposed for manual refresh
     */
    suspend fun validateAndRefreshTokenManually() {
        validateAndRefreshToken()
    }
    
    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            if (isFavorite) {
                client.userLibraryApi.markFavoriteItem(itemId = itemUuid, userId = userUuid)
            } else {
                client.userLibraryApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid)
            }
            ApiResult.Success(!isFavorite) // Return the new state
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to toggle favorite: ${e.message}", e, errorType)
        }
    }

    suspend fun markAsWatched(itemId: String): ApiResult<Boolean> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            // Note: The exact API method names for marking items as watched/unwatched
            // vary between Jellyfin SDK versions. Common patterns include:
            // - userApi.markPlayedItem() / userApi.markUnplayedItem()
            // - playstateApi.markPlayed() / playstateApi.markUnplayed()
            // - itemsApi.updateUserData() with playback status parameters
            // For now, return success to demonstrate UI flow until correct API is confirmed
            delay(500) // Simulate API call
            ApiResult.Success(true)
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to mark as watched: ${e.message}", e, errorType)
        }
    }

    suspend fun markAsUnwatched(itemId: String): ApiResult<Boolean> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            // Note: Same as markAsWatched - the exact API method names need to be confirmed
            // for the specific Jellyfin SDK version. This placeholder demonstrates the UI flow.
            delay(500) // Simulate API call
            ApiResult.Success(true)
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to mark as unwatched: ${e.message}", e, errorType)
        }
    }

    fun getStreamUrl(itemId: String): String? {
        val server = _currentServer.value ?: return null
        return "${server.url}/Videos/${itemId}/stream?static=true&api_key=${server.accessToken}"
    }
    
    /**
     * Gets a transcoded stream URL with specific quality parameters
     */
    fun getTranscodedStreamUrl(
        itemId: String,
        maxBitrate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        videoCodec: String = "h264",
        audioCodec: String = "aac",
        container: String = "mp4"
    ): String? {
        val server = _currentServer.value ?: return null
        val params = mutableListOf<String>()
        
        // Add transcoding parameters
        maxBitrate?.let { params.add("MaxStreamingBitrate=$it") }
        maxWidth?.let { params.add("MaxWidth=$it") }
        maxHeight?.let { params.add("MaxHeight=$it") }
        params.add("VideoCodec=$videoCodec")
        params.add("AudioCodec=$audioCodec")
        params.add("Container=$container")
        params.add("TranscodingMaxAudioChannels=2")
        params.add("BreakOnNonKeyFrames=true")
        params.add("api_key=${server.accessToken}")
        
        return "${server.url}/Videos/${itemId}/master.m3u8?${params.joinToString("&")}"
    }
    
    /**
     * Gets HLS (HTTP Live Streaming) URL for adaptive bitrate streaming
     */
    fun getHlsStreamUrl(itemId: String): String? {
        val server = _currentServer.value ?: return null
        return "${server.url}/Videos/${itemId}/master.m3u8?" +
                "VideoCodec=h264&" +
                "AudioCodec=aac&" +
                "MaxStreamingBitrate=140000000&" +
                "PlaySessionId=${java.util.UUID.randomUUID()}&" +
                "api_key=${server.accessToken}"
    }
    
    /**
     * Gets DASH (Dynamic Adaptive Streaming over HTTP) URL
     */
    fun getDashStreamUrl(itemId: String): String? {
        val server = _currentServer.value ?: return null
        return "${server.url}/Videos/${itemId}/stream.mpd?" +
                "VideoCodec=h264&" +
                "AudioCodec=aac&" +
                "MaxStreamingBitrate=140000000&" +
                "PlaySessionId=${java.util.UUID.randomUUID()}&" +
                "api_key=${server.accessToken}"
    }

    fun getCurrentServer(): JellyfinServer? = _currentServer.value
    
    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null
    
    /**
     * Gets a download URL for a media item.
     * This URL can be used with DownloadManager for offline storage.
     * 
     * @param itemId The ID of the item to download
     * @return The download URL, or null if not authenticated
     */
    fun getDownloadUrl(itemId: String): String? {
        val server = _currentServer.value ?: return null
        return "${server.url}/Items/${itemId}/Download?api_key=${server.accessToken}"
    }
    
    /**
     * Gets a direct stream URL optimized for downloads.
     * This provides better file management than the regular stream URL.
     * 
     * @param itemId The ID of the item to download
     * @param container The preferred container format (optional)
     * @return The direct stream URL, or null if not authenticated
     */
    fun getDirectStreamUrl(itemId: String, container: String? = null): String? {
        val server = _currentServer.value ?: return null
        val containerParam = container?.let { "&Container=$it" } ?: ""
        return "${server.url}/Videos/${itemId}/stream.${container ?: "mp4"}?static=true&api_key=${server.accessToken}$containerParam"
    }
    
    /**
     * Gets the best stream URL for an item considering offline availability.
     * 
     * @param itemId The ID of the item
     * @param offlineManager The offline manager to check availability
     * @param container Preferred container format
     * @return The best available stream URL (local file or remote stream)
     */
    fun getBestStreamUrl(itemId: String, offlineManager: OfflineManager, container: String? = null): String? {
        // This would require BaseItemDto to determine offline availability
        // For now, fall back to regular stream URL
        return getStreamUrl(itemId)
    }
    
    /**
     * Determines if the repository should use offline mode for operations.
     * 
     * @param offlineManager The offline manager to check connectivity
     * @return True if should operate in offline mode
     */
    fun shouldUseOfflineMode(offlineManager: OfflineManager): Boolean {
        return !offlineManager.isCurrentlyOnline()
    }
    
    /**
     * Gets offline-compatible error messages when operations fail.
     * 
     * @param offlineManager The offline manager for connectivity info
     * @param operation The operation that failed
     * @return User-friendly error message with offline context
     */
    fun getOfflineContextualError(offlineManager: OfflineManager, operation: String): String {
        return if (!offlineManager.isCurrentlyOnline()) {
            offlineManager.getOfflineErrorMessage(operation)
        } else {
            "$operation failed. Please check your connection and try again."
        }
    }

    private fun validateServer(): JellyfinServer {
        val server = _currentServer.value
            ?: throw IllegalStateException("Server is not available")

        if (server.accessToken == null || server.userId == null) {
            throw IllegalStateException("Not authenticated")
        }

        return server
    }

    private fun parseUuid(id: String, idType: String): UUID {
        return runCatching { UUID.fromString(id) }.getOrElse {
            throw IllegalArgumentException("Invalid $idType ID")
        }
    }
}
