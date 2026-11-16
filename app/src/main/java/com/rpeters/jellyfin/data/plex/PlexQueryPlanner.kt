package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import com.rpeters.jellyfin.utils.SecureLogger

/** Input parameters to planning a query against Plex Media Server. */
data class PlexQueryParams(
    val parentId: String? = null,
    val includeItemTypes: List<String> = emptyList(),
    val recursive: Boolean = false,
    val sortBy: List<String> = emptyList(),
    val sortOrder: String? = null,
    val startIndex: Int? = null,
    val limit: Int? = null,
    val searchTerm: String? = null,
    val filters: List<String> = emptyList(),
    val ids: List<String> = emptyList(),
    val personIds: List<String> = emptyList(),
    val artistIds: List<String> = emptyList(),
    val albumArtistIds: List<String> = emptyList(),
)

/** The planned Plex request path, query parameters, and static empty flag. */
data class PlexQueryPlan(
    val path: String,
    val queryParameters: Map<String, Any?> = emptyMap(),
    val isStaticEmpty: Boolean = false,
)

/**
 * Translates Jellyfin SDK `libraryApi.getItems` queries into Plex-native endpoints (KD4).
 */
object PlexQueryPlanner {
    private const val TAG = "PlexQueryPlanner"

    const val TYPE_MOVIE = 1
    const val TYPE_SERIES = 2
    const val TYPE_SEASON = 3
    const val TYPE_EPISODE = 4

    const val HEADER_START = "X-Plex-Container-Start"
    const val HEADER_SIZE = "X-Plex-Container-Size"

    fun plan(params: PlexQueryParams): PlexQueryPlan {
        // 1. Favorites: Plex has no per-item favorites (KD7).
        if (params.filters.any { it.equals("IsFavorite", ignoreCase = true) }) {
            return PlexQueryPlan(path = "", isStaticEmpty = true)
        }

        // 2. Music queries: out of scope until music phase.
        if (params.artistIds.isNotEmpty() || params.albumArtistIds.isNotEmpty()) {
            SecureLogger.w(TAG, "Music queries are not supported yet on Plex")
            return PlexQueryPlan(path = "", isStaticEmpty = true)
        }

        val paging = mapPaging(params.startIndex, params.limit)

        // 3. Search query: /hubs/search
        if (!params.searchTerm.isNullOrBlank()) {
            val queryMap = mutableMapOf<String, Any?>("query" to params.searchTerm)
            queryMap.putAll(paging)
            return PlexQueryPlan(path = "/hubs/search", queryParameters = queryMap)
        }

        // 4. Continue watching / Resumable: /hubs/continueWatching
        if (params.filters.any { it.equals("IsResumable", ignoreCase = true) }) {
            return PlexQueryPlan(path = "/hubs/continueWatching", queryParameters = paging)
        }

        // 5. Explicit IDs list: /library/metadata/{ids}
        if (params.ids.isNotEmpty()) {
            val decodedIds = params.ids.joinToString(",") { ServerIdCodec.decode(it) }
            return PlexQueryPlan(path = "/library/metadata/$decodedIds", queryParameters = paging)
        }

        val isDateCreatedDesc = params.sortBy.any { it.equals("DateCreated", ignoreCase = true) } &&
            params.sortOrder.equals("Descending", ignoreCase = true)

        // 6. Recently added globally (no parent): /library/recentlyAdded
        if (isDateCreatedDesc && params.parentId == null) {
            return PlexQueryPlan(path = "/library/recentlyAdded", queryParameters = paging)
        }

        // 7. Person / Actor filter: /library/sections/{id}/all?actor={tagId}
        if (params.personIds.isNotEmpty()) {
            val actorTagId = ServerIdCodec.decode(params.personIds.first())
            val sectionId = params.parentId?.let { ServerIdCodec.decode(it) } ?: "all"
            val queryMap = mutableMapOf<String, Any?>("actor" to actorTagId)
            queryMap.putAll(paging)
            return PlexQueryPlan(path = "/library/sections/$sectionId/all", queryParameters = queryMap)
        }

        // 8. Queries with parentId
        if (params.parentId != null) {
            val decodedParent = ServerIdCodec.decode(params.parentId)
            val hasSeason = params.includeItemTypes.any { it.equals("Season", ignoreCase = true) }
            val hasEpisode = params.includeItemTypes.any { it.equals("Episode", ignoreCase = true) }

            // Parent is series, requesting seasons -> /library/metadata/{id}/children
            if (hasSeason) {
                return PlexQueryPlan(path = "/library/metadata/$decodedParent/children", queryParameters = paging)
            }

            // Parent is series, requesting all episodes -> /library/metadata/{id}/allLeaves
            if (hasEpisode && params.recursive) {
                return PlexQueryPlan(path = "/library/metadata/$decodedParent/allLeaves", queryParameters = paging)
            }

            // Parent is season, requesting episodes -> /library/metadata/{id}/children
            if (hasEpisode && !params.recursive) {
                return PlexQueryPlan(path = "/library/metadata/$decodedParent/children", queryParameters = paging)
            }

            // Recently added in a library section: /library/sections/{id}/recentlyAdded
            if (isDateCreatedDesc) {
                return PlexQueryPlan(path = "/library/sections/$decodedParent/recentlyAdded", queryParameters = paging)
            }

            // Library section browse: /library/sections/{id}/all
            val queryMap = mutableMapOf<String, Any?>()
            val type = when {
                params.includeItemTypes.any { it.equals("Movie", ignoreCase = true) } -> TYPE_MOVIE
                params.includeItemTypes.any { it.equals("Series", ignoreCase = true) } -> TYPE_SERIES
                else -> null
            }
            if (type != null) queryMap["type"] = type
            val sort = mapSort(params.sortBy, params.sortOrder)
            if (sort != null) queryMap["sort"] = sort
            queryMap.putAll(paging)

            return PlexQueryPlan(path = "/library/sections/$decodedParent/all", queryParameters = queryMap)
        }

        // 9. Fallback
        val queryMap = mutableMapOf<String, Any?>()
        val sort = mapSort(params.sortBy, params.sortOrder)
        if (sort != null) queryMap["sort"] = sort
        queryMap.putAll(paging)
        return PlexQueryPlan(path = "/library/all", queryParameters = queryMap)
    }

    fun mapSort(sortBy: List<String>, sortOrder: String?): String? {
        if (sortBy.isEmpty()) return null
        val firstSort = sortBy.first()
        val plexField = when (firstSort) {
            "SortName" -> "titleSort"
            "DateCreated" -> "addedAt"
            "PremiereDate" -> "originallyAvailableAt"
            "CommunityRating" -> "audienceRating"
            "Random" -> "random"
            "DatePlayed" -> "lastViewedAt"
            else -> firstSort.replaceFirstChar { it.lowercase() }
        }
        val isDesc = sortOrder.equals("Descending", ignoreCase = true)
        return if (isDesc) "$plexField:desc" else "$plexField:asc"
    }

    fun mapPaging(startIndex: Int?, limit: Int?): Map<String, Any?> = buildMap {
        if (startIndex != null && startIndex > 0) {
            put(HEADER_START, startIndex)
        }
        if (limit != null && limit > 0) {
            put(HEADER_SIZE, limit)
        }
    }

    fun fromQueryMap(query: Map<String, Any?>): PlexQueryParams {
        fun toList(v: Any?): List<String> = when (v) {
            null -> emptyList()
            is Iterable<*> -> v.filterNotNull().map { it.toString() }
            is Array<*> -> v.filterNotNull().map { it.toString() }
            else -> v.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
        return PlexQueryParams(
            parentId = query["parentId"]?.toString(),
            includeItemTypes = toList(query["includeItemTypes"]),
            recursive = query["recursive"]?.toString()?.toBooleanStrictOrNull() ?: false,
            sortBy = toList(query["sortBy"]),
            sortOrder = query["sortOrder"]?.toString(),
            startIndex = query["startIndex"]?.toString()?.toIntOrNull(),
            limit = query["limit"]?.toString()?.toIntOrNull(),
            searchTerm = query["searchTerm"]?.toString(),
            filters = toList(query["filters"]),
            ids = toList(query["ids"]),
            personIds = toList(query["personIds"]),
            artistIds = toList(query["artistIds"]),
            albumArtistIds = toList(query["albumArtistIds"]),
        )
    }
}
