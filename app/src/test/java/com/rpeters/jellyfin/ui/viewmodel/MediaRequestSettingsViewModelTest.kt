package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.CinefinPluginCredentialsResponse
import com.rpeters.jellyfin.data.model.CinefinPluginInfoResponse
import com.rpeters.jellyfin.data.model.CinefinPluginProxyCredentials
import com.rpeters.jellyfin.data.model.CinefinPluginRequestResponse
import com.rpeters.jellyfin.data.model.CinefinPluginServiceCredentials
import com.rpeters.jellyfin.data.model.QuickConnectResult
import com.rpeters.jellyfin.data.model.QuickConnectState
import com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository
import com.rpeters.jellyfin.data.preferences.RadarrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.preferences.SonarrPreferences
import com.rpeters.jellyfin.data.repository.CinefinPluginRepository
import com.rpeters.jellyfin.data.repository.IJellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.RadarrRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
import com.rpeters.jellyfin.data.repository.SonarrRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRequestSettingsViewModelTest {

    @MockK(relaxed = true)
    private lateinit var seerrPrefsRepo: SeerrPreferencesRepository

    @MockK(relaxed = true)
    private lateinit var arrPrefsRepo: ArrPreferencesRepository

    @MockK(relaxed = true)
    private lateinit var seerrRepository: SeerrRepository

    @MockK(relaxed = true)
    private lateinit var sonarrRepository: SonarrRepository

    @MockK(relaxed = true)
    private lateinit var radarrRepository: RadarrRepository

    @MockK(relaxed = true)
    private lateinit var cinefinPluginRepository: CinefinPluginRepository

    private val authRepository = FakeJellyfinAuthRepository()

    private val testDispatcher = StandardTestDispatcher()
    
    private val seerrPreferencesFlow = MutableStateFlow(SeerrPreferences.DEFAULT)
    private val sonarrPreferencesFlow = MutableStateFlow(SonarrPreferences.DEFAULT)
    private val radarrPreferencesFlow = MutableStateFlow(RadarrPreferences.DEFAULT)

    private lateinit var viewModel: MediaRequestSettingsViewModel
    private var collectJob: Job? = null

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        every { seerrPrefsRepo.seerrPreferencesFlow } returns seerrPreferencesFlow
        every { arrPrefsRepo.sonarrPreferencesFlow } returns sonarrPreferencesFlow
        every { arrPrefsRepo.radarrPreferencesFlow } returns radarrPreferencesFlow

        coEvery { cinefinPluginRepository.getPluginInfo() } returns ApiResult.Success(
            CinefinPluginInfoResponse(
                version = "1.0.0",
                capabilities = emptyList(),
                isConfigured = true,
                allowNonAdminImports = false
            )
        )

        viewModel = MediaRequestSettingsViewModel(
            seerrPrefsRepo = seerrPrefsRepo,
            arrPrefsRepo = arrPrefsRepo,
            seerrRepository = seerrRepository,
            sonarrRepository = sonarrRepository,
            radarrRepository = radarrRepository,
            cinefinPluginRepository = cinefinPluginRepository,
            authRepository = authRepository,
        )

        collectJob = kotlinx.coroutines.CoroutineScope(testDispatcher).launch {
            viewModel.isCurrentUserAdmin.collect {}
        }
    }

    @After
    fun tearDown() {
        collectJob?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun isCurrentUserAdmin_reflectsServerAdminStatus() = runTest(testDispatcher) {
        // Initially false
        assertFalse(viewModel.isCurrentUserAdmin.value)

        // Set server as admin
        authRepository.currentServer.value = JellyfinServer(
            id = "server-id",
            name = "My Server",
            url = "http://localhost",
            isAdministrator = true
        )
        advanceUntilIdle()
        assertTrue(viewModel.isCurrentUserAdmin.value)

        // Set server as non-admin
        authRepository.currentServer.value = JellyfinServer(
            id = "server-id",
            name = "My Server",
            url = "http://localhost",
            isAdministrator = false
        )
        advanceUntilIdle()
        assertFalse(viewModel.isCurrentUserAdmin.value)
    }

    @Test
    fun importCredentials_nonAdmin_failsImmediatelyWithoutCallingRepo() = runTest(testDispatcher) {
        authRepository.currentServer.value = JellyfinServer(
            id = "server-id",
            name = "My Server",
            url = "http://localhost",
            isAdministrator = false
        )
        advanceUntilIdle()

        viewModel.importCredentialsFromPlugin()
        advanceUntilIdle()

        assertTrue(viewModel.credentialImportState.value is CredentialImportState.Failure)
        val failureState = viewModel.credentialImportState.value as CredentialImportState.Failure
        assertEquals("Administrator access is required to import plugin credentials", failureState.message)

        coVerify(exactly = 0) { cinefinPluginRepository.getCredentials() }
    }

    @Test
    fun importCredentials_admin_success_updatesPreferences() = runTest(testDispatcher) {
        authRepository.currentServer.value = JellyfinServer(
            id = "server-id",
            name = "My Server",
            url = "http://localhost",
            isAdministrator = true
        )
        advanceUntilIdle()

        val mockCredentials = CinefinPluginCredentialsResponse(
            sonarr = CinefinPluginServiceCredentials(
                isConfigured = true,
                url = "http://sonarr.local",
                apiKey = "sonarr-key"
            ),
            radarr = CinefinPluginServiceCredentials(
                isConfigured = true,
                url = "http://radarr.local",
                apiKey = "radarr-key"
            ),
            overseerr = CinefinPluginServiceCredentials(
                isConfigured = true,
                url = "http://seerr.local",
                apiKey = "seerr-key"
            ),
            proxy = CinefinPluginProxyCredentials(
                isConfigured = false,
                username = "",
                password = ""
            )
        )
        coEvery { cinefinPluginRepository.getCredentials() } returns ApiResult.Success(mockCredentials)

        viewModel.importCredentialsFromPlugin()
        advanceUntilIdle()

        assertTrue(viewModel.credentialImportState.value is CredentialImportState.Success)
        val successState = viewModel.credentialImportState.value as CredentialImportState.Success
        assertEquals("Imported 3 service configuration(s)", successState.message)

        coVerify(exactly = 1) { arrPrefsRepo.updateSonarrUrl("http://sonarr.local") }
        coVerify(exactly = 1) { arrPrefsRepo.updateSonarrApiKey("sonarr-key") }
        coVerify(exactly = 1) { arrPrefsRepo.setSonarrEnabled(true) }

        coVerify(exactly = 1) { arrPrefsRepo.updateRadarrUrl("http://radarr.local") }
        coVerify(exactly = 1) { arrPrefsRepo.updateRadarrApiKey("radarr-key") }
        coVerify(exactly = 1) { arrPrefsRepo.setRadarrEnabled(true) }

        coVerify(exactly = 1) { seerrPrefsRepo.updateBaseUrl("http://seerr.local") }
        coVerify(exactly = 1) { seerrPrefsRepo.updateApiKey("seerr-key") }
        coVerify(exactly = 1) { seerrPrefsRepo.setEnabled(true) }
    }

    @Test
    fun importCredentials_admin_unauthorizedError_returnsSessionExpiredMessage() = runTest(testDispatcher) {
        authRepository.currentServer.value = JellyfinServer(
            id = "server-id",
            name = "My Server",
            url = "http://localhost",
            isAdministrator = true
        )
        advanceUntilIdle()

        coEvery { cinefinPluginRepository.getCredentials() } returns ApiResult.Error(
            message = "Unauthorized",
            errorType = ErrorType.UNAUTHORIZED
        )

        viewModel.importCredentialsFromPlugin()
        advanceUntilIdle()

        assertTrue(viewModel.credentialImportState.value is CredentialImportState.Failure)
        val failureState = viewModel.credentialImportState.value as CredentialImportState.Failure
        assertEquals("Session expired or access denied. Please try logging in again.", failureState.message)
    }

    @Test
    fun importCredentials_admin_otherError_passesMessageThrough() = runTest(testDispatcher) {
        authRepository.currentServer.value = JellyfinServer(
            id = "server-id",
            name = "My Server",
            url = "http://localhost",
            isAdministrator = true
        )
        advanceUntilIdle()

        coEvery { cinefinPluginRepository.getCredentials() } returns ApiResult.Error(
            message = "Some server error occurred",
            errorType = ErrorType.SERVER_ERROR
        )

        viewModel.importCredentialsFromPlugin()
        advanceUntilIdle()

        assertTrue(viewModel.credentialImportState.value is CredentialImportState.Failure)
        val failureState = viewModel.credentialImportState.value as CredentialImportState.Failure
        assertEquals("Some server error occurred", failureState.message)
    }

    @Test
    fun importCredentials_nonAdmin_whenAllowNonAdminImportsTrue_succeeds() = runTest(testDispatcher) {
        authRepository.currentServer.value = JellyfinServer(
            id = "server-id",
            name = "My Server",
            url = "http://localhost",
            isAdministrator = false
        )
        advanceUntilIdle()

        // Set allowNonAdminImports to true (e.g. as fetched from plugin info)
        coEvery { cinefinPluginRepository.getPluginInfo() } returns ApiResult.Success(
            CinefinPluginInfoResponse(version = "1.0.0", capabilities = emptyList(), isConfigured = true, allowNonAdminImports = true)
        )
        viewModel.fetchPluginInfo()
        advanceUntilIdle()
        assertTrue(viewModel.allowNonAdminImports.value)

        val mockCredentials = CinefinPluginCredentialsResponse(
            sonarr = CinefinPluginServiceCredentials(isConfigured = true, url = "http://sonarr.local", apiKey = "sonarr-key"),
            radarr = CinefinPluginServiceCredentials(isConfigured = false, url = "", apiKey = ""),
            overseerr = CinefinPluginServiceCredentials(isConfigured = false, url = "", apiKey = ""),
            proxy = CinefinPluginProxyCredentials(isConfigured = false, username = "", password = "")
        )
        coEvery { cinefinPluginRepository.getCredentials() } returns ApiResult.Success(mockCredentials)

        viewModel.importCredentialsFromPlugin()
        advanceUntilIdle()

        assertTrue(viewModel.credentialImportState.value is CredentialImportState.Success)
        coVerify(exactly = 1) { arrPrefsRepo.updateSonarrUrl("http://sonarr.local") }
    }

    @Test
    fun fetchPluginInfo_updatesAllowNonAdminImportsAndPluginConfigured() = runTest(testDispatcher) {
        coEvery { cinefinPluginRepository.getPluginInfo() } returns ApiResult.Success(
            CinefinPluginInfoResponse(version = "1.0.0", capabilities = emptyList(), isConfigured = true, allowNonAdminImports = true)
        )
        viewModel.fetchPluginInfo()
        advanceUntilIdle()

        assertTrue(viewModel.isPluginConfigured.value)
        assertTrue(viewModel.allowNonAdminImports.value)
    }

    @Test
    fun setAllowNonAdminImports_admin_success_updatesLocalAndRemoteState() = runTest(testDispatcher) {
        coEvery { cinefinPluginRepository.updateConfiguration(true) } returns ApiResult.Success(
            CinefinPluginRequestResponse(success = true, message = "Updated")
        )

        viewModel.setAllowNonAdminImports(true)
        advanceUntilIdle()

        assertTrue(viewModel.allowNonAdminImports.value)
        coVerify(exactly = 1) { cinefinPluginRepository.updateConfiguration(true) }
    }

    @Test
    fun setAllowNonAdminImports_admin_failure_revertsLocalState() = runTest(testDispatcher) {
        // Initially false
        assertFalse(viewModel.allowNonAdminImports.value)

        coEvery { cinefinPluginRepository.updateConfiguration(true) } returns ApiResult.Error(
            message = "Server error",
            errorType = ErrorType.SERVER_ERROR
        )

        viewModel.setAllowNonAdminImports(true)
        advanceUntilIdle()

        // Reverted to false
        assertFalse(viewModel.allowNonAdminImports.value)
        assertTrue(viewModel.credentialImportState.value is CredentialImportState.Failure)
        assertEquals("Server error", (viewModel.credentialImportState.value as CredentialImportState.Failure).message)
    }

    class FakeJellyfinAuthRepository : IJellyfinAuthRepository {
        override val currentServer = MutableStateFlow<JellyfinServer?>(null)
        override val isConnected = MutableStateFlow(false).asStateFlow()
        override val isAuthenticating = MutableStateFlow(false).asStateFlow()

        override fun getCurrentServer(): JellyfinServer? = currentServer.value
        override fun isUserAuthenticated(): Boolean = currentServer.value != null
        override fun isTokenExpired(): Boolean = false
        override fun shouldRefreshToken(): Boolean = false
        override fun seedCurrentServer(server: JellyfinServer?) {
            currentServer.value = server
        }
        override fun restorePersistedSession(server: JellyfinServer) {
            currentServer.value = server
        }

        override suspend fun authenticateUser(
            serverUrl: String,
            username: String,
            password: String
        ): ApiResult<AuthenticationResult> = TODO()

        override suspend fun reAuthenticate(): Boolean = true
        override suspend fun forceReAuthenticate(): Boolean = true
        override suspend fun logout() {}

        override suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> = TODO()
        override suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> = TODO()
        override suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> = TODO()
        override suspend fun isQuickConnectEnabled(serverUrl: String): ApiResult<Boolean> = TODO()
        override suspend fun authenticateWithQuickConnect(serverUrl: String, secret: String): ApiResult<AuthenticationResult> = TODO()

        override suspend fun token(): String? = currentServer.value?.accessToken
    }
}
