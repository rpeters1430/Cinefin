package com.example.jellyfinandroid.data.repository

import android.util.Log
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.di.JellyfinClientFactory
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
    
    private fun <T> handleException(e: Exception, defaultMessage: String = "An error occurred"): ApiResult.Error<T> {
        val errorType = when {
            e is java.net.UnknownHostException -> ErrorType.NETWORK
            e is java.net.ConnectException -> ErrorType.NETWORK  
            e is java.net.SocketTimeoutException -> ErrorType.NETWORK
            e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
            e.message?.contains("403") == true -> ErrorType.FORBIDDEN
            e.message?.contains("404") == true -> ErrorType.NOT_FOUND
            e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
            else -> ErrorType.NETWORK
        }
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
            
            // Update current server state
            val server = JellyfinServer(
                id = authResult.serverId.toString(),
                name = "Jellyfin Server", // We'll get the actual name from server info
                url = serverUrl.trimEnd('/'),
                isConnected = true,
                userId = authResult.user?.id.toString(),
                username = authResult.user?.name,
                accessToken = authResult.accessToken
            )
            
            _currentServer.value = server
            _isConnected.value = true
            
            ApiResult.Success(authResult)
        } catch (e: Exception) {
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.AUTHENTICATION
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                else -> ErrorType.NETWORK
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
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
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
            val errorType = when {
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                else -> ErrorType.NETWORK
            }

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
            val client = getClient(serverUrl)
            val dto = QuickConnectDto(secret = secret)
            val response = client.userApi.authenticateWithQuickConnect(dto)
            val authResult = response.content

            val server = JellyfinServer(
                id = authResult.serverId ?: "",
                name = "Jellyfin Server",
                url = serverUrl.trimEnd('/'),
                isConnected = true,
                userId = authResult.user?.id?.toString(),
                username = authResult.user?.name,
                accessToken = authResult.accessToken
            )

            _currentServer.value = server
            _isConnected.value = true

            ApiResult.Success(authResult)
        } catch (e: Exception) {
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.AUTHENTICATION
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                else -> ErrorType.NETWORK
            }
            ApiResult.Error("Quick Connect authentication failed: ${e.message}", e, errorType)
        }
    }
    
    private fun generateQuickConnectCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
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
            val response = client.itemsApi.getItems(userId = userUuid)
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
            ApiResult.Error("Failed to load libraries: ${e.message}", e, errorType)
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
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
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
            Log.e("JellyfinRepository", "getRecentlyAdded: Failed to load items", e)
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
            ApiResult.Error("Failed to load recently added items: ${e.message}", e, errorType)
        }
    }

    private suspend fun reAuthenticate(): Boolean {
        val server = _currentServer.value ?: return false
        
        try {
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
                    Log.d("JellyfinRepository", "Re-authentication successful")
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
                
                // If it's a 401 error and we have saved credentials, try to re-authenticate
                if (e.message?.contains("401") == true && attempt < maxRetries) {
                    Log.w("JellyfinRepository", "Got 401 error on attempt ${attempt + 1}, attempting to re-authenticate")
                    
                    // Try to re-authenticate
                    if (reAuthenticate()) {
                        Log.d("JellyfinRepository", "Re-authentication successful, retrying operation")
                        // Wait a bit before retrying
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
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
                val client = getClient(server.url, server.accessToken)
                val response = client.itemsApi.getItems(
                    userId = userUuid,
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
            handleException(e, "Failed to load recently added ${itemType.name.lowercase()}")
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
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
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
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
            ApiResult.Error("Failed to load seasons: ${e.message}", e, errorType)
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
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
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
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
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
