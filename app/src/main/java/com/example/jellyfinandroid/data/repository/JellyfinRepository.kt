package com.example.jellyfinandroid.data.repository

import com.example.jellyfinandroid.data.AuthenticationResult
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.data.ServerInfo
import com.example.jellyfinandroid.di.JellyfinApiServiceFactory
import com.example.jellyfinandroid.network.AuthenticationRequest
import com.example.jellyfinandroid.network.BaseItem
import com.example.jellyfinandroid.network.JellyfinApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val message: String, val cause: Throwable? = null) : ApiResult<T>()
    data class Loading<T>(val message: String = "Loading...") : ApiResult<T>()
}

@Singleton
class JellyfinRepository @Inject constructor(
    private val apiServiceFactory: JellyfinApiServiceFactory
) {
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: Flow<JellyfinServer?> = _currentServer.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: Flow<Boolean> = _isConnected.asStateFlow()
    
    private fun getApiService(serverUrl: String): JellyfinApiService {
        return apiServiceFactory.getApiService(serverUrl)
    }
    
    suspend fun testServerConnection(serverUrl: String): ApiResult<ServerInfo> {
        return try {
            val apiService = getApiService(serverUrl)
            val response = apiService.getServerInfo()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error("Failed to connect to server: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
    
    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String
    ): ApiResult<AuthenticationResult> {
        return try {
            val apiService = getApiService(serverUrl)
            val request = AuthenticationRequest(
                Username = username,
                Pw = password
            )
            
            val response = apiService.authenticateByName(request)
            if (response.isSuccessful && response.body() != null) {
                val authResult = response.body()!!
                
                // Update current server state
                val server = JellyfinServer(
                    id = authResult.serverId,
                    name = "Jellyfin Server", // We'll get the actual name from server info
                    url = serverUrl.trimEnd('/'),
                    isConnected = true,
                    userId = authResult.user.id,
                    username = authResult.user.name,
                    accessToken = authResult.accessToken
                )
                
                _currentServer.value = server
                _isConnected.value = true
                
                ApiResult.Success(authResult)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Authentication failed"
                ApiResult.Error("Authentication failed: $errorBody")
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
    
    suspend fun getUserLibraries(): ApiResult<List<BaseItem>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated")
        }
        
        return try {
            val apiService = getApiService(server.url)
            val response = apiService.getUserViews(
                userId = server.userId,
                token = "MediaBrowser Token=\"${server.accessToken}\""
            )
            
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!.Items)
            } else {
                ApiResult.Error("Failed to load libraries: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
    
    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 100
    ): ApiResult<List<BaseItem>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated")
        }
        
        return try {
            val apiService = getApiService(server.url)
            val response = apiService.getUserItems(
                userId = server.userId,
                token = "MediaBrowser Token=\"${server.accessToken}\"",
                recursive = true,
                includeItemTypes = itemTypes,
                startIndex = startIndex,
                limit = limit
            )
            
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!.Items)
            } else {
                ApiResult.Error("Failed to load items: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}", e)
        }
    }
    
    suspend fun getRecentlyAdded(limit: Int = 20): ApiResult<List<BaseItem>> {
        return getLibraryItems(
            itemTypes = "Movie,Series,Episode,Audio",
            limit = limit
        )
    }
    
    suspend fun getFavorites(): ApiResult<List<BaseItem>> {
        val server = _currentServer.value
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated")
        }
        
        return try {
            val apiService = getApiService(server.url)
            val response = apiService.getUserItems(
                userId = server.userId,
                token = "MediaBrowser Token=\"${server.accessToken}\"",
                recursive = true,
                sortBy = "SortName"
            )
            
            if (response.isSuccessful && response.body() != null) {
                val favoriteItems = response.body()!!.Items.filter { 
                    it.UserData?.IsFavorite == true 
                }
                ApiResult.Success(favoriteItems)
            } else {
                ApiResult.Error("Failed to load favorites: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            ApiResult.Error("Network error: ${e.message}", e)
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
