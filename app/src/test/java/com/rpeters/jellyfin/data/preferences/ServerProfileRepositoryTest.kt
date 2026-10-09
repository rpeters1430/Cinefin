package com.rpeters.jellyfin.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.model.ServerType
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerProfileRepositoryTest {

    private lateinit var dataStore: InMemoryDataStore
    private lateinit var repository: ServerProfileRepository

    private val jellyfinProfile = ServerProfile(
        id = "server-a:user-1",
        serverId = "server-a",
        serverUrl = "https://jellyfin.example.com",
        serverName = "Home Jellyfin",
        serverType = ServerType.JELLYFIN,
        userId = "user-1",
        username = "alice",
        accessToken = "token-a",
        loginTimestamp = 1_000L,
    )

    private val embyProfile = ServerProfile(
        id = "server-b:user-2",
        serverId = "server-b",
        serverUrl = "https://emby.example.com",
        serverName = "Home Emby",
        serverType = ServerType.EMBY,
        userId = "user-2",
        username = "bob",
        accessToken = "token-b",
        loginTimestamp = 2_000L,
    )

    @Before
    fun setUp() {
        // SecureLogger writes through android.util.Log, which is not available on the JVM.
        mockkObject(com.rpeters.jellyfin.utils.SecureLogger)
        every { com.rpeters.jellyfin.utils.SecureLogger.w(any(), any(), any()) } returns Unit
        dataStore = InMemoryDataStore()
        repository = ServerProfileRepository(dataStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun clearAuthentication_forgetsOnlySelectedProfilesTokensAndConnectLink() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile.copy(embyConnectUserId = "connect-user", embyConnectAccessKey = "linked-key"))
        repository.clearAuthentication(embyProfile.id)
        val saved = repository.current()
        assertEquals(jellyfinProfile, saved.profiles.first { it.id == jellyfinProfile.id })
        val forgotten = saved.profiles.first { it.id == embyProfile.id }
        assertNull(forgotten.accessToken)
        assertNull(forgotten.embyConnectAccessKey)
        assertNull(forgotten.embyConnectUserId)
    }

    @Test
    fun current_noSavedProfiles_returnsEmptyWithNoActiveProfile() = runTest {
        val profiles = repository.current()

        assertTrue(profiles.profiles.isEmpty())
        assertNull(profiles.activeProfileId)
        assertNull(profiles.activeProfile)
    }

    @Test
    fun saveAndActivate_newProfile_addsItAndMakesItActive() = runTest {
        repository.saveAndActivate(jellyfinProfile)

        val profiles = repository.current()
        assertEquals(listOf(jellyfinProfile), profiles.profiles)
        assertEquals(jellyfinProfile, profiles.activeProfile)
    }

    @Test
    fun saveAndActivate_secondProfile_keepsFirstAndActivatesSecond() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile)

        val profiles = repository.current()
        assertEquals(listOf(jellyfinProfile, embyProfile), profiles.profiles)
        assertEquals(embyProfile, profiles.activeProfile)
    }

    @Test
    fun saveAndActivate_existingId_replacesInPlaceWithoutDuplicating() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile)
        val refreshed = jellyfinProfile.copy(accessToken = "token-a2", loginTimestamp = 3_000L)

        repository.saveAndActivate(refreshed)

        val profiles = repository.current()
        assertEquals(listOf(refreshed, embyProfile), profiles.profiles)
        assertEquals(refreshed, profiles.activeProfile)
    }

    @Test
    fun setActive_savedProfile_switchesActiveAndReturnsIt() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile)

        val activated = repository.setActive(jellyfinProfile.id)

        assertEquals(jellyfinProfile, activated)
        assertEquals(jellyfinProfile, repository.current().activeProfile)
    }

    @Test
    fun setActive_unknownProfile_returnsNullAndKeepsActive() = runTest {
        repository.saveAndActivate(jellyfinProfile)

        val activated = repository.setActive("missing")

        assertNull(activated)
        assertEquals(jellyfinProfile, repository.current().activeProfile)
    }

    @Test
    fun remove_activeProfile_removesItAndLeavesNoActiveProfile() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile)

        val removed = repository.remove(embyProfile.id)

        val profiles = repository.current()
        assertEquals(embyProfile, removed)
        assertEquals(listOf(jellyfinProfile), profiles.profiles)
        assertNull(profiles.activeProfile)
    }

    @Test
    fun remove_inactiveProfile_keepsActiveProfile() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile)

        repository.remove(jellyfinProfile.id)

        val profiles = repository.current()
        assertEquals(listOf(embyProfile), profiles.profiles)
        assertEquals(embyProfile, profiles.activeProfile)
    }

    @Test
    fun remove_unknownProfile_returnsNullAndChangesNothing() = runTest {
        repository.saveAndActivate(jellyfinProfile)

        val removed = repository.remove("missing")

        assertNull(removed)
        assertEquals(listOf(jellyfinProfile), repository.current().profiles)
    }

    @Test
    fun clearToken_savedProfile_dropsTokenButKeepsProfile() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile)

        repository.clearToken(jellyfinProfile.id)

        val profiles = repository.current().profiles
        assertEquals(jellyfinProfile.copy(accessToken = null, loginTimestamp = null), profiles[0])
        assertEquals(embyProfile, profiles[1])
    }

    @Test
    fun current_newRepositoryOnSameStore_readsPersistedProfiles() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        repository.saveAndActivate(embyProfile)

        val reopened = ServerProfileRepository(dataStore).current()

        assertEquals(listOf(jellyfinProfile, embyProfile), reopened.profiles)
        assertEquals(embyProfile, reopened.activeProfile)
    }

    @Test
    fun current_corruptStoredJson_returnsEmptyInsteadOfThrowing() = runTest {
        dataStore.edit { it[stringPreferencesKey("profiles")] = "{not json" }

        assertTrue(repository.current().profiles.isEmpty())
    }

    @Test
    fun current_unknownServerTypeAndExtraFields_fallsBackToJellyfin() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("profiles")] =
                """[{"id":"x:y","serverUrl":"https://s.example.com","serverName":"S","serverType":"PLEX",""" +
                """"username":"carol","futureField":true}]"""
        }

        val profile = repository.current().profiles.single()

        assertEquals(ServerType.JELLYFIN, profile.serverType)
        assertEquals("carol", profile.username)
    }

    @Test
    fun current_activeIdPointsAtMissingProfile_reportsNoActiveProfile() = runTest {
        repository.saveAndActivate(jellyfinProfile)
        dataStore.edit { it[stringPreferencesKey("active_profile_id")] = "gone" }

        val profiles = repository.current()

        assertNull(profiles.activeProfileId)
        assertNull(profiles.activeProfile)
    }

    @Test
    fun profileId_serverAndUserIdsPresent_usesIdsNotUrl() {
        val first = ServerProfile.profileId("srv", "usr", "https://one.example.com", "alice")
        val second = ServerProfile.profileId("srv", "usr", "https://two.example.com/", "alice")

        assertEquals("srv:usr", first)
        assertEquals(first, second)
    }

    @Test
    fun profileId_missingIds_fallsBackToNormalizedUrlAndUsername() {
        val withSlash = ServerProfile.profileId("", null, "https://one.example.com/", "alice")
        val withoutSlash = ServerProfile.profileId(null, "", "https://one.example.com", "alice")

        assertEquals(withSlash, withoutSlash)
        assertTrue(withSlash.endsWith("|alice"))
    }

    @Test
    fun fromServerThenToJellyfinServer_roundTripsSessionFields() {
        val server = JellyfinServer(
            id = "server-b",
            name = "Home Emby",
            url = "https://emby.example.com",
            isConnected = true,
            userId = "user-2",
            username = "bob",
            accessToken = "token-b",
            loginTimestamp = 2_000L,
            normalizedUrl = "https://emby.example.com",
            isAdministrator = true,
            serverType = ServerType.EMBY,
        )

        val profile = ServerProfile.fromServer(server)

        assertEquals("server-b:user-2", profile?.id)
        assertEquals(server, profile?.toJellyfinServer())
    }

    @Test
    fun fromServer_noUsername_returnsNull() {
        val server = JellyfinServer(id = "s", name = "n", url = "https://s.example.com", username = null)

        assertNull(ServerProfile.fromServer(server))
    }

    private class InMemoryDataStore : DataStore<Preferences> {
        private val stateFlow = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = stateFlow

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return mutex.withLock {
                val updated = transform(stateFlow.value)
                stateFlow.value = updated
                updated
            }
        }
    }
}
