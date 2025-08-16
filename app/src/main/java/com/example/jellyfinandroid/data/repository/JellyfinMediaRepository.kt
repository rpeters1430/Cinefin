package com.example.jellyfinandroid.data.repository

import com.example.jellyfinandroid.data.cache.JellyfinCache
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.data.repository.common.BaseJellyfinRepository
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
    clientFactory: com.example.jellyfinandroid.di.JellyfinClientFactory,
    cache: JellyfinCache,
) : BaseJellyfinRepository(authRepository, clientFactory, cache) {

    suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> = executeWithCache(
        operationName = "getUserLibraries",
        cacheKey = "user_libraries",
        cacheTtlMs = 60 * 60 * 1000L, // 1 hour
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val client = getClient(server.url, server.accessToken)
        val response = client.itemsApi.getItems(
            userId = userUuid,
            includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
        )
        response.content.items ?: emptyList()
    }

    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): ApiResult<List<BaseItemDto>> = execute {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val client = getClient(server.url, server.accessToken)
        val parent = parentId?.let { parseUuid(it, "parent") }
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
            parentId = parent,
            recursive = true,
            includeItemTypes = itemKinds,
            startIndex = startIndex,
            limit = limit,
        )
        response.content.items ?: emptyList()
    }

    suspend fun getRecentlyAdded(limit: Int = 20): ApiResult<List<BaseItemDto>> = executeWithCache(
        operationName = "getRecentlyAdded",
        cacheKey = "recently_added_$limit",
        cacheTtlMs = 30 * 60 * 1000L, // 30 minutes
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
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
                BaseItemKind.VIDEO,
            ),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            limit = limit,
        )
        response.content.items ?: emptyList()
    }

    suspend fun getRecentlyAddedByType(
        itemType: BaseItemKind,
        limit: Int = 20,
    ): ApiResult<List<BaseItemDto>> = executeWithRetry(
        operationName = "getRecentlyAddedByType_${itemType.name}",
        maxAttempts = 3,
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val client = getClient(server.url, server.accessToken)
        val response = client.itemsApi.getItems(
            userId = userUuid,
            recursive = true,
            includeItemTypes = listOf(itemType),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            limit = limit,
        )
        response.content.items ?: emptyList()
    }

    suspend fun getRecentlyAddedByTypes(limit: Int = 20): ApiResult<Map<String, List<BaseItemDto>>> = executeWithRetry(
        operationName = "getRecentlyAddedByTypes",
        maxAttempts = 3,
    ) {
        val types = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.EPISODE,
        )
        val result = mutableMapOf<String, List<BaseItemDto>>()
        for (type in types) {
            when (val res = getRecentlyAddedByType(type, limit)) {
                is ApiResult.Success -> if (res.data.isNotEmpty()) {
                    result[type.name] = res.data
                }
                else -> {}
            }
        }
        result
    }

    suspend fun getSeasonsForSeries(seriesId: String): ApiResult<List<BaseItemDto>> = execute {
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
        )
        response.content.items ?: emptyList()
    }

    /**
     * Refresh methods that force cache invalidation and fetch fresh data.
     */

    suspend fun refreshUserLibraries(): ApiResult<List<BaseItemDto>> = executeRefreshWithCache(
        operationName = "refreshUserLibraries",
        cacheKey = "user_libraries",
        cacheTtlMs = 60 * 60 * 1000L,
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
        val client = getClient(server.url, server.accessToken)
        val response = client.itemsApi.getItems(
            userId = userUuid,
            includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
        )
        response.content.items ?: emptyList()
    }

    suspend fun refreshRecentlyAdded(limit: Int = 20): ApiResult<List<BaseItemDto>> = executeRefreshWithCache(
        operationName = "refreshRecentlyAdded",
        cacheKey = "recently_added_$limit",
        cacheTtlMs = 30 * 60 * 1000L,
    ) {
        val server = validateServer()
        val userUuid = parseUuid(server.userId ?: "", "user")
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
                BaseItemKind.VIDEO,
            ),
            sortBy = listOf(ItemSortBy.DATE_CREATED),
            sortOrder = listOf(SortOrder.DESCENDING),
            limit = limit,
        )
        response.content.items ?: emptyList()
    }
}
