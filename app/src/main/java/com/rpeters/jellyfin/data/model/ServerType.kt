package com.rpeters.jellyfin.data.model

import kotlinx.serialization.Serializable

/**
 * Which media server product a saved connection talks to.
 *
 * UI code asks the capability getters below rather than comparing server types, so a new server
 * type only has to fill in this table.
 */
@Serializable
enum class ServerType {
    JELLYFIN,
    EMBY,
    PLEX,
    ;

    val supportsSyncPlay: Boolean get() = this == JELLYFIN
    val supportsCinefinPlugin: Boolean get() = this == JELLYFIN
    val supportsQuickConnect: Boolean get() = this == JELLYFIN

    /** Per-item favorites. Plex has none (its watchlist is a plex.tv feature, see [supportsWatchlist]). */
    val supportsFavorites: Boolean get() = this != PLEX

    /** Intro and credits segments. Emby's come from chapter markers; Plex's markers are not mapped yet. */
    val supportsMediaSegments: Boolean get() = this != PLEX

    /** Username and password sign-in. Plex signs in with a plex.tv PIN only. */
    val supportsPasswordSignIn: Boolean get() = this != PLEX

    /** Streaming away from the server's home network may need the owner's Plex Pass. */
    val remoteStreamingMayRequirePass: Boolean get() = this == PLEX

    /** The server exposes a per-user remote client bitrate limit. */
    val supportsRemoteBitrateLimit: Boolean get() = this == EMBY

    // Server-specific features that are not built yet; each turns on with its feature.

    /** Plex Home managed users. */
    val supportsManagedUsers: Boolean get() = false

    /** A plex.tv watchlist standing in for favorites. */
    val supportsWatchlist: Boolean get() = false

    /** The server supplies its own home rows (Plex hubs). */
    val providesServerHubs: Boolean get() = false

    /** Downloads the server converts to a chosen quality first. */
    val supportsServerConvertedDownloads: Boolean get() = false

    companion object {
        // Jellyfin has reported 10.x and later since it forked; Emby is on 4.x.
        private const val FIRST_JELLYFIN_MAJOR_VERSION = 10

        /**
         * Works out the server type from `/System/Info/Public`. Jellyfin names itself in
         * `ProductName`; Emby sends no product name and a 4.x version.
         *
         * Anything that cannot be positively identified as Emby is treated as Jellyfin, so a
         * Jellyfin server with an unusual version string is never turned away. Plex does not
         * serve `/System/Info/Public`; it is detected from `/identity` instead.
         */
        fun detect(productName: String?, version: String?): ServerType {
            if (productName?.contains("jellyfin", ignoreCase = true) == true) return JELLYFIN
            if (productName?.contains("emby", ignoreCase = true) == true) return EMBY
            val majorVersion = version?.substringBefore('.')?.toIntOrNull() ?: return JELLYFIN
            return if (majorVersion < FIRST_JELLYFIN_MAJOR_VERSION) EMBY else JELLYFIN
        }
    }
}
