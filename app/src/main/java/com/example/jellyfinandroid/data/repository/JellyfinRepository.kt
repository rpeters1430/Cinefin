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
import java.util.UUID
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.jellyfinandroid.data.SecureCredentialManager

// Quick Connect data classes
data class QuickConnectResult(
    val code: String,
    val secret: String
)

data class QuickConnectState(
    val state: String // "Pending", "Approved", "Denied", "Expired"
)

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
                else -> ErrorType.NETWORK
            }
            else -> ErrorType.NETWORK
        }
    }

    private fun <T> handleException(e: Exception, defaultMessage: String = "An error occurred"): ApiResult.Error<T> {
        val errorType = getErrorType(e)
        val errorMessage = when (errorType) {
            ErrorType.NETWORK -> "Cannot connect to server. Please check your internet connection and server URL."
            ErrorType.UNAUTHORIZED -> "Authentication failed. Please check your credentials."
            ErrorType.FORBIDDEN -> "Access denied. You don't have permission to access this server."
            ErrorType.NOT_FOUND -> "Server not found. Please check the server URL."
            ErrorType.SERVER_ERROR -> "Server error. Please try again later."
            else -> "$defaultMessage: ${e.message}"
        }
        return ApiResult.Error(errorMessage, e, errorType)
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
                accessToken = authResult.accessToken
            )
            
            _currentServer.value = server
            _isConnected.value = true
            
            ApiResult.Success(authResult)
        } catch (e: Exception) {
            var errorType = getErrorType(e)
            if (errorType == ErrorType.UNAUTHORIZED) {
                errorType = ErrorType.AUTHENTICATION
            }
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
                accessToken = mockAuthResult.accessToken
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
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val secureRandom = SecureRandom()
        return (1..6).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
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

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER)
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            // ✅ FIX: Use helper to preserve cancellation exceptions
            handleExceptionSafely(e, "Failed to load libraries")
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
                val client = getClient(server.url, server.accessToken)
                val response = client.itemsApi.getItems(
                    userId = userUuid,
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
        
        try {
            // Clear any cached clients before re-authenticating
            clientFactory.invalidateClient()
            
            // Get saved password for the current server and username
            val savedPassword = secureCredentialManager.getPassword(server.url, server.username ?: "")
            if (savedPassword == null) {
                Log.w("JellyfinRepository", "No saved password found for re-authentication")
                return false
            }
            
            Log.d("JellyfinRepository", "Attempting to re-authenticate with saved credentials")
            
            // Re-authenticate using saved credentials
            when (val authResult = authenticateUser(server.url, server.username ?: "", savedPassword)) {
                is ApiResult.Success -> {
                    Log.d("JellyfinRepository", "Re-authentication successful with new token")
                    return true
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinRepository", "Re-authentication failed: ${authResult.message}")
                    return false
                }
                is ApiResult.Loading -> {
                    Log.w("JellyfinRepository", "Re-authentication is loading (unexpected)")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "Error during re-authentication", e)
            return false
        }
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
                if (e is HttpException && e.code() == 401 && attempt < maxRetries) {
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

    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = 10): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            Log.d("JellyfinRepository", "getRecentlyAddedByType: Requesting $limit items of type $itemType")
            
            val items = executeWithRetry {
                // ✅ FIX: Get current server state for each retry attempt
                val currentServer = _currentServer.value 
                    ?: throw IllegalStateException("Server not available")
                val client = getClient(currentServer.url, currentServer.accessToken)
                val currentUserUuid = runCatching { UUID.fromString(currentServer.userId ?: "") }.getOrNull()
                    ?: throw IllegalStateException("Invalid user ID")
                    
                val response = client.itemsApi.getItems(
                    userId = currentUserUuid,
                    recursive = true,
                    includeItemTypes = listOf(itemType),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit
                )
                response.content.items ?: emptyList()
            }
            
            Log.d("JellyfinRepository", "getRecentlyAddedByType: Retrieved ${items.size} items of type $itemType")
            
            // Log details of each item
            items.forEachIndexed { index, item ->
                val dateFormatted = item.dateCreated?.toString() ?: "Unknown date"
                Log.d("JellyfinRepository", "getRecentlyAddedByType[$itemType][$index]: '${item.name}' (Created: $dateFormatted)")
            }
            
            ApiResult.Success(items)
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "getRecentlyAddedByType: Failed to load $itemType items", e)
            handleExceptionSafely(e, "Failed to load recently added ${itemType.name.lowercase()}")
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
    
    suspend fun getSeasonsForSeries(seriesId: String): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val seriesUuid = runCatching { UUID.fromString(seriesId) }.getOrNull()
        if (seriesUuid == null) {
            return ApiResult.Error("Invalid series ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seriesUuid,
                includeItemTypes = listOf(BaseItemKind.SEASON),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING)
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load seasons: ${e.message}", e, errorType)
        }
    }

    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val seasonUuid = runCatching { UUID.fromString(seasonId) }.getOrNull()
        if (seasonUuid == null) {
            return ApiResult.Error("Invalid season ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seasonUuid,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                sortOrder = listOf(SortOrder.ASCENDING)
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load episodes: ${e.message}", e, errorType)
        }
    }
    
    suspend fun getSeriesDetails(seriesId: String): ApiResult<BaseItemDto> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val seriesUuid = runCatching { UUID.fromString(seriesId) }.getOrNull()
        if (seriesUuid == null) {
            return ApiResult.Error("Invalid series ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                ids = listOf(seriesUuid),
                limit = 1
            )
            val item = response.content.items?.firstOrNull()
            if (item != null) {
                ApiResult.Success(item)
            } else {
                ApiResult.Error("Series not found", errorType = ErrorType.NOT_FOUND)
            }
        } catch (e: Exception) {
            val errorType = getErrorType(e)
            ApiResult.Error("Failed to load series details: ${e.message}", e, errorType)
        }
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
    
    fun logout() {
        _currentServer.value = null
        _isConnected.value = false
    }
    
    fun getCurrentServer(): JellyfinServer? = _currentServer.value
    
    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null
}
