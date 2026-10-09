package com.rpeters.jellyfin.data.model

import kotlinx.serialization.Serializable

/**
 * Which media server product a saved connection talks to.
 */
@Serializable
enum class ServerType {
    JELLYFIN,
    EMBY,
    ;

    val supportsSyncPlay: Boolean get() = this == JELLYFIN
    val supportsCinefinPlugin: Boolean get() = this == JELLYFIN

    companion object {
        // Jellyfin has reported 10.x and later since it forked; Emby is on 4.x.
        private const val FIRST_JELLYFIN_MAJOR_VERSION = 10

        /**
         * Works out the server type from `/System/Info/Public`. Jellyfin names itself in
         * `ProductName`; Emby sends no product name and a 4.x version.
         *
         * Anything that cannot be positively identified as Emby is treated as Jellyfin, so a
         * Jellyfin server with an unusual version string is never turned away.
         */
        fun detect(productName: String?, version: String?): ServerType {
            if (productName?.contains("jellyfin", ignoreCase = true) == true) return JELLYFIN
            if (productName?.contains("emby", ignoreCase = true) == true) return EMBY
            val majorVersion = version?.substringBefore('.')?.toIntOrNull() ?: return JELLYFIN
            return if (majorVersion < FIRST_JELLYFIN_MAJOR_VERSION) EMBY else JELLYFIN
        }
    }
}
