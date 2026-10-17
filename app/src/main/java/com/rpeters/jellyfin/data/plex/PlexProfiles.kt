package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.utils.normalizeServerUrl

/** Builds the saved profile for a Plex server picked after plex.tv sign-in (KD6). */
object PlexProfiles {

    /**
     * One profile per (account, server): [ServerProfile.serverId] is the server's machine
     * identifier and [ServerProfile.accessToken] the server token from [server]. The account
     * token is kept apart in [ServerProfile.plexAccountToken] and is only ever sent to plex.tv.
     *
     * @return null when plex.tv gave no token for a shared server; the account token only works
     *   on servers the account owns.
     */
    fun create(
        server: PlexResource,
        address: PlexServerAddress,
        user: PlexTvUser,
        accountToken: String,
        loginTimestamp: Long,
    ): ServerProfile? {
        val serverToken = server.accessToken?.takeIf { it.isNotBlank() }
            ?: accountToken.takeIf { server.owned }
            ?: return null
        val serverUrl = normalizeServerUrl(address.uri)
        val userId = userId(user)
        return ServerProfile(
            id = ServerProfile.profileId(server.clientIdentifier, userId, serverUrl, user.displayName),
            serverId = server.clientIdentifier,
            serverUrl = serverUrl,
            serverName = server.name,
            serverType = ServerType.PLEX,
            userId = userId,
            username = user.displayName,
            accessToken = serverToken,
            loginTimestamp = loginTimestamp,
            isAdministrator = server.owned,
            plexAccountToken = accountToken,
            alternateServerUrls = address.alternateUris.map(::normalizeServerUrl).distinct() - serverUrl,
        )
    }

    /**
     * The plex.tv account ID as a UUID string, since screens parse user IDs as UUIDs (KD2). Uses
     * the [ServerIdCodec.PLEX] marker; account and item IDs never meet in the same lookup.
     */
    fun userId(user: PlexTvUser): String =
        ServerIdCodec.PLEX.encode(user.id.toString())?.toString() ?: user.id.toString()
}
