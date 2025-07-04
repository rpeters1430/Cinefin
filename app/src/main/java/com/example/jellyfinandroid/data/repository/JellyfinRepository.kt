package com.example.jellyfinandroid.data.repository

import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.di.JellyfinClientFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.AuthenticateUserByName
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
    private val clientFactory: JellyfinClientFactory
) {
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: Flow<JellyfinServer?> = _currentServer.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: Flow<Boolean> = _isConnected.asStateFlow()
    
    private fun getClient(serverUrl: String, accessToken: String? = null): ApiClient {
        return clientFactory.getClient(serverUrl, accessToken)
    }
    
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            val client = getClient(serverUrl)
            val response = client.systemApi.getPublicSystemInfo()
            ApiResult.Success(response.content)
        } catch (e: Exception) {
            val errorType = when {
                e.message?.contains("401") == true -> ErrorType.UNAUTHORIZED
                e.message?.contains("403") == true -> ErrorType.FORBIDDEN
                e.message?.contains("404") == true -> ErrorType.NOT_FOUND
                e.message?.contains("5") == true -> ErrorType.SERVER_ERROR
                else -> ErrorType.NETWORK
            }
            ApiResult.Error("Network error: ${e.message}", e, errorType)
        }
    }
    
    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String
    ): ApiResult<AuthenticationResult> {
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
            // Simulate checking state - in real implementation this would call the server
            // For now, we'll simulate that the user has approved after a delay
            kotlinx.coroutines.delay(2000) // Simulate network delay
            
            // Simulate approval (in real implementation, this would check server state)
            val state = if (secret.isNotEmpty()) "Approved" else "Pending"
            
            ApiResult.Success(QuickConnectState(state = state))
        } catch (e: Exception) {
            ApiResult.Error("Failed to get Quick Connect state: ${e.message}", e, ErrorType.NETWORK)
        }
    }
    
    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String
    ): ApiResult<AuthenticationResult> {
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
            
            // Update current server state
            val server = JellyfinServer(
                id = mockAuthResult.serverId ?: "",
                name = "Jellyfin Server",
                url = serverUrl.trimEnd('/'),
                isConnected = true,
                userId = mockAuthResult.user?.id?.toString(),
                username = mockAuthResult.user?.name,
                accessToken = mockAuthResult.accessToken
            )
            
            _currentServer.value = server
            _isConnected.value = true
            
            ApiResult.Success(mockAuthResult)
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
        
        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(userId = UUID.fromString(server.userId))
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
                userId = UUID.fromString(server.userId),
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

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = UUID.fromString(server.userId),
                recursive = true,
                includeItemTypes = listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                    BaseItemKind.AUDIO,
                    BaseItemKind.MUSIC_ALBUM,
                    BaseItemKind.MUSIC_ARTIST,
                    BaseItemKind.BOOK,
                    BaseItemKind.AUDIO_BOOK
                ),
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
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
            ApiResult.Error("Failed to load recently added items: ${e.message}", e, errorType)
        }
    }
    
    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = UUID.fromString(server.userId),
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
        
        return try {
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = UUID.fromString(server.userId),
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
    
    fun logout() {
        _currentServer.value = null
        _isConnected.value = false
    }
    
    fun getCurrentServer(): JellyfinServer? = _currentServer.value
    
    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null
}
