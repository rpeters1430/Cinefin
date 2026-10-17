package com.rpeters.jellyfin.ui.viewmodel

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.core.FeatureFlags
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.plex.PlexAuthDataSource
import com.rpeters.jellyfin.data.plex.PlexConnectionSelector
import com.rpeters.jellyfin.data.plex.PlexResource
import com.rpeters.jellyfin.data.plex.PlexServerAddress
import com.rpeters.jellyfin.data.plex.PlexSignInState
import com.rpeters.jellyfin.data.plex.PlexTvClient
import com.rpeters.jellyfin.data.plex.PlexTvUser
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.session.ProfileSwitcher
import com.rpeters.jellyfin.utils.SecureLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@UnstableApi
class PlexSignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authDataSource: PlexAuthDataSource = mockk()
    private val plexTvClient: PlexTvClient = mockk()
    private val connectionSelector: PlexConnectionSelector = mockk()
    private val profileSwitcher: ProfileSwitcher = mockk()
    private val remoteConfig: RemoteConfigRepository = mockk()

    private val owned = resource("Living Room", MACHINE_OWNED, owned = true, online = true)
    private val sharedOffline = resource("Dad's PMS", MACHINE_SHARED, owned = false, online = false, sourceTitle = "dad")
    private val sharedOnline = resource("Aunt's PMS", "machine-shared-2", owned = false, online = true)

    @Before
    fun setUp() {
        mockkObject(SecureLogger)
        every { SecureLogger.w(any(), any<String>(), any()) } returns Unit
        every { remoteConfig.getBoolean(FeatureFlags.Experimental.ENABLE_PLEX_SUPPORT) } returns true
        coEvery { plexTvClient.getUser(ACCOUNT_TOKEN) } returns ApiResult.Success(USER)
        coEvery { plexTvClient.getServers(ACCOUNT_TOKEN) } returns
            ApiResult.Success(listOf(sharedOffline, owned, sharedOnline))
        coEvery { profileSwitcher.addAndSwitch(any()) } answers { firstArg() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun start_approved_listsServersOwnedThenOnlineFirst() = runTest(mainDispatcherRule.dispatcher) {
        signsInWith(PlexSignInState.SignedIn(ACCOUNT_TOKEN))
        val viewModel = viewModel()

        viewModel.start()
        advanceUntilIdle()

        val step = viewModel.step.value as PlexSignInStep.ChooseServer
        assertEquals(listOf(MACHINE_OWNED, "machine-shared-2", MACHINE_SHARED), step.servers.map { it.id })
        assertEquals("dad", step.servers.last().ownerName)
        assertEquals(null, step.servers.first().ownerName)
    }

    @Test
    fun start_waitingForApproval_showsCodeAndLink() = runTest(mainDispatcherRule.dispatcher) {
        val states = MutableSharedFlow<PlexSignInState>()
        every { authDataSource.signIn() } returns states
        val viewModel = viewModel()

        viewModel.start()
        advanceUntilIdle()
        states.emit(PlexSignInState.WaitingForApproval("AB12", AUTH_URL))
        advanceUntilIdle()

        assertEquals(PlexSignInStep.WaitingForApproval("AB12", AUTH_URL), viewModel.step.value)
    }

    @Test
    fun start_pinExpires_showsExpired() = runTest(mainDispatcherRule.dispatcher) {
        signsInWith(PlexSignInState.Expired)
        val viewModel = viewModel()

        viewModel.start()
        advanceUntilIdle()

        assertEquals(PlexSignInStep.Expired, viewModel.step.value)
    }

    @Test
    fun start_serverListFails_showsItsMessage() = runTest(mainDispatcherRule.dispatcher) {
        signsInWith(PlexSignInState.SignedIn(ACCOUNT_TOKEN))
        coEvery { plexTvClient.getServers(ACCOUNT_TOKEN) } returns
            ApiResult.Error("Could not reach plex.tv", errorType = ErrorType.NETWORK)
        val viewModel = viewModel()

        viewModel.start()
        advanceUntilIdle()

        assertEquals(PlexSignInStep.Failed("Could not reach plex.tv"), viewModel.step.value)
    }

    @Test
    fun start_accountWithoutServers_showsNoServers() = runTest(mainDispatcherRule.dispatcher) {
        signsInWith(PlexSignInState.SignedIn(ACCOUNT_TOKEN))
        coEvery { plexTvClient.getServers(ACCOUNT_TOKEN) } returns ApiResult.Success(emptyList())
        val viewModel = viewModel()

        viewModel.start()
        advanceUntilIdle()

        assertEquals(PlexSignInStep.NoServers, viewModel.step.value)
    }

    @Test
    fun selectServer_reachable_savesPlexProfileAndSwitchesToIt() = runTest(mainDispatcherRule.dispatcher) {
        signsInWith(PlexSignInState.SignedIn(ACCOUNT_TOKEN))
        coEvery { connectionSelector.select(owned) } returns PlexServerAddress(LOCAL, listOf(REMOTE))
        val saved = slot<ServerProfile>()
        coEvery { profileSwitcher.addAndSwitch(capture(saved)) } answers { firstArg() }
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.selectServer(MACHINE_OWNED)
        advanceUntilIdle()

        with(saved.captured) {
            assertEquals(ServerType.PLEX, serverType)
            assertEquals(MACHINE_OWNED, serverId)
            assertEquals(LOCAL, serverUrl)
            assertEquals(listOf(REMOTE), alternateServerUrls)
            assertEquals("server-token-$MACHINE_OWNED", accessToken)
            assertEquals(ACCOUNT_TOKEN, plexAccountToken)
            assertEquals("Carol", username)
        }
        assertEquals(PlexSignInStep.Idle, viewModel.step.value)
    }

    @Test
    fun selectServer_unreachable_staysOnPickerWithProblem() = runTest(mainDispatcherRule.dispatcher) {
        signsInWith(PlexSignInState.SignedIn(ACCOUNT_TOKEN))
        coEvery { connectionSelector.select(sharedOffline) } returns null
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.selectServer(MACHINE_SHARED)
        advanceUntilIdle()

        val step = viewModel.step.value as PlexSignInStep.ChooseServer
        assertEquals(PlexServerProblem.UNREACHABLE, step.problem)
        assertEquals("Dad's PMS", step.problemServerName)
        assertEquals(null, step.connectingTo)
        coVerify(exactly = 0) { profileSwitcher.addAndSwitch(any()) }
    }

    @Test
    fun selectServer_sharedWithoutToken_reportsNoAccess() = runTest(mainDispatcherRule.dispatcher) {
        val noToken = sharedOnline.copy(accessToken = null)
        coEvery { plexTvClient.getServers(ACCOUNT_TOKEN) } returns ApiResult.Success(listOf(noToken))
        signsInWith(PlexSignInState.SignedIn(ACCOUNT_TOKEN))
        coEvery { connectionSelector.select(noToken) } returns PlexServerAddress(REMOTE, emptyList())
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.selectServer(noToken.clientIdentifier)
        advanceUntilIdle()

        val step = viewModel.step.value as PlexSignInStep.ChooseServer
        assertEquals(PlexServerProblem.NO_ACCESS, step.problem)
        coVerify(exactly = 0) { profileSwitcher.addAndSwitch(any()) }
    }

    @Test
    fun cancel_whileWaiting_returnsToIdleAndStopsListening() = runTest(mainDispatcherRule.dispatcher) {
        val states = MutableSharedFlow<PlexSignInState>()
        every { authDataSource.signIn() } returns states
        val viewModel = viewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.cancel()
        advanceUntilIdle()

        assertEquals(PlexSignInStep.Idle, viewModel.step.value)
        assertEquals(0, states.subscriptionCount.value)
    }

    @Test
    fun isAvailable_followsTheFeatureFlag() = runTest(mainDispatcherRule.dispatcher) {
        every { remoteConfig.getBoolean(FeatureFlags.Experimental.ENABLE_PLEX_SUPPORT) } returns false

        assertTrue(!viewModel().isAvailable)
    }

    private fun signsInWith(vararg states: PlexSignInState) {
        every { authDataSource.signIn() } returns flowOf(*states)
    }

    private fun TestScope.viewModel() = PlexSignInViewModel(
        authDataSource,
        plexTvClient,
        connectionSelector,
        profileSwitcher,
        remoteConfig,
        backgroundScope,
    )

    private fun resource(
        name: String,
        machineId: String,
        owned: Boolean,
        online: Boolean,
        sourceTitle: String? = null,
    ) = PlexResource(
        name = name,
        clientIdentifier = machineId,
        provides = "server",
        owned = owned,
        accessToken = "server-token-$machineId",
        presence = online,
        sourceTitle = sourceTitle,
    )

    private companion object {
        const val ACCOUNT_TOKEN = "account-token"
        const val MACHINE_OWNED = "machine-owned-1"
        const val MACHINE_SHARED = "machine-shared-1"
        const val AUTH_URL = "https://app.plex.tv/auth#?code=AB12"
        const val LOCAL = "https://10-0-0-5.abc123.plex.direct:32400"
        const val REMOTE = "https://203-0-113-9.abc123.plex.direct:32400"
        val USER = PlexTvUser(id = 12345, username = "carol", title = "Carol")
    }
}
