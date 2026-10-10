package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.data.BiometricCapability
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.credentials.PasswordCredentialSyncManager
import com.rpeters.jellyfin.data.repository.IJellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.security.CertificatePinningManager
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.BIOMETRIC_AUTH_ENABLED
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.BIOMETRIC_REQUIRE_STRONG
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.REMEMBER_LOGIN
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.SERVER_URL
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.SESSION_LOGIN_TIMESTAMP
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.SESSION_SERVER_ID
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.SESSION_TOKEN
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.SESSION_USER_ID
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.USERNAME
import com.rpeters.jellyfin.ui.components.ConnectionPhase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import com.rpeters.jellyfin.data.common.TestDispatcherProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServerConnectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: IJellyfinRepository
    private lateinit var authRepository: IJellyfinAuthRepository
    private lateinit var secureCredentialManager: SecureCredentialManager
    private lateinit var passwordCredentialSyncManager: PasswordCredentialSyncManager
    private lateinit var certificatePinningManager: CertificatePinningManager
    private lateinit var connectivityChecker: com.rpeters.jellyfin.network.ConnectivityChecker
    private lateinit var offlineDownloadManager: com.rpeters.jellyfin.data.offline.OfflineDownloadManager
    private lateinit var offlineDownloadManagerProvider: Provider<com.rpeters.jellyfin.data.offline.OfflineDownloadManager>
    private lateinit var discoveryRepository: com.rpeters.jellyfin.data.repository.IJellyfinDiscoveryRepository
    private lateinit var context: Context
    private lateinit var viewModel: ServerConnectionViewModel
    private lateinit var defaultProfileRepository: com.rpeters.jellyfin.data.preferences.ServerProfileRepository

    @Before
    fun setUp() = runTest {
        unmockkAll()
        MockKAnnotations.init(this)
        defaultProfileRepository = mockk(relaxed = true)
        
        context = ApplicationProvider.getApplicationContext()
        repository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        every { authRepository.getCurrentServerSync() } returns null
        secureCredentialManager = mockk(relaxed = true)
        passwordCredentialSyncManager = mockk(relaxed = true)
        certificatePinningManager = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
        discoveryRepository = mockk(relaxed = true)
        offlineDownloadManager = mockk(relaxed = true)
        offlineDownloadManagerProvider = Provider { offlineDownloadManager }
        every { discoveryRepository.discoverServers() } returns flowOf(emptyList())
        every { connectivityChecker.observeNetworkConnectivity() } returns flowOf(true)
        every { connectivityChecker.isOnline() } returns true
        every { offlineDownloadManager.downloads } returns MutableStateFlow(emptyList())

        every { repository.isConnectedFlow } returns MutableStateFlow(false)
        val mockServer = mockk<com.rpeters.jellyfin.data.JellyfinServer>(relaxed = true)
        every { mockServer.url } returns "https://example.com"
        every { mockServer.username } returns "user"
        every { mockServer.accessToken } returns "token"
        every { mockServer.serverType } returns com.rpeters.jellyfin.data.model.ServerType.JELLYFIN
        every { repository.currentServerFlow } returns MutableStateFlow(mockServer)
        
        every { authRepository.isTokenExpired() } returns false
        
        val strongCapability = BiometricCapability(
            authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            isAvailable = true,
            isStrongSupported = true,
            isWeakOnly = false,
            allowsDeviceCredentialFallback = true,
            status = BiometricManager.BIOMETRIC_SUCCESS,
        )
        every { secureCredentialManager.isBiometricAuthAvailable(any()) } returns true
        every { secureCredentialManager.getBiometricCapability(any()) } returns strongCapability
        coEvery { secureCredentialManager.hasSavedPassword("https://example.com", "user") } returns true

        context.dataStore.edit { preferences ->
            preferences.clear()
            preferences[SERVER_URL] = "https://example.com"
            preferences[USERNAME] = "user"
            preferences[REMEMBER_LOGIN] = true
            preferences[BIOMETRIC_AUTH_ENABLED] = true
        }
    }

    @After
    fun tearDown() = runTest(mainDispatcherRule.dispatcher) {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        }
        unmockkAll()
    }

    @Test
    fun autoLoginWithBiometric_triggersConnectToServer_whenAuthenticationSucceeds() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { secureCredentialManager.getPassword("https://example.com", "user") } returns null
            coEvery { secureCredentialManager.getPassword("https://example.com", "user", null, any()) } returns null
            coEvery {
                secureCredentialManager.getPassword(
                    "https://example.com",
                    "user",
                    any<FragmentActivity>(),
                    any(),
                )
            } returns "biometricPassword"
            coEvery { secureCredentialManager.savePassword(any(), any(), any()) } returns Unit

            coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(
                mockk<PublicSystemInfo>(relaxed = true),
            )
            coEvery {
                authRepository.authenticateUser(any(), any(), any())
            } returns ApiResult.Success(mockk<AuthenticationResult>(relaxed = true))

            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            awaitCondition {
                viewModel.connectionState.value.savedServerUrl == "https://example.com"
            }
            advanceUntilIdle()

            val fragmentActivity = mockk<FragmentActivity>(relaxed = true)

            viewModel.autoLoginWithBiometric(fragmentActivity)

            advanceUntilIdle()

            coVerify(exactly = 1) {
                authRepository.authenticateUser("https://example.com", "user", "biometricPassword")
            }

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun setRequireStrongBiometric_updatesStateAndPreference() = runTest(mainDispatcherRule.dispatcher) {
        val weakOnlyCapability = BiometricCapability(
            authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK,
            isAvailable = false,
            isStrongSupported = false,
            isWeakOnly = true,
            allowsDeviceCredentialFallback = true,
            status = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        )
        every { secureCredentialManager.getBiometricCapability(any()) } returns weakOnlyCapability
        every { connectivityChecker.observeNetworkConnectivity() } returns emptyFlow()

        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            passwordCredentialSyncManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(mainDispatcherRule.dispatcher),
            serverProfileRepository = defaultProfileRepository,
        )
        awaitCondition {
            viewModel.connectionState.value.savedServerUrl == "https://example.com"
        }
        advanceUntilIdle()

        viewModel.setRequireStrongBiometric(true)

        val connectionState = viewModel.connectionState.first { it.requireStrongBiometric }

        val preferences = context.dataStore.data.first()
        assertTrue(preferences[BIOMETRIC_REQUIRE_STRONG] == true)
        assertTrue(connectionState.requireStrongBiometric)
        assertFalse(connectionState.isUsingWeakBiometric)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun init_persistsRememberLoginDefaultForFirstTimeUser() = runTest(mainDispatcherRule.dispatcher) {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        every { repository.isConnectedFlow } returns MutableStateFlow(false)

        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            passwordCredentialSyncManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(mainDispatcherRule.dispatcher),
            serverProfileRepository = defaultProfileRepository,
        )
        awaitCondition(timeoutMs = 5000) {
            val preferences = context.dataStore.data.first()
            preferences[REMEMBER_LOGIN] == true
        }
        advanceUntilIdle()

        val preferences = context.dataStore.data.first()
        assertTrue(preferences[REMEMBER_LOGIN] == true)
        assertTrue(viewModel.connectionState.value.rememberLogin)
        assertNull(preferences[BIOMETRIC_AUTH_ENABLED])
        assertFalse(viewModel.connectionState.value.isBiometricAuthEnabled)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun autoLoginWithBiometric_respectsStrongBiometricRequirement() =
        runTest(mainDispatcherRule.dispatcher) {
            context.dataStore.edit { preferences ->
                preferences[BIOMETRIC_REQUIRE_STRONG] = true
                preferences[BIOMETRIC_AUTH_ENABLED] = true
            }
            coEvery { secureCredentialManager.getPassword("https://example.com", "user") } returns null
            coEvery { secureCredentialManager.getPassword("https://example.com", "user", null, any()) } returns null
            coEvery {
                secureCredentialManager.getPassword(
                    "https://example.com",
                    "user",
                    any<FragmentActivity>(),
                    true,
                )
            } returns "biometricPassword"
            coEvery { secureCredentialManager.savePassword(any(), any(), any()) } returns Unit
            coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(
                mockk<PublicSystemInfo>(relaxed = true),
            )
            coEvery {
                authRepository.authenticateUser(any(), any(), any())
            } returns ApiResult.Success(mockk<AuthenticationResult>(relaxed = true))

            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            awaitCondition {
                viewModel.connectionState.value.savedServerUrl == "https://example.com"
            }
            advanceUntilIdle()

            val fragmentActivity = mockk<FragmentActivity>(relaxed = true)

            viewModel.autoLoginWithBiometric(fragmentActivity)

            advanceUntilIdle()

            coVerify {
                secureCredentialManager.getPassword(
                    "https://example.com",
                    "user",
                    any<FragmentActivity>(),
                    true,
                )
            }

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun connectToServer_respectsUserChoiceWhenRememberLoginDisabled() = runTest(mainDispatcherRule.dispatcher) {
        context.dataStore.edit { preferences ->
            preferences.clear()
            preferences[REMEMBER_LOGIN] = false
        }
        every { repository.isConnectedFlow } returns MutableStateFlow(false)
        coEvery { authRepository.testServerConnection("https://example.com") } returns ApiResult.Success(
            mockk<PublicSystemInfo>(relaxed = true),
        )
        coEvery {
            authRepository.authenticateUser("https://example.com", "user", "password")
        } returns ApiResult.Success(mockk<AuthenticationResult>(relaxed = true))
        coJustRun { secureCredentialManager.savePassword(any(), any(), any()) }

        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            passwordCredentialSyncManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(mainDispatcherRule.dispatcher),
            serverProfileRepository = defaultProfileRepository,
        )
        awaitCondition {
            !viewModel.connectionState.value.rememberLogin
        }
        advanceUntilIdle()

        viewModel.connectToServer("https://example.com", "user", "password")

        advanceUntilIdle()

        val preferences = context.dataStore.data.first()
        assertFalse(preferences[REMEMBER_LOGIN] == true)
        coVerify(exactly = 0) { secureCredentialManager.savePassword(any(), any(), any()) }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `testServerConnection handles various URL formats`() = runTest(mainDispatcherRule.dispatcher) {
        val formats = listOf("192.168.1.1", "http://jellyfin:8096", "jellyfin.local")
        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            passwordCredentialSyncManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(mainDispatcherRule.dispatcher),
            serverProfileRepository = defaultProfileRepository,
        )
        awaitCondition {
            viewModel.connectionState.value.savedServerUrl == "https://example.com"
        }
        advanceUntilIdle()

        formats.forEach { url ->
            coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(mockk(relaxed = true))
            coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Success(mockk(relaxed = true))
            
            viewModel.connectToServer(url, "user", "pass")
            advanceUntilIdle()
            
            // Verify normalization (implementation detail, but observable via repo call)
            coVerify { authRepository.testServerConnection(match { it.startsWith("http") }) }
            
            // Reset for next iteration
            viewModel.logout()
            advanceUntilIdle()
        }
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `connection phases transition correctly`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(mockk(relaxed = true))
        coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Success(mockk(relaxed = true))

        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            passwordCredentialSyncManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(mainDispatcherRule.dispatcher),
            serverProfileRepository = defaultProfileRepository,
        )
        awaitCondition {
            viewModel.connectionState.value.savedServerUrl == "https://example.com"
        }
        advanceUntilIdle()

        viewModel.connectToServer("https://example.com", "user", "pass")
        
        // Wait for the Connected state (this handles the background work on real IO dispatcher)
        val finalState = viewModel.connectionState.first { it.connectionPhase == ConnectionPhase.Connected }
        assertEquals(ConnectionPhase.Connected, finalState.connectionPhase)
        
        viewModel.viewModelScope.cancel()
    }

    // region Session restore tests (regression for issue #1094)

    @Test
    fun sessionRestore_freshSession_callsRestorePersistedSession() =
        runTest(mainDispatcherRule.dispatcher) {
            // A session 1 hour old is well within the 30-day window — restore must be called.
            val recentTimestamp = System.currentTimeMillis() - (60 * 60 * 1000L)
            context.dataStore.edit { preferences ->
                preferences[SESSION_TOKEN] = "existing-token"
                preferences[SESSION_USER_ID] = "user-id-1"
                preferences[SESSION_SERVER_ID] = "server-id-1"
                preferences[SESSION_LOGIN_TIMESTAMP] = recentTimestamp
            }

            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            awaitCondition { viewModel.connectionState.value.savedServerUrl == "https://example.com" }
            advanceUntilIdle()

            // The fix requires restorePersistedSession to be called (not skipped or cleared).
            coVerify(exactly = 1) { authRepository.restorePersistedSession(any()) }
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun sessionRestore_staleSession_doesNotCallRestorePersistedSession() =
        runTest(mainDispatcherRule.dispatcher) {
            // A session older than SESSION_TOKEN_MAX_AGE_MS (30 days) must be discarded before
            // restorePersistedSession() is called — this prevents the race condition where the
            // repository emits connected=true and we then clear the DataStore token underneath.
            val staleTimestamp = System.currentTimeMillis() - (Constants.SESSION_TOKEN_MAX_AGE_MS + 1000L)
            context.dataStore.edit { preferences ->
                preferences[SESSION_TOKEN] = "stale-token"
                preferences[SESSION_USER_ID] = "user-id-1"
                preferences[SESSION_SERVER_ID] = "server-id-1"
                preferences[SESSION_LOGIN_TIMESTAMP] = staleTimestamp
            }

            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            awaitCondition { viewModel.connectionState.value.savedServerUrl == "https://example.com" }
            advanceUntilIdle()

            // Critical: restorePersistedSession must NEVER be called for a stale session.
            // If it were, the connected state would be set before the token is cleared,
            // leaving the home screen in a broken state (images fail, session has no token).
            coVerify(exactly = 0) { authRepository.restorePersistedSession(any()) }
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun sessionRestore_trailingSlashUrl_normalizesUrlBeforeRestore() =
        runTest(mainDispatcherRule.dispatcher) {
            // Legacy DataStore values saved with trailing slashes must be normalized before the
            // JellyfinServer object is built. Without this, getImageUrl() produces double-slash
            // paths (e.g. "server//Items/…") that the server rejects with 404.
            context.dataStore.edit { preferences ->
                preferences[SERVER_URL] = "http://jellyfin.local:8096/"
                preferences[USERNAME] = "user"
                preferences[SESSION_TOKEN] = "token-abc"
                preferences[SESSION_USER_ID] = "uid"
                preferences[SESSION_SERVER_ID] = "sid"
                preferences[SESSION_LOGIN_TIMESTAMP] = System.currentTimeMillis()
            }
            coEvery {
                secureCredentialManager.hasSavedPassword("http://jellyfin.local:8096/", "user")
            } returns false
            coEvery {
                secureCredentialManager.hasSavedPassword("http://jellyfin.local:8096", "user")
            } returns false

            val capturedServer = slot<JellyfinServer>()
            coEvery { authRepository.restorePersistedSession(capture(capturedServer)) } just Runs

            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            awaitCondition { capturedServer.isCaptured }
            advanceUntilIdle()

            assertFalse(capturedServer.captured.url.endsWith("/"))
            assertFalse(capturedServer.captured.normalizedUrl?.endsWith("/") == true)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `logout clears saved credentials, resets connection state and calls authRepository logout`() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { authRepository.logout() } just Runs

            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            advanceUntilIdle()

            viewModel.logout()
            awaitCondition {
                runCatching { coVerify { authRepository.logout() } }.isSuccess
            }
            advanceUntilIdle()

            val state = viewModel.connectionState.value
            assertFalse(state.isConnected)
            assertFalse(state.isConnecting)
            assertEquals("", state.savedServerUrl)
            assertEquals("", state.savedUsername)
            assertFalse(state.hasSavedPassword)

            coVerify { authRepository.logout() }
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun connectToServer_withDemoModeKeyword_activatesDemoModeInsteadOfConnecting() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            advanceUntilIdle()

            viewModel.connectToServer("demo.mode", "", "")
            advanceUntilIdle()

            val state = viewModel.connectionState.value
            assertTrue(state.isDemoMode)
            assertTrue(state.isConnected)
            coVerify(exactly = 0) { authRepository.testServerConnection(any()) }
            coVerify(exactly = 0) { authRepository.authenticateUser(any(), any(), any()) }

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun connectToServer_withDemoModeKeyword_isCaseInsensitiveAndTrimmed() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel = ServerConnectionViewModel(
                repository,
                authRepository,
                secureCredentialManager,
                passwordCredentialSyncManager,
                certificatePinningManager,
                connectivityChecker,
                discoveryRepository,
                offlineDownloadManagerProvider,
                context,
                TestDispatcherProvider(mainDispatcherRule.dispatcher),
                serverProfileRepository = defaultProfileRepository,
            )
            advanceUntilIdle()

            viewModel.connectToServer("  Demo.Mode  ", "", "")
            advanceUntilIdle()

            assertTrue(viewModel.connectionState.value.isDemoMode)

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun exitDemoMode_deactivatesDemoModeAndDisconnects() = runTest(mainDispatcherRule.dispatcher) {
        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            passwordCredentialSyncManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(mainDispatcherRule.dispatcher),
            serverProfileRepository = defaultProfileRepository,
        )
        advanceUntilIdle()

        viewModel.connectToServer("demo.mode", "", "")
        advanceUntilIdle()
        assertTrue(viewModel.connectionState.value.isDemoMode)

        viewModel.exitDemoMode()
        advanceUntilIdle()

        val state = viewModel.connectionState.value
        assertFalse(state.isDemoMode)
        assertFalse(state.isConnected)

        viewModel.viewModelScope.cancel()
    }

    // endregion

    @Test
    fun embyConnect_cancelledSelection_doesNotClearNewSelectionsLoadingState() = runTest(mainDispatcherRule.dispatcher) {
        context.dataStore.edit { it[REMEMBER_LOGIN] = false }
        val pending = kotlinx.coroutines.CompletableDeferred<ApiResult<AuthenticationResult>>()
        coEvery { authRepository.authenticateWithEmbyConnect(any(), any(), any(), any()) } coAnswers { pending.await() }
        viewModel = createViewModelWithProfiles(defaultProfileRepository)
        awaitCondition { viewModel.connectionState.value.isLocalCredentialCheckComplete }
        val linked = com.rpeters.jellyfin.data.emby.EmbyConnectServer(
            "NAS", "server", listOf("https://example.com"), "connect-user", "linked-key",
        )
        viewModel.selectEmbyConnectServer(linked)
        runCurrent()
        viewModel.cancelEmbyConnect()
        viewModel.selectEmbyConnectServer(linked)
        runCurrent()
        assertTrue(viewModel.connectionState.value.isConnecting)
        assertTrue(viewModel.embyConnectState.value.isBusy)
        viewModel.cancelEmbyConnect()
        runCurrent()
        assertFalse(viewModel.connectionState.value.isConnecting)
        assertFalse(viewModel.embyConnectState.value.isBusy)
    }

    @Test
    fun embyConnect_rememberLoginOff_forgetsSavedProfileAndSessionKeys() = runTest(mainDispatcherRule.dispatcher) {
        val server = JellyfinServer(
            id = "emby-server", name = "NAS", url = "https://example.com", userId = "emby-user", username = "user",
            accessToken = "local-token", serverType = com.rpeters.jellyfin.data.model.ServerType.EMBY,
            embyConnectUserId = "connect-user", embyConnectAccessKey = "linked-key",
        )
        context.dataStore.edit { it[REMEMBER_LOGIN] = false }
        every { authRepository.getCurrentServerSync() } returns server
        coEvery { authRepository.authenticateWithEmbyConnect(any(), any(), any(), any()) } returns
            ApiResult.Success(mockk<AuthenticationResult>())
        viewModel = createViewModelWithProfiles(defaultProfileRepository)
        awaitCondition { viewModel.connectionState.value.isLocalCredentialCheckComplete }
        viewModel.selectEmbyConnectServer(com.rpeters.jellyfin.data.emby.EmbyConnectServer(
            "NAS", "emby-server", listOf("https://example.com"), "connect-user", "linked-key",
        ))
        awaitCondition { viewModel.connectionState.value.isConnected }
        assertFalse(viewModel.connectionState.value.rememberLogin)
        assertNull(context.dataStore.data.first()[SESSION_TOKEN])
        assertNull(context.dataStore.data.first()[PreferencesKeys.EMBY_CONNECT_ACCESS_KEY])
        coVerify { defaultProfileRepository.clearAuthentication("emby-server:emby-user") }
    }

    @Test
    fun embyConnect_staleTokenOnLaunch_usesLinkedKeyInsteadOfDiscardingSession() = runTest(mainDispatcherRule.dispatcher) {
        context.dataStore.edit {
            it[SESSION_TOKEN] = "stale-token"
            it[SESSION_USER_ID] = "emby-user"
            it[SESSION_SERVER_ID] = "emby-server"
            it[SESSION_LOGIN_TIMESTAMP] = 1L
            it[PreferencesKeys.SESSION_SERVER_TYPE] = "EMBY"
            it[PreferencesKeys.EMBY_CONNECT_USER_ID] = "connect-user"
            it[PreferencesKeys.EMBY_CONNECT_ACCESS_KEY] = "linked-key"
        }
        val refreshed = JellyfinServer(
            "emby-server", "NAS", "https://example.com", userId = "emby-user", username = "user",
            accessToken = "fresh-token", loginTimestamp = System.currentTimeMillis(),
            serverType = com.rpeters.jellyfin.data.model.ServerType.EMBY,
            embyConnectUserId = "connect-user", embyConnectAccessKey = "linked-key",
        )
        every { authRepository.getCurrentServerSync() } returns refreshed
        coEvery { authRepository.authenticateWithEmbyConnect(any(), any(), any(), any()) } returns
            ApiResult.Success(mockk<AuthenticationResult>())
        viewModel = createViewModelWithProfiles(defaultProfileRepository)
        awaitCondition { viewModel.connectionState.value.isConnected }
        assertEquals("fresh-token", context.dataStore.data.first()[SESSION_TOKEN])
        coVerify { authRepository.authenticateWithEmbyConnect("https://example.com", "connect-user", "linked-key", "emby-server") }
        coVerify(exactly = 0) { defaultProfileRepository.clearAuthentication(any()) }
    }

    // region Saved server profiles

    private fun createViewModelWithProfiles(
        profileRepository: com.rpeters.jellyfin.data.preferences.ServerProfileRepository,
    ) = ServerConnectionViewModel(
        repository,
        authRepository,
        secureCredentialManager,
        passwordCredentialSyncManager,
        certificatePinningManager,
        connectivityChecker,
        discoveryRepository,
        offlineDownloadManagerProvider,
        context,
        TestDispatcherProvider(mainDispatcherRule.dispatcher),
        serverProfileRepository = profileRepository,
    )

    @Test
    fun sessionRestore_legacySingleSession_becomesSavedProfile() =
        runTest(mainDispatcherRule.dispatcher) {
            val profileRepository = mockk<com.rpeters.jellyfin.data.preferences.ServerProfileRepository>(relaxed = true)
            val saved = slot<com.rpeters.jellyfin.data.model.ServerProfile>()
            coEvery { profileRepository.saveAndActivate(capture(saved)) } just Runs
            context.dataStore.edit { preferences ->
                preferences[SESSION_TOKEN] = "existing-token"
                preferences[SESSION_USER_ID] = "user-id-1"
                preferences[SESSION_SERVER_ID] = "server-id-1"
                preferences[SESSION_LOGIN_TIMESTAMP] = System.currentTimeMillis() - (60 * 60 * 1000L)
            }

            viewModel = createViewModelWithProfiles(profileRepository)
            awaitCondition { saved.isCaptured }
            advanceUntilIdle()

            assertEquals("server-id-1:user-id-1", saved.captured.id)
            assertEquals("https://example.com", saved.captured.serverUrl)
            assertEquals("user", saved.captured.username)
            assertEquals("existing-token", saved.captured.accessToken)
            assertEquals(com.rpeters.jellyfin.data.model.ServerType.JELLYFIN, saved.captured.serverType)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun sessionRestore_staleSession_clearsProfileTokenInsteadOfSavingProfile() =
        runTest(mainDispatcherRule.dispatcher) {
            val profileRepository = mockk<com.rpeters.jellyfin.data.preferences.ServerProfileRepository>(relaxed = true)
            coEvery { secureCredentialManager.hasSavedPassword(any(), any()) } returns false
            context.dataStore.edit { preferences ->
                preferences[SESSION_TOKEN] = "existing-token"
                preferences[SESSION_USER_ID] = "user-id-1"
                preferences[SESSION_SERVER_ID] = "server-id-1"
                preferences[SESSION_LOGIN_TIMESTAMP] =
                    System.currentTimeMillis() - Constants.SESSION_TOKEN_MAX_AGE_MS - 1_000L
            }

            viewModel = createViewModelWithProfiles(profileRepository)
            awaitCondition {
                runCatching { coVerify { profileRepository.clearToken("server-id-1:user-id-1") } }.isSuccess
            }
            advanceUntilIdle()

            coVerify(exactly = 0) { profileRepository.saveAndActivate(any()) }
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun connectToServer_rememberLoginOn_savesSignedInServerAsProfile() =
        runTest(mainDispatcherRule.dispatcher) {
            val profileRepository = mockk<com.rpeters.jellyfin.data.preferences.ServerProfileRepository>(relaxed = true)
            val saved = slot<com.rpeters.jellyfin.data.model.ServerProfile>()
            coEvery { profileRepository.saveAndActivate(capture(saved)) } just Runs
            coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(mockk(relaxed = true))
            coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Success(mockk(relaxed = true))
            every { repository.currentServerFlow } returns MutableStateFlow(
                JellyfinServer(
                    id = "server-id-2",
                    name = "Second",
                    url = "https://example.com",
                    userId = "user-id-2",
                    username = "user",
                    accessToken = "new-token",
                    loginTimestamp = 5_000L,
                ),
            )

            viewModel = createViewModelWithProfiles(profileRepository)
            awaitCondition { viewModel.connectionState.value.savedServerUrl == "https://example.com" }
            advanceUntilIdle()
            viewModel.connectToServer("https://example.com", "user", "pass")
            viewModel.connectionState.first { it.connectionPhase == ConnectionPhase.Connected }

            assertEquals("server-id-2:user-id-2", saved.captured.id)
            assertEquals("new-token", saved.captured.accessToken)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun connectToServer_profileSaveFails_stillConnects() =
        runTest(mainDispatcherRule.dispatcher) {
            val profileRepository = mockk<com.rpeters.jellyfin.data.preferences.ServerProfileRepository>(relaxed = true)
            coEvery { profileRepository.saveAndActivate(any()) } throws java.io.IOException("disk full")
            coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(mockk(relaxed = true))
            coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Success(mockk(relaxed = true))
            every { repository.currentServerFlow } returns MutableStateFlow(
                JellyfinServer(id = "s", name = "S", url = "https://example.com", userId = "u", username = "user", accessToken = "t"),
            )

            viewModel = createViewModelWithProfiles(profileRepository)
            awaitCondition { viewModel.connectionState.value.savedServerUrl == "https://example.com" }
            advanceUntilIdle()
            viewModel.connectToServer("https://example.com", "user", "pass")
            val finalState = viewModel.connectionState.first { it.connectionPhase == ConnectionPhase.Connected }

            assertTrue(finalState.isConnected)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun logout_removesOnlyTheCurrentSessionsProfile() =
        runTest(mainDispatcherRule.dispatcher) {
            val profileRepository = mockk<com.rpeters.jellyfin.data.preferences.ServerProfileRepository>(relaxed = true)
            coEvery { authRepository.logout() } just Runs
            every { authRepository.getCurrentServerSync() } returns JellyfinServer(
                id = "server-id-1",
                name = "First",
                url = "https://example.com",
                userId = "user-id-1",
                username = "user",
                accessToken = "token",
            )

            viewModel = createViewModelWithProfiles(profileRepository)
            advanceUntilIdle()
            viewModel.logout()
            awaitCondition { runCatching { coVerify { authRepository.logout() } }.isSuccess }
            advanceUntilIdle()

            coVerify(exactly = 1) { profileRepository.remove("server-id-1:user-id-1") }
            coVerify(exactly = 1) { profileRepository.remove(any()) }
            viewModel.viewModelScope.cancel()
        }

    // endregion

    private suspend fun awaitCondition(timeoutMs: Long = 2000, condition: suspend () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) return
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                kotlinx.coroutines.delay(10)
            }
        }
        if (!condition()) {
            val details = if (::viewModel.isInitialized) {
                "connectionState=${viewModel.connectionState.value}, embyState=${viewModel.embyConnectState.value}"
            } else "viewModel not initialized"
            throw AssertionError("Condition not met within $timeoutMs ms. $details")
        }
    }
}
