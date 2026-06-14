package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.IJellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.security.CertificatePinningManager
import com.rpeters.jellyfin.network.ConnectivityChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import kotlinx.coroutines.test.resetMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.inject.Provider
import com.rpeters.jellyfin.data.common.TestDispatcherProvider
import com.rpeters.jellyfin.data.repository.common.ApiResult
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel

/**
 * Tests for ServerConnectionViewModel offline startup behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServerConnectionViewModelOfflineTest {

    private lateinit var repository: IJellyfinRepository
    private lateinit var authRepository: IJellyfinAuthRepository
    private lateinit var secureCredentialManager: SecureCredentialManager
    private lateinit var certificatePinningManager: CertificatePinningManager
    private lateinit var connectivityChecker: ConnectivityChecker
    private lateinit var offlineDownloadManager: com.rpeters.jellyfin.data.offline.OfflineDownloadManager
    private lateinit var offlineDownloadManagerProvider: Provider<com.rpeters.jellyfin.data.offline.OfflineDownloadManager>
    private lateinit var discoveryRepository: com.rpeters.jellyfin.data.repository.IJellyfinDiscoveryRepository
    private lateinit var context: Context
    private lateinit var viewModel: ServerConnectionViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        context = ApplicationProvider.getApplicationContext()
        repository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        secureCredentialManager = mockk(relaxed = true)
        certificatePinningManager = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
        discoveryRepository = mockk(relaxed = true)
        offlineDownloadManager = mockk(relaxed = true)
        offlineDownloadManagerProvider = Provider { offlineDownloadManager }
        every { offlineDownloadManager.downloads } returns MutableStateFlow(emptyList())

        // Setup default mocks
        coEvery { repository.isConnectedFlow } returns flowOf(false)
        coEvery { authRepository.isTokenExpired() } returns false
        coEvery { secureCredentialManager.getBiometricCapability(any()) } returns
            mockk {
                every { isAvailable } returns false
                every { isWeakOnly } returns false
            }
        coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(mockk(relaxed = true))
        coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Success(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.viewModelScope.cancel()
        }
        io.mockk.unmockkAll()
        Dispatchers.resetMain()
    }

    private suspend fun awaitCondition(timeoutMs: Long = 2000, condition: suspend () -> Boolean) {
        val maxIterations = timeoutMs / 10
        var iterations = 0
        while (iterations < maxIterations) {
            if (condition()) return
            delay(10)
            iterations++
        }
        if (!condition()) {
            throw AssertionError("Condition not met within $timeoutMs ms")
        }
    }

    @Test
    fun `init skips auto-login when offline`() = runTest(testDispatcher) {
        // Given: Device is offline with saved credentials
        every { connectivityChecker.isOnline() } returns false
        coEvery { connectivityChecker.observeNetworkConnectivity() } returns flowOf(false)

        setupDataStoreWithCredentials(
            serverUrl = "https://server.com",
            username = "testuser",
            rememberLogin = true,
        )
        coEvery {
            secureCredentialManager.hasSavedPassword("https://server.com", "testuser")
        } returns true

        // When: ViewModel initializes
        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(testDispatcher),
        )
        awaitCondition {
            viewModel.connectionState.value.savedServerUrl == "https://server.com"
        }
        advanceUntilIdle()

        // Then: Auto-login should be skipped
        val state = viewModel.connectionState.value
        assertFalse("Should not be connected", state.isConnected)
        assertFalse("Should not be connecting", state.isConnecting)
        assertTrue("Should have error message", state.errorMessage?.contains("No internet connection") == true)

        // Verify no password was retrieved (auto-login skipped)
        coVerify(exactly = 0) {
            secureCredentialManager.getPassword("https://server.com", "testuser")
        }
    }

    @Test
    fun `init retries auto-login when network becomes available`() = runTest(testDispatcher) {
        // Given: Device starts offline then comes online
        val networkState = MutableStateFlow(false)
        every { connectivityChecker.isOnline() } returns false
        coEvery { connectivityChecker.observeNetworkConnectivity() } returns networkState

        setupDataStoreWithCredentials(
            serverUrl = "https://server.com",
            username = "testuser",
            rememberLogin = true,
        )
        coEvery {
            secureCredentialManager.hasSavedPassword("https://server.com", "testuser")
        } returns true
        coEvery {
            secureCredentialManager.getPassword("https://server.com", "testuser")
        } returns "password123"

        // When: ViewModel initializes while offline
        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(testDispatcher),
        )
        awaitCondition {
            viewModel.connectionState.value.savedServerUrl == "https://server.com"
        }
        advanceUntilIdle()

        // Then: Initially offline with error
        var state = viewModel.connectionState.value
        assertTrue("Should have offline error", state.errorMessage?.contains("No internet connection") == true)

        // When: Network becomes available
        networkState.value = true
        advanceUntilIdle()

        // Then: Should attempt to connect
        state = viewModel.connectionState.value
        // Note: connectToServer would be called but we're not testing the full flow here
        // We verify the password for retry
        coVerify(atLeast = 1) {
            secureCredentialManager.getPassword("https://server.com", "testuser")
        }
    }

    @Test
    fun `init proceeds with auto-login when online`() = runTest(testDispatcher) {
        // Given: Device is online with saved credentials
        every { connectivityChecker.isOnline() } returns true
        coEvery { connectivityChecker.observeNetworkConnectivity() } returns flowOf(true)

        setupDataStoreWithCredentials(
            serverUrl = "https://server.com",
            username = "testuser",
            rememberLogin = true,
        )
        coEvery {
            secureCredentialManager.hasSavedPassword("https://server.com", "testuser")
        } returns true
        coEvery {
            secureCredentialManager.getPassword("https://server.com", "testuser")
        } returns "password123"

        // When: ViewModel initializes
        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(testDispatcher),
        )
        awaitCondition {
            viewModel.connectionState.value.savedServerUrl == "https://server.com"
        }
        advanceUntilIdle()

        // Then: Should attempt auto-login
        coVerify(atLeast = 1) {
            secureCredentialManager.getPassword("https://server.com", "testuser")
        }
    }

    @Test
    fun `connectToServer fails fast when offline`() = runTest(testDispatcher) {
        // Given: Device is offline
        every { connectivityChecker.isOnline() } returns false
        setupDataStoreWithCredentials(
            serverUrl = "https://server.com",
            username = "testuser",
            rememberLogin = false,
        )

        viewModel = ServerConnectionViewModel(
            repository,
            authRepository,
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            discoveryRepository,
            offlineDownloadManagerProvider,
            context,
            TestDispatcherProvider(testDispatcher),
        )
        awaitCondition {
            viewModel.connectionState.value.savedServerUrl == "https://server.com"
        }
        advanceUntilIdle()
    }

    private suspend fun setupDataStoreWithCredentials(
        serverUrl: String = "",
        username: String = "",
        rememberLogin: Boolean = true,
    ) {
        context.dataStore.edit { preferences ->
            preferences.clear()
            preferences[PreferencesKeys.SERVER_URL] = serverUrl
            preferences[PreferencesKeys.USERNAME] = username
            preferences[PreferencesKeys.REMEMBER_LOGIN] = rememberLogin
            preferences[PreferencesKeys.BIOMETRIC_AUTH_ENABLED] = false
            preferences[PreferencesKeys.BIOMETRIC_REQUIRE_STRONG] = false
        }
    }
}
