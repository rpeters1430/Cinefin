package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.BaseJellyfinRepository
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
) : BaseJellyfinRepository(authRepository, clientFactory, cache) {

    suspend fun getUserLibraries(forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val operation: suspend () -> List<BaseItemDto> = {
            val server = validateServer()
            val userUuid = parseUuid(server.userId ?: "", "user")
            val client = getClient(server.url, server.accessToken)
            val response = client.itemsApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
            )
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
    ): ApiResult<List<BaseItemDto>> = execute("getLibraryItems") {
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

    suspend fun getRecentlyAdded(limit: Int = 50, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val operation: suspend () -> List<BaseItemDto> = {
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
}
