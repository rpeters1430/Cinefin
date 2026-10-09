package com.rpeters.jellyfin.data.model

import kotlinx.serialization.Serializable

/**
 * Which media server product a saved connection talks to.
 */
@Serializable
enum class ServerType {
    JELLYFIN,
    EMBY,
}
