package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.common.ApiParameterValidator
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.BaseJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.LibraryHealthChecker
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository containing media-related operations that were previously
 * part of the large [JellyfinRepository]. The implementations here are
 * intentionally lightweight and focused on simple request execution.
 */
@Singleton
class JellyfinMediaRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    clientFactory: com.rpeters.jellyfin.di.JellyfinClientFactory,
    cache: JellyfinCache,
    private val healthChecker: LibraryHealthChecker,
) : BaseJellyfinRepository(authRepository, clientFactory, cache) {

    // Helper function to get default item types for a collection
    private fun getDefaultTypesForCollection(collectionType: String?): List<BaseItemKind>? = when (collectionType?.lowercase()) {
        "movies" -> listOf(BaseItemKind.MOVIE)
        "tvshows" -> listOf(BaseItemKind.SERIES)
        "music" -> listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO, BaseItemKind.MUSIC_ARTIST)
        "homevideos" -> null // Don't specify types for home videos - let server decide
        "photos" -> listOf(BaseItemKind.PHOTO)
        "books" -> listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK)
        else -> null
    }

    suspend fun getUserLibraries(forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val operation: suspend () -> List<BaseItemDto> = {
            val server = validateServer()
            val userUuid = parseUuid(server.userId ?: "", "user")
            val client = getClient(server.url, server.accessToken)
            val response = executeWithTokenRefresh {
                client.itemsApi.getItems(
                    userId = userUuid,
                    includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
                )
            }
            response.content.items ?: emptyList()
        }

        return if (forceRefresh) {
            executeRefreshWithCache(
                operationName = "getUserLibraries",
                cacheKey = "user_libraries",
                cacheTtlMs = 60 * 60 * 1000L, // 1 hour
                block = operation,
            )
        } else {
            executeWithCache(
                operationName = "getUserLibraries",
                cacheKey = "user_libraries",
                cacheTtlMs = 60 * 60 * 1000L, // 1 hour
                block = operation,
            )
        }
    }

    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        collectionType: String? = null,
    ): ApiResult<List<BaseItemDto>> = execute("getLibraryItems") {
        // ✅ COMPREHENSIVE FIX: Use centralized parameter validation
        val validatedParams = ApiParameterValidator.validateLibraryParams(
            parentId = parentId,
            itemTypes = itemTypes,
            startIndex = startIndex,
            limit = limit,
            collectionType = collectionType,
        ) ?: throw IllegalArgumentException("Invalid API parameters provided")

        // Check if library is blocked due to repeated failures
        if (validatedParams.parentId != null && healthChecker.isLibraryBlocked(validatedParams.parentId)) {
            android.util.Log.w(
                "JellyfinMediaRepository",
                "Library ${validatedParams.parentId} is blocked due to repeated failures, returning empty list",
            )
            return@execute emptyList()
        }

        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val client = getClient(server.url, server.accessToken)

        // Parse parentId if provided
        val parent = validatedParams.parentId?.let { parseUuid(it, "parent") }

        // Parse item types from validated string
        val itemKinds = validatedParams.itemTypes?.split(",")?.mapNotNull { type ->
            when (type.trim()) {
                "Movie" -> BaseItemKind.MOVIE
                "Series" -> BaseItemKind.SERIES
                "Episode" -> BaseItemKind.EPISODE
                "Audio" -> BaseItemKind.AUDIO
                "MusicAlbum" -> BaseItemKind.MUSIC_ALBUM
                "MusicArtist" -> BaseItemKind.MUSIC_ARTIST
                "Book" -> BaseItemKind.BOOK
                "AudioBook" -> BaseItemKind.AUDIO_BOOK
                "Video" -> BaseItemKind.VIDEO
                "Photo" -> BaseItemKind.PHOTO
                else -> null
            }
        }

        android.util.Log.d(
            "JellyfinMediaRepository",
            "Making validated API call with parentId=${parent?.toString()}, itemTypes=${itemKinds?.joinToString { it.name }}, startIndex=${validatedParams.startIndex}, limit=${validatedParams.limit}",
        )

        try {
            val response = executeWithTokenRefresh {
                client.itemsApi.getItems(
                    userId = userUuid,
                    parentId = parent,
                    recursive = true,
                    includeItemTypes = itemKinds,
                    startIndex = validatedParams.startIndex,
                    limit = validatedParams.limit,
                )
            }
            val items = response.content.items ?: emptyList()

            // Report success to health checker
            validatedParams.parentId?.let { libraryId ->
                healthChecker.reportSuccess(libraryId)
            }

            items
        } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
            val errorMsg = try { e.message } catch (_: Throwable) { "Bad Request" }
            android.util.Log.e(
                "JellyfinMediaRepository",
                "getLibraryItems ${e.status}: ${errorMsg ?: e.message}",
            )

            // If we get a 401, the token refresh should have already been handled by executeWithTokenRefresh
            if (e.message?.contains("401") == true) {
                android.util.Log.w(
                    "JellyfinMediaRepository",
                    "HTTP 401 error after token refresh attempt, authentication may have permanently failed",
                )

                // Report failure to health checker
                validatedParams.parentId?.let { libraryId ->
                    healthChecker.reportFailure(libraryId, "Authentication failed")
                }

                throw Exception("Authentication failed: Token refresh was unsuccessful")
            }

            // If we get a 400, try multiple fallback strategies
            if (e.message?.contains("400") == true) {
                android.util.Log.w(
                    "JellyfinMediaRepository",
                    "HTTP 400 error detected, attempting fallback strategies for parentId=$parentId, collectionType=$collectionType",
                )

                // Strategy 1: Try collection-type defaults if we had explicit types
                if (!collectionType.isNullOrBlank() && !itemTypes.isNullOrBlank()) {
                    try {
                        val fallbackTypes = getDefaultTypesForCollection(collectionType)
                        android.util.Log.d(
                            "JellyfinMediaRepository",
                            "Fallback strategy 1: Using collection type defaults: ${fallbackTypes?.joinToString()}",
                        )

                        val response = executeWithTokenRefresh {
                            client.itemsApi.getItems(
                                userId = userUuid,
                                parentId = parent,
                                recursive = true,
                                includeItemTypes = fallbackTypes,
                                startIndex = validatedParams.startIndex,
                                limit = validatedParams.limit,
                            )
                        }
                        android.util.Log.d(
                            "JellyfinMediaRepository",
                            "Fallback strategy 1 succeeded: ${response.content.items?.size ?: 0} items",
                        )
                        return@execute response.content.items ?: emptyList()
                    } catch (fallbackException: Exception) {
                        android.util.Log.w(
                            "JellyfinMediaRepository",
                            "Fallback strategy 1 failed: ${fallbackException.message}",
                        )
                    }
                }

                // Strategy 2: Try without any includeItemTypes (let server decide)
                try {
                    android.util.Log.d(
                        "JellyfinMediaRepository",
                        "Fallback strategy 2: Requesting without includeItemTypes filter",
                    )

                    val response = executeWithTokenRefresh {
                        client.itemsApi.getItems(
                            userId = userUuid,
                            parentId = parent,
                            recursive = true,
                            includeItemTypes = null, // Let server return all types
                            startIndex = validatedParams.startIndex,
                            limit = validatedParams.limit,
                        )
                    }
                    android.util.Log.d(
                        "JellyfinMediaRepository",
                        "Fallback strategy 2 succeeded: ${response.content.items?.size ?: 0} items",
                    )
                    return@execute response.content.items ?: emptyList()
                } catch (fallbackException2: Exception) {
                    android.util.Log.w(
                        "JellyfinMediaRepository",
                        "Fallback strategy 2 also failed: ${fallbackException2.message}",
                    )
                }

                // Strategy 3: Try without parentId (library-wide search)
                if (parent != null) {
                    try {
                        android.util.Log.d(
                            "JellyfinMediaRepository",
                            "Fallback strategy 3: Requesting without parentId constraint",
                        )

                        val response = executeWithTokenRefresh {
                            client.itemsApi.getItems(
                                userId = userUuid,
                                parentId = null, // Remove library constraint
                                recursive = true,
                                includeItemTypes = itemKinds,
                                startIndex = validatedParams.startIndex,
                                limit = validatedParams.limit,
                            )
                        }
                        android.util.Log.d(
                            "JellyfinMediaRepository",
                            "Fallback strategy 3 succeeded: ${response.content.items?.size ?: 0} items",
                        )
                        return@execute response.content.items ?: emptyList()
                    } catch (fallbackException3: Exception) {
                        android.util.Log.w(
                            "JellyfinMediaRepository",
                            "Fallback strategy 3 also failed: ${fallbackException3.message}",
                        )
                    }
                }

                // Strategy 4: Return empty list as graceful degradation
                android.util.Log.w(
                    "JellyfinMediaRepository",
                    "All fallback strategies failed for library ${validatedParams.parentId}, returning empty list",
                )

                // Report failure to health checker
                validatedParams.parentId?.let { libraryId ->
                    healthChecker.reportFailure(libraryId, errorMsg ?: "HTTP 400 error")
                }

                return@execute emptyList()
            }

            // Report failure to health checker for any unhandled exceptions
            validatedParams.parentId?.let { libraryId ->
                healthChecker.reportFailure(libraryId, errorMsg ?: "HTTP error")
            }

            throw e
        }
    }

    suspend fun getRecentlyAdded(limit: Int = 50, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val operation: suspend () -> List<BaseItemDto> = {
            val server = validateServer()
            val userUuid = parseUuid(server.userId ?: "", "user")
            val client = getClient(server.url, server.accessToken)

            val response = executeWithTokenRefresh {
                client.itemsApi.getItems(
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
                        BaseItemKind.VIDEO,
                    ),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit,
                )
            }
            response.content.items ?: emptyList()
        }

        return if (forceRefresh) {
            executeRefreshWithCache(
                operationName = "getRecentlyAdded",
                cacheKey = "recently_added",
                cacheTtlMs = 15 * 60 * 1000L, // 15 minutes - improved cache efficiency
                block = operation,
            )
        } else {
            executeWithCache(
                operationName = "getRecentlyAdded",
                cacheKey = "recently_added",
                cacheTtlMs = 15 * 60 * 1000L, // 15 minutes - improved cache efficiency
                block = operation,
            )
        }
    }

    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = 20, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val operation: suspend () -> List<BaseItemDto> = {
            val server = validateServer()
            val userUuid = parseUuid(server.userId ?: "", "user")
            val client = getClient(server.url, server.accessToken)

            val response = executeWithTokenRefresh {
                client.itemsApi.getItems(
                    userId = userUuid,
                    recursive = true,
                    includeItemTypes = listOf(itemType),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit,
                )
            }
            response.content.items ?: emptyList()
        }

        val cacheKey = "recently_added_${itemType.name.lowercase()}"

        return if (forceRefresh) {
            executeRefreshWithCache(
                operationName = "getRecentlyAddedByType",
                cacheKey = cacheKey,
                cacheTtlMs = 15 * 60 * 1000L, // 15 minutes - improved cache efficiency
                block = operation,
            )
        } else {
            executeWithCache(
                operationName = "getRecentlyAddedByType",
                cacheKey = cacheKey,
                cacheTtlMs = 15 * 60 * 1000L, // 15 minutes - improved cache efficiency
                block = operation,
            )
        }
    }

    suspend fun getMovieDetails(movieId: String): ApiResult<BaseItemDto> = execute("getMovieDetails") {
        getItemDetailsById(movieId, "movie")
    }

    suspend fun getSeriesDetails(seriesId: String): ApiResult<BaseItemDto> = execute("getSeriesDetails") {
        getItemDetailsById(seriesId, "series")
    }

    suspend fun getEpisodeDetails(episodeId: String): ApiResult<BaseItemDto> = execute("getEpisodeDetails") {
        getItemDetailsById(episodeId, "episode")
    }

    suspend fun getSeasonsForSeries(seriesId: String): ApiResult<List<BaseItemDto>> = executeWithCache(
        operationName = "getSeasonsForSeries",
        cacheKey = "seasons_$seriesId",
        cacheTtlMs = 30 * 60 * 1000L,
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val seriesUuid = parseUuid(seriesId, "series")
        val client = getClient(server.url, server.accessToken)

        val response = client.itemsApi.getItems(
            userId = userUuid,
            parentId = seriesUuid,
            includeItemTypes = listOf(BaseItemKind.SEASON),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.MEDIA_SOURCES,
                org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
            ),
        )
        response.content.items ?: emptyList()
    }

    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>> = executeWithCache(
        operationName = "getEpisodesForSeason",
        cacheKey = "episodes_$seasonId",
        cacheTtlMs = 30 * 60 * 1000L,
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val seasonUuid = parseUuid(seasonId, "season")
        val client = getClient(server.url, server.accessToken)

        val response = client.itemsApi.getItems(
            userId = userUuid,
            parentId = seasonUuid,
            includeItemTypes = listOf(BaseItemKind.EPISODE),
            sortBy = listOf(ItemSortBy.INDEX_NUMBER),
            sortOrder = listOf(SortOrder.ASCENDING),
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.MEDIA_SOURCES,
                org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
            ),
        )
        response.content.items ?: emptyList()
    }

    private suspend fun getItemDetailsById(itemId: String, itemTypeName: String): BaseItemDto {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val itemUuid = parseUuid(itemId, itemTypeName)
        val client = getClient(server.url, server.accessToken)

        val response = client.itemsApi.getItems(
            userId = userUuid,
            ids = listOf(itemUuid),
            limit = 1,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.GENRES,
                org.jellyfin.sdk.model.api.ItemFields.PEOPLE,
                org.jellyfin.sdk.model.api.ItemFields.MEDIA_SOURCES,
                org.jellyfin.sdk.model.api.ItemFields.MEDIA_STREAMS,
                org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                org.jellyfin.sdk.model.api.ItemFields.STUDIOS,
                org.jellyfin.sdk.model.api.ItemFields.TAGS,
                org.jellyfin.sdk.model.api.ItemFields.CHAPTERS,
            ),
        )

        return response.content.items?.firstOrNull()
            ?: throw IllegalStateException("$itemTypeName not found")
    }

    suspend fun logHttpError(requestUrl: String, status: Int, body: String?) {
        Log.e("JellyfinMediaRepository", "HTTP error for $requestUrl\n$status ${body.orEmpty()}")
    }
}
