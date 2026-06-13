package com.rpeters.jellyfin.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Sonarr models ────────────────────────────────────────────────────────────

@Serializable
data class SonarrSeriesLookupResult(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String = "",
    @SerialName("titleSlug") val titleSlug: String = "",
    @SerialName("tvdbId") val tvdbId: Int = 0,
    @SerialName("seriesType") val seriesType: String = "standard",
    @SerialName("seasons") val seasons: List<SonarrSeasonItem> = emptyList(),
    @SerialName("rootFolderPath") val rootFolderPath: String? = null,
    @SerialName("qualityProfileId") val qualityProfileId: Int = 0,
    @SerialName("images") val images: List<SonarrImage> = emptyList(),
)

@Serializable
data class SonarrSeasonItem(
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("monitored") val monitored: Boolean = false,
)

@Serializable
data class SonarrImage(
    @SerialName("coverType") val coverType: String = "",
    @SerialName("url") val url: String = "",
)

@Serializable
data class SonarrRootFolder(
    @SerialName("path") val path: String,
)

@Serializable
data class SonarrQualityProfile(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
)

@Serializable
data class SonarrEpisodeItem(
    @SerialName("id") val id: Int,
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("episodeNumber") val episodeNumber: Int,
)

@Serializable
data class SonarrAddSeriesRequest(
    @SerialName("title") val title: String,
    @SerialName("titleSlug") val titleSlug: String,
    @SerialName("tvdbId") val tvdbId: Int,
    @SerialName("seriesType") val seriesType: String,
    @SerialName("seasons") val seasons: List<SonarrSeasonItem>,
    @SerialName("rootFolderPath") val rootFolderPath: String,
    @SerialName("qualityProfileId") val qualityProfileId: Int,
    @SerialName("monitored") val monitored: Boolean = true,
    @SerialName("addOptions") val addOptions: SonarrAddOptions = SonarrAddOptions(),
    @SerialName("images") val images: List<SonarrImage> = emptyList(),
)

@Serializable
data class SonarrAddOptions(
    @SerialName("searchForMissingEpisodes") val searchForMissingEpisodes: Boolean = true,
)

@Serializable
data class SonarrCommand(
    @SerialName("name") val name: String,
    @SerialName("episodeIds") val episodeIds: List<Int> = emptyList(),
    @SerialName("seriesId") val seriesId: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
)

// ── Radarr models ────────────────────────────────────────────────────────────

@Serializable
data class RadarrMovieLookupResult(
    @SerialName("title") val title: String = "",
    @SerialName("titleSlug") val titleSlug: String = "",
    @SerialName("tmdbId") val tmdbId: Int = 0,
    @SerialName("year") val year: Int = 0,
    @SerialName("images") val images: List<RadarrImage> = emptyList(),
)

@Serializable
data class RadarrImage(
    @SerialName("coverType") val coverType: String = "",
    @SerialName("url") val url: String = "",
)

@Serializable
data class RadarrRootFolder(
    @SerialName("path") val path: String,
)

@Serializable
data class RadarrQualityProfile(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
)

@Serializable
data class RadarrAddMovieRequest(
    @SerialName("title") val title: String,
    @SerialName("titleSlug") val titleSlug: String,
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("year") val year: Int,
    @SerialName("images") val images: List<RadarrImage> = emptyList(),
    @SerialName("rootFolderPath") val rootFolderPath: String,
    @SerialName("qualityProfileId") val qualityProfileId: Int,
    @SerialName("monitored") val monitored: Boolean = true,
    @SerialName("addOptions") val addOptions: RadarrAddOptions = RadarrAddOptions(),
)

@Serializable
data class RadarrAddOptions(
    @SerialName("searchForMovie") val searchForMovie: Boolean = true,
)
