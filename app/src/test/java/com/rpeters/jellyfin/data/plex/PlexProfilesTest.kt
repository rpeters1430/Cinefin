package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import com.rpeters.jellyfin.data.model.ServerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PlexProfilesTest {

    private val user = PlexTvUser(id = 12345, uuid = "abc123", username = "carol", title = "Carol")
    private val address = PlexServerAddress(
        uri = "$LOCAL/",
        alternateUris = listOf(REMOTE, "$LOCAL/", RELAY),
    )

    @Test
    fun create_ownedServer_savesServerTokenAndKeepsAccountTokenApart() {
        val profile = PlexProfiles.create(server(owned = true), address, user, ACCOUNT_TOKEN, LOGIN_TIME)!!

        assertEquals(ServerType.PLEX, profile.serverType)
        assertEquals(MACHINE_ID, profile.serverId)
        assertEquals(LOCAL, profile.serverUrl)
        assertEquals("Living Room PMS", profile.serverName)
        assertEquals("Carol", profile.username)
        assertEquals(SERVER_TOKEN, profile.accessToken)
        assertEquals(ACCOUNT_TOKEN, profile.plexAccountToken)
        assertEquals(LOGIN_TIME, profile.loginTimestamp)
        assertTrue(profile.isAdministrator)
        assertEquals("$MACHINE_ID:${profile.userId}", profile.id)
    }

    @Test
    fun create_alternates_dropTheChosenAddressAndDuplicates() {
        val profile = PlexProfiles.create(server(owned = true), address, user, ACCOUNT_TOKEN, LOGIN_TIME)!!

        assertEquals(listOf(REMOTE, RELAY), profile.alternateServerUrls)
    }

    @Test
    fun create_userId_isTheAccountIdAsAPlexUuid() {
        val profile = PlexProfiles.create(server(owned = true), address, user, ACCOUNT_TOKEN, LOGIN_TIME)!!

        val uuid = UUID.fromString(profile.userId)
        assertTrue(ServerIdCodec.PLEX.isEncoded(uuid))
        assertEquals("12345", ServerIdCodec.PLEX.decode(uuid))
    }

    @Test
    fun create_sharedServer_isNotAdministrator() {
        val profile = PlexProfiles.create(server(owned = false), address, user, ACCOUNT_TOKEN, LOGIN_TIME)!!

        assertEquals(false, profile.isAdministrator)
        assertEquals(SERVER_TOKEN, profile.accessToken)
    }

    @Test
    fun create_ownedServerWithoutToken_fallsBackToAccountToken() {
        val profile = PlexProfiles.create(
            server(owned = true, accessToken = null), address, user, ACCOUNT_TOKEN, LOGIN_TIME,
        )!!

        assertEquals(ACCOUNT_TOKEN, profile.accessToken)
    }

    @Test
    fun create_sharedServerWithoutToken_returnsNull() {
        assertNull(PlexProfiles.create(server(owned = false, accessToken = ""), address, user, ACCOUNT_TOKEN, LOGIN_TIME))
    }

    private fun server(owned: Boolean, accessToken: String? = SERVER_TOKEN) = PlexResource(
        name = "Living Room PMS",
        clientIdentifier = MACHINE_ID,
        provides = "server",
        owned = owned,
        accessToken = accessToken,
    )

    private companion object {
        const val MACHINE_ID = "machine-owned-1"
        const val SERVER_TOKEN = "server-token"
        const val ACCOUNT_TOKEN = "account-token"
        const val LOGIN_TIME = 1_700_000_000_000L
        const val LOCAL = "https://10-0-0-5.abc123.plex.direct:32400"
        const val REMOTE = "https://203-0-113-9.abc123.plex.direct:32400"
        const val RELAY = "https://198-51-100-20.abc123.plex.direct:8443"
    }
}
