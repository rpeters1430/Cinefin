package com.rpeters.jellyfin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CinefinPluginInfoResponse(
    val version: String,
    val capabilities: List<String>,
    val isConfigured: Boolean
)

@Serializable
data class CinefinPluginMediaRequest(
    val externalId: String,
    val mediaType: String,
    val seasons: List<Int>? = null,
)

@Serializable
data class CinefinPluginEpisodeRequest(
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int
)

@Serializable
data class CinefinPluginRequestResponse(
    val success: Boolean,
    val message: String
)
