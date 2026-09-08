package com.rpeters.jellyfin.benchmarks

import com.rpeters.jellyfin.data.model.SeerrMediaInfo
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.data.model.SeerrRequest
import com.rpeters.jellyfin.data.model.SeerrRequestedSeason
import com.rpeters.jellyfin.data.model.SeerrSearchResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Fixtures for the Jellyseerr payload benchmarks.
 *
 * The JSON is generated once from the production models so the benchmarked payload stays
 * in sync with the schema the app actually consumes, and the parser is exercised with a
 * realistic discover/search page (nested media info, requests and seasons).
 */
object SeerrPayloads {

    /** Mirrors the `Json` instance configured in `SeerrModule`. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** A single search page as returned by Jellyseerr (20 results). */
    val searchPageJson: String = buildSearchPageJson(resultCount = 20)

    /** A larger page, representative of an infinite-scroll discover request. */
    val largeSearchPageJson: String = buildSearchPageJson(resultCount = 100)

    fun parseSearchPage(payload: String): SeerrSearchResult =
        json.decodeFromString(SeerrSearchResult.serializer(), payload)

    fun encodeSearchPage(result: SeerrSearchResult): String =
        json.encodeToString(SeerrSearchResult.serializer(), result)

    private fun buildSearchPageJson(resultCount: Int): String {
        val result = SeerrSearchResult(
            page = 1,
            totalPages = 42,
            totalResults = resultCount * 42,
            results = (1..resultCount).map { index -> buildMediaItem(index) },
        )
        return json.encodeToString(result)
    }

    private fun buildMediaItem(index: Int): SeerrMediaItem {
        val isMovie = index % 2 == 0
        return SeerrMediaItem(
            id = index,
            mediaType = if (isMovie) "movie" else "tv",
            tmdbId = 100_000 + index,
            tvdbId = if (isMovie) null else 200_000 + index,
            title = if (isMovie) "Benchmark Movie $index" else null,
            name = if (isMovie) null else "Benchmark Series $index",
            overview = OVERVIEW,
            posterPath = "/poster_$index.jpg",
            backdropPath = "/backdrop_$index.jpg",
            releaseDate = if (isMovie) "2024-0${index % 9 + 1}-1$index" else null,
            firstAirDate = if (isMovie) null else "2023-0${index % 9 + 1}-0$index",
            mediaInfo = SeerrMediaInfo(
                status = index % 5 + 1,
                requests = listOf(
                    SeerrRequest(
                        id = index,
                        status = index % 3 + 1,
                        createdAt = "2024-01-0${index % 9 + 1}T12:00:00.000Z",
                        seasons = if (isMovie) {
                            emptyList()
                        } else {
                            (1..3).map { season -> SeerrRequestedSeason(season, season % 5 + 1) }
                        },
                        is4k = index % 4 == 0,
                    ),
                ),
                seasons = emptyList(),
            ),
        )
    }

    private const val OVERVIEW =
        "A representative overview blob, long enough to reflect the payload sizes returned " +
            "by the Jellyseerr API for discover and search requests, including punctuation, " +
            "unicode characters such as \u00e9\u00e8\u00fc and escaped \"quotes\"."
}
