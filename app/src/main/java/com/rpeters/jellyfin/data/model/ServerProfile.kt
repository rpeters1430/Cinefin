package com.rpeters.jellyfin.data.model

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.utils.normalizeServerUrl
import kotlinx.serialization.Serializable

/**
 * One saved sign-in: a user on a server. Two users on the same server are two profiles.
 */
@Serializable
data class ServerProfile(
    val id: String,
    val serverId: String = "",
    val serverUrl: String,
    val serverName: String,
    val serverType: ServerType = ServerType.JELLYFIN,
    val userId: String? = null,
    val username: String,
    val accessToken: String? = null,
    val loginTimestamp: Long? = null,
    val isAdministrator: Boolean = false,
) {
    fun toJellyfinServer(): JellyfinServer {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        return JellyfinServer(
            id = serverId,
            name = serverName,
            url = normalizedUrl,
            isConnected = true,
            userId = userId,
            username = username,
            accessToken = accessToken,
            loginTimestamp = loginTimestamp,
            normalizedUrl = normalizedUrl,
            isAdministrator = isAdministrator,
            serverType = serverType,
        )
    }

    companion object {
        /**
         * Keyed on server ID plus user ID so a token is never matched to the wrong server when a
         * URL changes. Falls back to URL plus username for sessions saved without those IDs.
         */
        fun profileId(serverId: String?, userId: String?, serverUrl: String, username: String): String =
            if (!serverId.isNullOrBlank() && !userId.isNullOrBlank()) {
                "$serverId:$userId"
            } else {
                "${normalizeServerUrl(serverUrl)}|$username"
            }

        /** Returns null when the server has no signed-in user to save. */
        fun fromServer(server: JellyfinServer): ServerProfile? {
            val username = server.username?.takeIf { it.isNotBlank() } ?: return null
            if (server.url.isBlank()) return null
            val normalizedUrl = normalizeServerUrl(server.url)
            return ServerProfile(
                id = profileId(server.id, server.userId, normalizedUrl, username),
                serverId = server.id,
                serverUrl = normalizedUrl,
                serverName = server.name,
                serverType = server.serverType,
                userId = server.userId,
                username = username,
                accessToken = server.accessToken,
                loginTimestamp = server.loginTimestamp,
                isAdministrator = server.isAdministrator,
            )
        }
    }
}
