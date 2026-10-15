package com.rpeters.jellyfin.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** One row per server type; mirrors the KD7 table in docs/plans/2026-10-15-plex-server-support.md. */
class ServerTypeCapabilitiesTest {

    private data class Row(
        val syncPlay: Boolean,
        val cinefinPlugin: Boolean,
        val quickConnect: Boolean,
        val favorites: Boolean,
        val mediaSegments: Boolean,
        val passwordSignIn: Boolean,
        val remotePass: Boolean,
        val managedUsers: Boolean = false,
        val watchlist: Boolean = false,
        val serverHubs: Boolean = false,
        val convertedDownloads: Boolean = false,
    )

    private fun ServerType.row() = Row(
        syncPlay = supportsSyncPlay,
        cinefinPlugin = supportsCinefinPlugin,
        quickConnect = supportsQuickConnect,
        favorites = supportsFavorites,
        mediaSegments = supportsMediaSegments,
        passwordSignIn = supportsPasswordSignIn,
        remotePass = remoteStreamingMayRequirePass,
        managedUsers = supportsManagedUsers,
        watchlist = supportsWatchlist,
        serverHubs = providesServerHubs,
        convertedDownloads = supportsServerConvertedDownloads,
    )

    @Test
    fun capabilityTable_matchesPlan() {
        val expected = mapOf(
            ServerType.JELLYFIN to Row(
                syncPlay = true, cinefinPlugin = true, quickConnect = true, favorites = true,
                mediaSegments = true, passwordSignIn = true, remotePass = false,
            ),
            ServerType.EMBY to Row(
                syncPlay = false, cinefinPlugin = false, quickConnect = false, favorites = true,
                mediaSegments = true, passwordSignIn = true, remotePass = false,
            ),
            ServerType.PLEX to Row(
                syncPlay = false, cinefinPlugin = false, quickConnect = false, favorites = false,
                mediaSegments = false, passwordSignIn = false, remotePass = true,
            ),
        )

        assertEquals(ServerType.entries.toSet(), expected.keys)
        ServerType.entries.forEach { assertEquals(it.name, expected.getValue(it), it.row()) }
    }
}
