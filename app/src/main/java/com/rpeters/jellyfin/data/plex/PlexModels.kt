package com.rpeters.jellyfin.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * plex.tv (`clients.plex.tv/api/v2`) responses. Only the fields Cinefin uses are declared; the
 * client's Json ignores the rest. Classes that hold a token redact it from toString().
 */

/** A sign-in PIN. [authToken] stays null until the user approves [code] on plex.tv. */
@Serializable
data class PlexPin(
    val id: Long,
    val code: String,
    /** Seconds left before the PIN expires, as of the response. */
    val expiresIn: Int? = null,
    val authToken: String? = null,
) {
    override fun toString(): String =
        "PlexPin(id=$id, code=$code, expiresIn=${expiresIn ?: "unknown"}, " +
            "authToken=${if (authToken == null) "null" else "redacted"})"
}

/** The signed-in plex.tv account. */
@Serializable
data class PlexTvUser(
    val id: Long,
    val uuid: String? = null,
    val username: String,
    val title: String? = null,
    val thumb: String? = null,
) {
    /** The name to show: Plex Home users have a [title] that differs from [username]. */
    val displayName: String get() = title?.takeIf { it.isNotBlank() } ?: username
}

/**
 * A device on the account from `/api/v2/resources`. Only resources that provide `server` are
 * Plex Media Servers; the list also contains players and other clients.
 */
@Serializable
data class PlexResource(
    val name: String,
    val product: String? = null,
    val productVersion: String? = null,
    /** The server's `machineIdentifier`. */
    val clientIdentifier: String,
    val provides: String = "",
    /** True for the account's own servers; false for servers shared with it. */
    val owned: Boolean = false,
    /** The token for this server. On a shared server it differs from the account token. */
    val accessToken: String? = null,
    /** Whether plex.tv has heard from the server recently. */
    val presence: Boolean = false,
    /** The owner's name on a shared server. */
    val sourceTitle: String? = null,
    val connections: List<PlexConnection> = emptyList(),
) {
    val isServer: Boolean get() = provides.split(',').any { it.trim() == "server" }

    override fun toString(): String =
        "PlexResource(name=$name, clientIdentifier=$clientIdentifier, provides=$provides, owned=$owned, " +
            "presence=$presence, connections=${connections.size}, accessToken=redacted)"
}

/** One address a server can be reached on. */
@Serializable
data class PlexConnection(
    val protocol: String = "",
    val address: String = "",
    val port: Int = 0,
    val uri: String,
    /** On the same network as the server, as plex.tv saw it. */
    val local: Boolean = false,
    /** Goes through Plex's relay, which is bandwidth-limited. */
    val relay: Boolean = false,
    @SerialName("IPv6") val ipv6: Boolean = false,
) {
    val isHttps: Boolean get() = protocol.equals("https", ignoreCase = true) || uri.startsWith("https://")
}

/** The error body plex.tv sends with a 4xx, e.g. code 1020 for an unknown or expired PIN. */
@Serializable
internal data class PlexTvErrors(val errors: List<PlexTvError> = emptyList())

@Serializable
internal data class PlexTvError(val code: Int = 0, val message: String = "", val status: Int = 0)

/*
 * Plex Media Server (`/library/...`, `/identity`, etc.) responses.
 */

/** Response envelope returned by almost all Plex Media Server endpoints. */
@Serializable
data class PlexMediaContainerResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexMediaContainer,
)

/** The container holding library sections, items, or search hubs. */
@Serializable
data class PlexMediaContainer(
    val size: Int = 0,
    val totalSize: Int? = null,
    val offset: Int? = null,
    val identifier: String? = null,
    val parentRatingKey: String? = null,
    val parentTitle: String? = null,
    val grandparentRatingKey: String? = null,
    val grandparentTitle: String? = null,
    val librarySectionID: Long? = null,
    val librarySectionTitle: String? = null,
    @SerialName("Directory") val directory: List<PlexDirectory> = emptyList(),
    @SerialName("Metadata") val metadata: List<PlexMetadata> = emptyList(),
    @SerialName("Hub") val hub: List<PlexHub> = emptyList(),
)

/** A library section (e.g. Movies, TV Shows) from `/library/sections`. */
@Serializable
data class PlexDirectory(
    val key: String,
    val type: String,
    val title: String,
    val agent: String? = null,
    val scanner: String? = null,
    val language: String? = null,
    val uuid: String? = null,
    val updatedAt: Long? = null,
    val createdAt: Long? = null,
    val composite: String? = null,
    val thumb: String? = null,
    val art: String? = null,
)

/** A media item (movie, series, season, episode, track, etc.) on Plex Media Server. */
@Serializable
data class PlexMetadata(
    val ratingKey: String,
    val key: String? = null,
    val guid: String? = null,
    val studio: String? = null,
    val type: String,
    val title: String,
    val titleSort: String? = null,
    val originalTitle: String? = null,
    val contentRating: String? = null,
    val summary: String? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,
    val year: Int? = null,
    val tagline: String? = null,
    val thumb: String? = null,
    val art: String? = null,
    val clearLogo: String? = null,
    val duration: Long? = null,
    val originallyAvailableAt: String? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val lastViewedAt: Long? = null,
    val index: Int? = null,
    val parentIndex: Int? = null,
    val parentRatingKey: String? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val grandparentRatingKey: String? = null,
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    val childCount: Int? = null,
    val leafCount: Int? = null,
    val viewedLeafCount: Int? = null,
    @SerialName("Genre") val genres: List<PlexTag> = emptyList(),
    @SerialName("Country") val countries: List<PlexTag> = emptyList(),
    @SerialName("Role") val roles: List<PlexRole> = emptyList(),
    @SerialName("Director") val directors: List<PlexTag> = emptyList(),
    @SerialName("Writer") val writers: List<PlexTag> = emptyList(),
    @SerialName("Producer") val producers: List<PlexTag> = emptyList(),
    @SerialName("Media") val media: List<PlexMedia> = emptyList(),
    @SerialName("Marker") val markers: List<PlexMarker> = emptyList(),
)

/** Tag entry (genre, country, director, writer, producer). */
@Serializable
data class PlexTag(
    val id: Long? = null,
    val tag: String,
    val filter: String? = null,
)

/** Cast/actor role. */
@Serializable
data class PlexRole(
    val id: Long? = null,
    val tag: String,
    val role: String? = null,
    val thumb: String? = null,
)

/** Media container on an item. */
@Serializable
data class PlexMedia(
    val id: Long? = null,
    val duration: Long? = null,
    val bitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspectRatio: Double? = null,
    val audioChannels: Int? = null,
    val audioCodec: String? = null,
    val videoCodec: String? = null,
    val videoResolution: String? = null,
    val container: String? = null,
    val videoFrameRate: String? = null,
    @SerialName("Part") val parts: List<PlexPart> = emptyList(),
)

/** Media part representing a file on disk. */
@Serializable
data class PlexPart(
    val id: Long? = null,
    val key: String? = null,
    val duration: Long? = null,
    val file: String? = null,
    val size: Long? = null,
    val container: String? = null,
    val decision: String? = null,
    @SerialName("Stream") val streams: List<PlexStream> = emptyList(),
)

/** Stream within a media part (video, audio, subtitle). */
@Serializable
data class PlexStream(
    val id: Long? = null,
    val streamType: Int? = null,
    val default: Boolean? = null,
    val selected: Boolean? = null,
    val codec: String? = null,
    val index: Int? = null,
    val channels: Int? = null,
    val language: String? = null,
    val languageCode: String? = null,
    val displayTitle: String? = null,
    val extendedDisplayTitle: String? = null,
    val title: String? = null,
    val decision: String? = null,
    val location: String? = null,
    val key: String? = null,
)

/** Chapter/timeline marker (intro, credits). */
@Serializable
data class PlexMarker(
    val id: Long? = null,
    val type: String,
    val startTimeOffset: Long,
    val endTimeOffset: Long,
)

/** A hub in `/hubs/search` or `/hubs/continueWatching`. */
@Serializable
data class PlexHub(
    val key: String? = null,
    val title: String? = null,
    val type: String? = null,
    val hubIdentifier: String? = null,
    val size: Int = 0,
    val more: Boolean = false,
    @SerialName("Metadata") val metadata: List<PlexMetadata> = emptyList(),
)

