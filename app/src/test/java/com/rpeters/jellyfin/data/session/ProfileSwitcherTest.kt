package com.rpeters.jellyfin.data.session

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.cache.JellyfinCache
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.preferences.ServerProfileRepository
import com.rpeters.jellyfin.data.preferences.ServerProfiles
import com.rpeters.jellyfin.data.repository.IJellyfinAuthRepository
import com.rpeters.jellyfin.di.OptimizedClientFactory
import com.rpeters.jellyfin.ui.player.CastManager
import com.rpeters.jellyfin.ui.player.audio.AudioServiceConnection
import com.rpeters.jellyfin.ui.player.cast.CastState
import com.rpeters.jellyfin.ui.viewmodel.common.SharedAppStateManager
import com.rpeters.jellyfin.utils.SecureLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@UnstableApi
class ProfileSwitcherTest {

    private val profileRepository: ServerProfileRepository = mockk(relaxed = true)
    private val activeSessionStore: ActiveSessionStore = mockk(relaxed = true)
    private val authRepository: IJellyfinAuthRepository = mockk(relaxed = true)
    private val clientFactory: OptimizedClientFactory = mockk(relaxed = true)
    private val cache: JellyfinCache = mockk(relaxed = true)
    private val sharedAppStateManager: SharedAppStateManager = mockk(relaxed = true)
    private val castManager: CastManager = mockk(relaxed = true)
    private val audioServiceConnection: AudioServiceConnection = mockk(relaxed = true)
    private val secureCredentialManager: SecureCredentialManager = mockk(relaxed = true)

    private lateinit var switcher: ProfileSwitcher

    private val now = System.currentTimeMillis()

    private val jellyfinProfile = ServerProfile(
        id = "server-a:user-1",
        serverId = "server-a",
        serverUrl = "https://jellyfin.example.com",
        serverName = "Home Jellyfin",
        userId = "user-1",
        username = "alice",
        accessToken = "token-a",
        loginTimestamp = now,
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
        loginTimestamp = now,
    )

    @Before
    fun setUp() {
        mockkObject(SecureLogger)
        every { SecureLogger.w(any(), any(), any()) } returns Unit
        every { castManager.castState } returns MutableStateFlow(CastState())
        every { authRepository.getCurrentServerSync() } returns jellyfinProfile.toJellyfinServer()
        coEvery { profileRepository.current() } returns
            ServerProfiles(listOf(jellyfinProfile, embyProfile), activeProfileId = jellyfinProfile.id)
        coEvery { profileRepository.remove(any()) } answers {
            listOf(jellyfinProfile, embyProfile).firstOrNull { it.id == firstArg<String>() }
        }
        switcher = ProfileSwitcher(
            profileRepository,
            activeSessionStore,
            authRepository,
            clientFactory,
            cache,
            sharedAppStateManager,
            castManager,
            audioServiceConnection,
            secureCredentialManager,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun switchTo_expiredLinkedProfile_exchangesKeyAndSavesFreshSession() = runTest {
        val expired = embyProfile.copy(
            accessToken = null, loginTimestamp = 1L,
            embyConnectUserId = "connect-user", embyConnectAccessKey = "linked-key",
        )
        val refreshed = expired.copy(accessToken = "fresh-token", loginTimestamp = now)
        coEvery { profileRepository.current() } returns ServerProfiles(listOf(jellyfinProfile, expired), jellyfinProfile.id)
        coEvery { authRepository.authenticateWithEmbyConnect(any(), any(), any(), any()) } returns
            com.rpeters.jellyfin.data.repository.common.ApiResult.Success(mockk<org.jellyfin.sdk.model.api.AuthenticationResult>())
        every { authRepository.getCurrentServerSync() } returns refreshed.toJellyfinServer()
        val result = switcher.switchTo(expired.id)
        assertTrue(result is ProfileSwitchResult.Switched)
        coVerify { profileRepository.saveAndActivate(refreshed) }
        coVerify { activeSessionStore.save(refreshed.toJellyfinServer()) }
    }

    @Test
    fun switchTo_linkExchangeFails_doesNotReplaceSavedActiveProfile() = runTest {
        val linked = embyProfile.copy(embyConnectUserId = "connect-user", embyConnectAccessKey = "linked-key")
        coEvery { profileRepository.current() } returns ServerProfiles(listOf(jellyfinProfile, linked), jellyfinProfile.id)
        coEvery { authRepository.authenticateWithEmbyConnect(any(), any(), any(), any()) } returns
            com.rpeters.jellyfin.data.repository.common.ApiResult.Error("Server unavailable")
        assertTrue(switcher.switchTo(linked.id) is ProfileSwitchResult.SignInRequired)
        coVerify(exactly = 0) { activeSessionStore.save(any()) }
        coVerify(exactly = 0) { profileRepository.saveAndActivate(any()) }
    }

    @Test
    fun switchTo_otherSavedProfile_restoresItsSessionAndPersistsIt() = runTest {
        val restored = slot<JellyfinServer>()

        val result = switcher.switchTo(embyProfile.id)

        assertEquals(ProfileSwitchResult.Switched(embyProfile), result)
        coVerify { profileRepository.setActive(embyProfile.id) }
        coVerify { activeSessionStore.save(capture(restored)) }
        verify { authRepository.restorePersistedSession(restored.captured) }
        assertEquals("https://emby.example.com", restored.captured.url)
        assertEquals("token-b", restored.captured.accessToken)
        assertEquals("user-2", restored.captured.userId)
        assertEquals(ServerType.EMBY, restored.captured.serverType)
    }

    @Test
    fun switchTo_otherSavedProfile_clearsServerScopedStateBeforeActivating() = runTest {
        switcher.switchTo(embyProfile.id)

        coVerifyOrder {
            audioServiceConnection.stopPlayback()
            clientFactory.clearAllClients()
            cache.clearAllCache()
            sharedAppStateManager.clearCache()
            authRepository.restorePersistedSession(any())
        }
    }

    @Test
    fun switchTo_whileCasting_endsCastSession() = runTest {
        every { castManager.castState } returns MutableStateFlow(CastState(isConnected = true))

        switcher.switchTo(embyProfile.id)

        verify { castManager.disconnectCastSession(userInitiated = true) }
    }

    @Test
    fun switchTo_notCasting_leavesCastAlone() = runTest {
        switcher.switchTo(embyProfile.id)

        verify(exactly = 0) { castManager.disconnectCastSession(any()) }
    }

    @Test
    fun switchTo_cacheClearFails_stillCompletesSwitch() = runTest {
        coEvery { cache.clearAllCache() } throws IllegalStateException("disk")

        val result = switcher.switchTo(embyProfile.id)

        assertEquals(ProfileSwitchResult.Switched(embyProfile), result)
        verify { authRepository.restorePersistedSession(any()) }
    }

    @Test
    fun switchTo_alreadyActiveSession_doesNothing() = runTest {
        val result = switcher.switchTo(jellyfinProfile.id)

        assertEquals(ProfileSwitchResult.Switched(jellyfinProfile), result)
        coVerify(exactly = 0) { cache.clearAllCache() }
        verify(exactly = 0) { authRepository.restorePersistedSession(any()) }
    }

    @Test
    fun switchTo_activeProfileButNoRunningSession_restoresIt() = runTest {
        every { authRepository.getCurrentServerSync() } returns null

        val result = switcher.switchTo(jellyfinProfile.id)

        assertEquals(ProfileSwitchResult.Switched(jellyfinProfile), result)
        verify { authRepository.restorePersistedSession(any()) }
    }

    @Test
    fun switchTo_profileWithoutToken_asksForSignInAndKeepsCurrentSession() = runTest {
        val signedOut = embyProfile.copy(accessToken = null)
        coEvery { profileRepository.current() } returns
            ServerProfiles(listOf(jellyfinProfile, signedOut), activeProfileId = jellyfinProfile.id)

        val result = switcher.switchTo(embyProfile.id)

        assertEquals(ProfileSwitchResult.SignInRequired(signedOut), result)
        coVerify(exactly = 0) { cache.clearAllCache() }
        verify(exactly = 0) { authRepository.restorePersistedSession(any()) }
    }

    @Test
    fun switchTo_profileWithStaleToken_asksForSignIn() = runTest {
        val stale = embyProfile.copy(loginTimestamp = now - 31L * 24 * 60 * 60 * 1000)
        coEvery { profileRepository.current() } returns
            ServerProfiles(listOf(jellyfinProfile, stale), activeProfileId = jellyfinProfile.id)

        val result = switcher.switchTo(embyProfile.id)

        assertEquals(ProfileSwitchResult.SignInRequired(stale), result)
    }

    @Test
    fun switchTo_unknownProfile_returnsNotFound() = runTest {
        assertEquals(ProfileSwitchResult.NotFound, switcher.switchTo("missing"))
    }

    @Test
    fun remove_inactiveProfile_clearsOnlyItsPasswordAndKeepsSession() = runTest {
        val wasActive = switcher.remove(embyProfile.id)

        assertFalse(wasActive)
        coVerify { secureCredentialManager.clearPassword("https://emby.example.com", "bob") }
        coVerify(exactly = 0) { activeSessionStore.clear() }
        verify(exactly = 0) { authRepository.seedCurrentServer(any()) }
    }

    @Test
    fun remove_activeProfile_endsSessionAndClearsState() = runTest {
        val wasActive = switcher.remove(jellyfinProfile.id)

        assertTrue(wasActive)
        coVerify { secureCredentialManager.clearPassword("https://jellyfin.example.com", "alice") }
        coVerify { cache.clearAllCache() }
        coVerify { activeSessionStore.clear() }
        verify { authRepository.seedCurrentServer(null) }
    }

    @Test
    fun remove_unknownProfile_returnsFalseAndTouchesNothing() = runTest {
        coEvery { profileRepository.remove("missing") } returns null

        assertFalse(switcher.remove("missing"))
        coVerify(exactly = 0) { secureCredentialManager.clearPassword(any(), any()) }
    }

    @Test
    fun suspendActiveSession_endsSessionButKeepsProfileAndPassword() = runTest {
        switcher.suspendActiveSession()

        coVerify { cache.clearAllCache() }
        coVerify { activeSessionStore.clear() }
        verify { authRepository.seedCurrentServer(null) }
        coVerify(exactly = 0) { profileRepository.remove(any()) }
        coVerify(exactly = 0) { secureCredentialManager.clearPassword(any(), any()) }
    }
}
