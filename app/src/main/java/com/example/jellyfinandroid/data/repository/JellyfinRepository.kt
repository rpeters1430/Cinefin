package com.example.jellyfinandroid.data.repository

import android.content.Context
import android.util.Log
import com.example.jellyfinandroid.BuildConfig
import com.example.jellyfinandroid.R
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.data.SecureCredentialManager
import com.example.jellyfinandroid.data.model.QuickConnectResult
import com.example.jellyfinandroid.data.model.QuickConnectState
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.data.repository.common.ErrorType
import com.example.jellyfinandroid.data.utils.RepositoryUtils
import com.example.jellyfinandroid.di.JellyfinClientFactory
import com.example.jellyfinandroid.ui.utils.ErrorHandler
import com.example.jellyfinandroid.ui.utils.OfflineManager
import com.example.jellyfinandroid.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.SortOrder
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepository @Inject constructor(
    private val clientFactory: JellyfinClientFactory,
    private val secureCredentialManager: SecureCredentialManager,
    @ApplicationContext private val context: Context,
    private val authRepository: JellyfinAuthRepository,
    private val streamRepository: JellyfinStreamRepository,
) {
    companion object {
        // ✅ PHASE 4: Use centralized constants
        private const val TOKEN_VALIDITY_DURATION_MS = Constants.TOKEN_VALIDITY_DURATION_MS

        // API retry constants
        private const val DEFAULT_MAX_RETRIES = Constants.MAX_RETRY_ATTEMPTS - 1 // Convert to retry count
        private const val RE_AUTH_DELAY_MS = Constants.RE_AUTH_DELAY_MS

        // Stream quality constants
        private const val DEFAULT_MAX_BITRATE = 140_000_000
        private const val DEFAULT_MAX_AUDIO_CHANNELS = 2

        // Image size constants
        private const val DEFAULT_IMAGE_MAX_HEIGHT = 400
        private const val DEFAULT_IMAGE_MAX_WIDTH = 400
        private const val BACKDROP_MAX_HEIGHT = 400
        private const val BACKDROP_MAX_WIDTH = 800

        // API pagination constants
        private const val DEFAULT_LIMIT = 100
        private const val DEFAULT_START_INDEX = 0
        private const val RECENTLY_ADDED_LIMIT = Constants.RECENTLY_ADDED_LIMIT
        private const val RECENTLY_ADDED_BY_TYPE_LIMIT = Constants.RECENTLY_ADDED_BY_TYPE_LIMIT
        private const val SEARCH_LIMIT = 50

        // Default codecs
        private const val DEFAULT_VIDEO_CODEC = "h264"
        private const val DEFAULT_AUDIO_CODEC = "aac"
        private const val DEFAULT_CONTAINER = "mp4"
    }

    // ===== STATE FLOWS - Delegated to JellyfinAuthRepository =====
    val currentServer: Flow<JellyfinServer?> = authRepository.currentServer
    val isConnected: Flow<Boolean> = authRepository.isConnected

    // Mutex to prevent race conditions in authentication
    private val authMutex = Mutex()

    // Helper function for debug logging that only logs in debug builds
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", message)
        }
    }

    // Helper function to get string resources
    private fun getString(resId: Int): String = context.getString(resId)

    private fun getClient(serverUrl: String, accessToken: String? = null): ApiClient {
        return clientFactory.getClient(serverUrl, accessToken)
    }

    // ✅ FIX: Helper method to get current authenticated client
    private fun getCurrentAuthenticatedClient(): ApiClient? {
        val currentServer = authRepository.getCurrentServer()
        return currentServer?.let {
            getClient(it.url, it.accessToken)
        }
    }

    // ✅ PHASE 4: Simplified error handling using centralized utilities
    private fun <T> handleExceptionSafely(e: Exception, defaultMessage: String = getString(R.string.error_occurred)): ApiResult.Error<T> {
        // Let cancellation exceptions bubble up instead of converting to ApiResult.Error
        if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
            throw e
        }
        return handleException(e, defaultMessage)
    }

    private fun <T> handleException(e: Exception, defaultMessage: String = getString(R.string.error_occurred)): ApiResult.Error<T> {
        val processedError = ErrorHandler.processError(e, operation = defaultMessage)

        // Log error analytics
        val currentServer = authRepository.getCurrentServer()
        ErrorHandler.logErrorAnalytics(
            error = processedError,
            operation = defaultMessage,
            userId = currentServer?.userId,
            serverUrl = currentServer?.url,
        )

        return ApiResult.Error(
            message = processedError.userMessage,
            cause = e,
            errorType = processedError.errorType,
        )
    }

    // ===== AUTHENTICATION METHODS - Delegated to JellyfinAuthRepository =====

    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> =
        authRepository.testServerConnection(serverUrl)

    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> {
        // Delegate to auth repository
        return authRepository.authenticateUser(serverUrl, username, password)
    }

    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> =
        authRepository.initiateQuickConnect(serverUrl)

    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> =
        authRepository.getQuickConnectState(serverUrl, secret)

    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String,
    ): ApiResult<AuthenticationResult> =
        authRepository.authenticateWithQuickConnect(serverUrl, secret)

    // ===== LIBRARY METHODS - Simplified for better maintainability =====

    suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Validate token before making requests
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "getUserLibraries: Token expired, attempting proactive refresh")
            if (!reAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        return executeWithAuthRetry("getUserLibraries") {
            // Use current server from auth repository for consistency
            val server = authRepository.getCurrentServer()
            if (server?.accessToken == null || server.userId == null) {
                return@executeWithAuthRetry ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
            }

            val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
            if (userUuid == null) {
                return@executeWithAuthRetry ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
            }

            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER),
            )
            ApiResult.Success(response.content.items ?: emptyList())
        }
    }

    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = DEFAULT_START_INDEX,
        limit: Int = DEFAULT_LIMIT,
    ): ApiResult<List<BaseItemDto>> {
        val server = authRepository.getCurrentServer()
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
                limit = limit,
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to load items: ${e.message}", e, errorType)
        }
    }

    suspend fun getRecentlyAdded(limit: Int = RECENTLY_ADDED_LIMIT): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Validate token before making requests
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "getRecentlyAdded: Token expired, attempting proactive refresh")
            if (!reAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            logDebug("getRecentlyAdded: Requesting $limit items from server")

            val items = executeWithRetry {
                // ✅ FIX: Always get current server state inside the retry closure to use fresh token
                val currentServer = authRepository.getCurrentServer()
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
                        BaseItemKind.VIDEO,
                    ),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit,
                )
                response.content.items ?: emptyList()
            }

            logDebug("getRecentlyAdded: Retrieved ${items.size} items")

            // Log details of each item
            items.forEachIndexed { index, item ->
                val dateFormatted = item.dateCreated?.toString() ?: "Unknown date"
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "getRecentlyAdded[$index]: ${item.type} - '${item.name}' (Created: $dateFormatted)")
                }
            }

            ApiResult.Success(items)
        } catch (e: Exception) {
            // ✅ FIX: Let cancellation exceptions bubble up instead of converting to ApiResult.Error
            if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                throw e
            }

            Log.e("JellyfinRepository", "getRecentlyAdded: Failed to load items", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to load recently added items: ${e.message}", e, errorType)
        }
    }

    private suspend fun reAuthenticate(): Boolean = authMutex.withLock {
        val server = authRepository.getCurrentServer() ?: return@withLock false

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "reAuthenticate: Starting re-authentication for user ${server.username} on ${server.url}")
        }

        try {
            // Clear any cached clients before re-authenticating
            clientFactory.invalidateClient()

            // Get saved password for the current server and username
            val savedPassword = secureCredentialManager.getPassword(server.url, server.username ?: "")
            if (savedPassword == null) {
                Log.w("JellyfinRepository", "reAuthenticate: No saved password found for user ${server.username}")
                // If we can't re-authenticate, logout the user
                logout()
                return@withLock false
            }

            if (BuildConfig.DEBUG) {
                Log.d("JellyfinRepository", "reAuthenticate: Found saved credentials, attempting authentication")
            }

            // Re-authenticate using saved credentials - delegate to auth repository for consistency
            when (val authResult = authRepository.authenticateUser(server.url, server.username ?: "", savedPassword)) {
                is ApiResult.Success -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinRepository", "reAuthenticate: Successfully re-authenticated user ${server.username}")
                    }
                    // Update auth repository state
                    val updatedServer = server.copy(
                        accessToken = authResult.data.accessToken,
                        loginTimestamp = System.currentTimeMillis(),
                    )
                    authRepository.updateCurrentServer(updatedServer)

                    // Clear client factory again to ensure fresh token is used
                    clientFactory.invalidateClient()

                    return@withLock true
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinRepository", "reAuthenticate: Failed to re-authenticate: ${authResult.message}")
                    // If re-authentication fails, logout the user
                    logout()
                    return@withLock false
                }
                is ApiResult.Loading -> {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinRepository", "reAuthenticate: Authentication in progress")
                    }
                    return@withLock false
                }
            }
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "reAuthenticate: Exception during re-authentication", e)
            // If there's an exception during re-auth, logout to prevent further errors
            logout()
            return@withLock false
        }
    }

    suspend fun logout() {
        authRepository.logout()
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        operation: suspend () -> T,
    ): T {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e

                // ✅ FIX: Don't retry if the job was cancelled (navigation/lifecycle cancellation)
                if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinRepository", "Operation was cancelled, not retrying")
                    }
                    throw e
                }

                // If it's a 401 error and we have saved credentials, try to re-authenticate
                val is401Error = RepositoryUtils.is401Error(e)
                if (is401Error && attempt < maxRetries) {
                    Log.w("JellyfinRepository", "Got 401 error on attempt ${attempt + 1}, attempting to re-authenticate")

                    // Try to re-authenticate with proper synchronization
                    if (reAuthenticate()) {
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinRepository", "Re-authentication successful, retrying operation")
                        }
                        // Additional delay to ensure token propagation
                        kotlinx.coroutines.delay(RE_AUTH_DELAY_MS)
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
        operation: suspend () -> ApiResult<T>,
    ): ApiResult<T> {
        for (attempt in 0..maxRetries) {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "$operationName: Attempt ${attempt + 1}/${maxRetries + 1}")
                }

                val result = operation()

                when (result) {
                    is ApiResult.Success -> {
                        if (attempt > 0) {
                            if (BuildConfig.DEBUG) {
                                Log.i("JellyfinRepository", "$operationName: Succeeded on attempt ${attempt + 1}")
                            }
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
                                if (BuildConfig.DEBUG) {
                                    Log.d("JellyfinRepository", "$operationName: Re-authentication successful, retrying")
                                }
                                // Additional delay for token propagation
                                kotlinx.coroutines.delay(RE_AUTH_DELAY_MS)
                                continue
                            } else {
                                Log.w("JellyfinRepository", "$operationName: Re-authentication failed")
                                return result
                            }
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.d("JellyfinRepository", "$operationName: Error type ${result.errorType} not retryable or max attempts reached")
                            }
                            return result
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle direct exceptions from the operation
                if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinRepository", "$operationName: Operation cancelled on attempt ${attempt + 1}")
                    }
                    throw e
                }

                val errorType = RepositoryUtils.getErrorType(e)
                Log.w("JellyfinRepository", "$operationName: Exception on attempt ${attempt + 1}: ${e.message} (type: $errorType)")

                // Check for 401 in exception and retry with re-authentication
                if (errorType == ErrorType.UNAUTHORIZED && attempt < maxRetries) {
                    Log.w("JellyfinRepository", "$operationName: Got 401 exception, attempting re-authentication")

                    if (reAuthenticate()) {
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinRepository", "$operationName: Re-authentication successful, retrying")
                        }
                        // Additional delay for token propagation
                        kotlinx.coroutines.delay(RE_AUTH_DELAY_MS)
                        continue
                    } else {
                        Log.w("JellyfinRepository", "$operationName: Re-authentication failed")
                        return handleExceptionSafely(e, operationName)
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinRepository", "$operationName: Exception type $errorType not retryable or max attempts reached")
                    }
                    return handleExceptionSafely(e, operationName)
                }
            }
        }

        return ApiResult.Error("$operationName failed after $maxRetries retry attempts", errorType = ErrorType.UNKNOWN)
    }

    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = RECENTLY_ADDED_BY_TYPE_LIMIT): ApiResult<List<BaseItemDto>> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getRecentlyAddedByType: Requesting $limit items of type $itemType")
        }

        return executeWithAuthRetry("getRecentlyAddedByType") {
            val currentServer = authRepository.getCurrentServer()
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
                limit = limit,
            )
            val items = response.content.items ?: emptyList()

            if (BuildConfig.DEBUG) {
                Log.d("JellyfinRepository", "getRecentlyAddedByType: Retrieved ${items.size} items of type $itemType")
            }

            // Log details of each item
            items.forEachIndexed { index, item ->
                val dateFormatted = item.dateCreated?.toString() ?: "Unknown date"
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "getRecentlyAddedByType[$itemType][$index]: '${item.name}' (Created: $dateFormatted)")
                }
            }

            ApiResult.Success(items)
        }
    }

    suspend fun getRecentlyAddedFromLibrary(
        libraryId: String,
        limit: Int = 10,
    ): ApiResult<List<BaseItemDto>> {
        val server = authRepository.getCurrentServer()
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
                limit = limit,
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            handleException(e, "Failed to load recently added items from library")
        }
    }

    suspend fun getRecentlyAddedByTypes(limit: Int = RECENTLY_ADDED_BY_TYPE_LIMIT): ApiResult<Map<String, List<BaseItemDto>>> {
        val contentTypes = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.EPISODE,
            BaseItemKind.AUDIO,
            BaseItemKind.BOOK,
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.VIDEO,
        )

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Starting to fetch items for ${contentTypes.size} content types")
        }
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
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Added ${result.data.size} items to category '$typeName'")
                        }
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: No items found for type $contentType")
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinRepository", "getRecentlyAddedByTypes: Failed to load $contentType: ${result.message}")
                    // Continue with other types even if one fails
                }
                else -> { /* Loading state not relevant here */ }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Completed with ${results.size} categories: ${results.keys.joinToString(", ")}")
        }
        return ApiResult.Success(results)
    }

    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> {
        val server = authRepository.getCurrentServer()
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
                filters = listOf(ItemFilter.IS_FAVORITE),
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
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
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getSeasonsForSeries: Fetching seasons for seriesId=$seriesId")
        }
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
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.DATE_CREATED, ItemFields.OVERVIEW),
            )
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinRepository", "getSeasonsForSeries: Successfully fetched ${response.content.items?.size ?: 0} seasons for seriesId=$seriesId")
            }
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "getSeasonsForSeries: Failed to fetch seasons for seriesId=$seriesId", e)
            val errorType = RepositoryUtils.getErrorType(e)
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
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getEpisodesForSeason: Fetching episodes for seasonId=$seasonId")
        }
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
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.DATE_CREATED, ItemFields.OVERVIEW),
            )
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinRepository", "getEpisodesForSeason: Successfully fetched ${response.content.items?.size ?: 0} episodes for seasonId=$seasonId")
            }
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "getEpisodesForSeason: Failed to fetch episodes for seasonId=$seasonId", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to load episodes: ${e.message}", e, errorType)
        }
    }

    private suspend fun getItemDetailsById(itemId: String, itemTypeName: String): ApiResult<BaseItemDto> {
        val server = authRepository.getCurrentServer()
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
                limit = 1,
                fields = listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.GENRES,
                    ItemFields.PEOPLE,
                    ItemFields.MEDIA_SOURCES,
                    ItemFields.MEDIA_STREAMS,
                    ItemFields.DATE_CREATED,
                    ItemFields.STUDIOS,
                    ItemFields.TAGS,
                    ItemFields.CHAPTERS,
                ),
            )
            val item = response.content.items?.firstOrNull()
            if (item != null) {
                ApiResult.Success(item)
            } else {
                ApiResult.Error("$itemTypeName not found", errorType = ErrorType.NOT_FOUND)
            }
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
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

    // ===== SEARCH METHODS - Simplified implementation =====

    suspend fun searchItems(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        limit: Int = SEARCH_LIMIT,
    ): ApiResult<List<BaseItemDto>> {
        if (query.isBlank()) {
            return ApiResult.Success(emptyList())
        }

        val server = authRepository.getCurrentServer()
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
                    BaseItemKind.AUDIO_BOOK,
                ),
                limit = limit,
            )
            ApiResult.Success(response.content.items ?: emptyList())
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Search failed: ${e.message}", e, errorType)
        }
    }

    // ===== IMAGE METHODS - Delegated to JellyfinStreamRepository =====

    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String? =
        streamRepository.getImageUrl(itemId, imageType, tag)

    fun getSeriesImageUrl(item: BaseItemDto): String? =
        streamRepository.getSeriesImageUrl(item)

    fun getBackdropUrl(item: BaseItemDto): String? =
        streamRepository.getBackdropUrl(item)

    private suspend fun <T> safeApiCall(operation: suspend () -> T): T {
        try {
            return operation()
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Log only non-sensitive information for debugging
                Log.w("JellyfinRepository", "401 Unauthorized detected. Server ID: ${authRepository.getCurrentServer()?.id?.take(8)}, Endpoint path: ${e.response()?.raw()?.request?.url?.encodedPath}")
                logout() // Clear session and redirect to login
                throw e
            }
            throw e
        }
    }

    private fun isTokenExpired(): Boolean {
        val server = authRepository.getCurrentServer() ?: return true
        val loginTimestamp = server.loginTimestamp ?: return true
        val currentTime = System.currentTimeMillis()

        // Consider token expired after 50 minutes (10 minutes before actual expiry)
        // This gives us time to refresh before hitting 401 errors
        val isExpired = (currentTime - loginTimestamp) > TOKEN_VALIDITY_DURATION_MS

        if (isExpired) {
            // Log only non-sensitive information for debugging
            Log.w("JellyfinRepository", "Token expired. Server ID: ${server.id?.take(8)}")
        }

        return isExpired
    }

    private suspend fun validateAndRefreshToken() {
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "Token expired, attempting proactive refresh")
            if (reAuthenticate()) {
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "Proactive token refresh successful")
                }
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
        val server = authRepository.getCurrentServer()
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
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to toggle favorite: ${e.message}", e, errorType)
        }
    }

    suspend fun markAsWatched(itemId: String): ApiResult<Boolean> {
        val server = authRepository.getCurrentServer()
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
            client.playStateApi.markPlayedItem(itemId = itemUuid, userId = userUuid)
            ApiResult.Success(true)
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to mark as watched: ${e.message}", e, errorType)
        }
    }

    suspend fun markAsUnwatched(itemId: String): ApiResult<Boolean> {
        val server = authRepository.getCurrentServer()
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
            client.playStateApi.markUnplayedItem(itemId = itemUuid, userId = userUuid)
            ApiResult.Success(true)
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to mark as unwatched: ${e.message}", e, errorType)
        }
    }

    suspend fun deleteItem(itemId: String): ApiResult<Boolean> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            client.libraryApi.deleteItem(itemId = itemUuid)
            ApiResult.Success(true)
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to delete item: ${e.message}", e, errorType)
        }
    }

    /**
     * Checks if the currently authenticated user has administrator privileges
     * or permission to delete content.
     */
    private suspend fun hasAdminDeletePermission(server: JellyfinServer): ApiResult<Boolean> {
        val userId = server.userId ?: return ApiResult.Success(false)
        return try {
            val userUuid = parseUuid(userId, "user")
            val client = getClient(server.url, server.accessToken)
            val user = client.userApi.getCurrentUser().content
            ApiResult.Success(user.policy?.isAdministrator == true || user.policy?.enableContentDeletion == true)
        } catch (e: Exception) {
            Log.e("JellyfinRepository", "Failed to verify admin permissions: ${e.message}", e)
            ApiResult.Error("Failed to verify admin permissions: ${e.message}", e, RepositoryUtils.getErrorType(e))
        }
    }

    /**
     * Deletes an item only if the current user has administrator permissions.
     */
    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        val adminPermissionResult = hasAdminDeletePermission(server)
        if (adminPermissionResult is ApiResult.Error) {
            return adminPermissionResult
        }
        if (adminPermissionResult is ApiResult.Success && !adminPermissionResult.data) {
            return ApiResult.Error("Administrator permissions required", errorType = ErrorType.FORBIDDEN)
        }

        return try {
            val client = getClient(server.url, server.accessToken)
            client.libraryApi.deleteItem(itemId = itemUuid)
            ApiResult.Success(true)
        } catch (e: Exception) {
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error("Failed to delete item: ${e.message}", e, errorType)
        }
    }

    // ===== STREAMING METHODS - Delegated to JellyfinStreamRepository =====

    fun getStreamUrl(itemId: String): String? =
        streamRepository.getStreamUrl(itemId)

    fun getTranscodedStreamUrl(
        itemId: String,
        maxBitrate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        videoCodec: String = DEFAULT_VIDEO_CODEC,
        audioCodec: String = DEFAULT_AUDIO_CODEC,
        container: String = DEFAULT_CONTAINER,
    ): String? =
        streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = maxBitrate,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            container = container,
        )

    fun getHlsStreamUrl(itemId: String): String? =
        streamRepository.getHlsStreamUrl(itemId)

    fun getDashStreamUrl(itemId: String): String? =
        streamRepository.getDashStreamUrl(itemId)

    fun getCurrentServer(): JellyfinServer? = authRepository.getCurrentServer()

    fun isUserAuthenticated(): Boolean = authRepository.isUserAuthenticated()

    fun getDownloadUrl(itemId: String): String? =
        streamRepository.getDownloadUrl(itemId)

    fun getDirectStreamUrl(itemId: String, container: String? = null): String? =
        streamRepository.getDirectStreamUrl(itemId, container)

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

    // ✅ PHASE 4: Utility methods replaced with centralized utilities
    private fun validateServer(): JellyfinServer = RepositoryUtils.validateServer(authRepository.getCurrentServer())
    private fun parseUuid(id: String, idType: String): UUID = RepositoryUtils.parseUuid(id, idType)
}
