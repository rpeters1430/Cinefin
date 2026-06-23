package com.rpeters.jellyfin.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SonarrAddOptions(
    // Tells Sonarr which seasons to monitor when the series is first added.
    // "all" monitors all episodes within already-monitored seasons (per-season flags still control
    // which seasons are active). Without this field Sonarr defaults to "unknown" and the series
    // is added in an unmonitored state regardless of the seasons array.
    // ALWAYS encode this field so it reaches Sonarr regardless of the Json encodeDefaults setting.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("monitor") val monitor: String = "all",
    @SerialName("searchForMissingEpisodes") val searchForMissingEpisodes: Boolean = true,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SonarrCommand(
    @SerialName("name") val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("episodeIds") val episodeIds: List<Int> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("seriesId") val seriesId: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
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
