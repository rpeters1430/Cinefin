package com.rpeters.jellyfin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SeerrSearchResult(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val results: List<SeerrMediaItem>
)

@Serializable
data class SeerrMediaItem(
    val id: Int,
    val mediaType: String, // "movie" or "tv"
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val title: String? = null,
    val name: String? = null, // TV shows use "name"
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val mediaInfo: SeerrMediaInfo? = null
) {
    val displayTitle: String get() = title ?: name ?: "Unknown"
    val displayDate: String get() = releaseDate ?: firstAirDate ?: ""
}

@Serializable
data class SeerrMediaInfo(
    val status: Int, // 1 = unknown, 2 = pending, 3 = processing, 4 = partially available, 5 = available
    val requests: List<SeerrRequest>? = null,
    val seasons: List<SeerrMediaInfoSeason> = emptyList()
)

@Serializable
data class SeerrMediaInfoSeason(
    val id: Int? = null,
    val seasonNumber: Int,
    val status: Int? = null,
    val status4k: Int? = null
)

@Serializable
data class SeerrRequest(
    val id: Int,
    val status: Int, // 1 = pending, 2 = approved, 3 = declined
    val createdAt: String? = null,
    val seasons: List<SeerrRequestedSeason> = emptyList(),
    val is4k: Boolean = false
)

@Serializable
data class SeerrRequestedSeason(
    val seasonNumber: Int,
    val status: Int? = null
)

@Serializable
data class SeerrRequestRequest(
    val mediaType: String,
    val mediaId: Int,
    val seasons: List<Int>? = null,
    val is4k: Boolean = false
)

@Serializable
data class SeerrRequestResponse(
    val id: Int? = null,
    val status: Int? = null,
    val media: SeerrMediaInfo? = null,
    val message: String? = null
)

@Serializable
data class SeerrExternalIds(
    val tvdbId: Int? = null,
    val imdbId: String? = null,
)

@Serializable
data class SeerrTvDetails(
    val id: Int? = null,
    val name: String? = null,
    val mediaInfo: SeerrMediaInfo? = null,
    val seasons: List<SeerrSeason> = emptyList(),
    val externalIds: SeerrExternalIds? = null,
)

@Serializable
data class SeerrSeason(
    val id: Int? = null,
    val name: String? = null,
    val seasonNumber: Int,
    val episodeCount: Int? = null,
    val airDate: String? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    val episodes: List<SeerrEpisode> = emptyList()
)

@Serializable
data class SeerrEpisode(
    val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val airDate: String? = null,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null
)
