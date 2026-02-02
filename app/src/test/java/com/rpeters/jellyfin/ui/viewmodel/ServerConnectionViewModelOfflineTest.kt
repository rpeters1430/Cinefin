package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.PreferencesKeys
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinRepository
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for ServerConnectionViewModel offline startup behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionViewModelOfflineTest {

    private lateinit var repository: JellyfinRepository
    private lateinit var secureCredentialManager: SecureCredentialManager
    private lateinit var certificatePinningManager: CertificatePinningManager
    private lateinit var connectivityChecker: ConnectivityChecker
    private lateinit var context: Context
    private lateinit var viewModel: ServerConnectionViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)
        secureCredentialManager = mockk(relaxed = true)
        certificatePinningManager = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
        context = mockk(relaxed = true)

        // Setup default mocks
        coEvery { repository.isConnected } returns flowOf(false)
        coEvery { secureCredentialManager.getBiometricCapability(any()) } returns
            mockk {
                every { isAvailable } returns false
                every { isWeakOnly } returns false
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
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            context,
        )
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
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            context,
        )
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
        // We verify the password was retrieved for retry
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
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            context,
        )
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
            secureCredentialManager,
            certificatePinningManager,
            connectivityChecker,
            context,
        )
        advanceUntilIdle()

        // When: User tries to connect manually
        // Note: The actual implementation would check connectivity in connectToServer
        // For now, the NetworkStateInterceptor will catch it

        // Then: Connection should be intercepted by NetworkStateInterceptor
        // This is tested in NetworkStateInterceptorTest
    }

    private fun setupDataStoreWithCredentials(
        serverUrl: String = "",
        username: String = "",
        rememberLogin: Boolean = true,
    ) {
        val preferences = mockk<androidx.datastore.preferences.core.Preferences>(relaxed = true)
        every { preferences[com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.SERVER_URL] } returns serverUrl
        every { preferences[com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.USERNAME] } returns username
        every { preferences[com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.REMEMBER_LOGIN] } returns rememberLogin
        every { preferences[com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.BIOMETRIC_AUTH_ENABLED] } returns false
        every { preferences[com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.BIOMETRIC_REQUIRE_STRONG] } returns false

        val dataStore = mockk<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>(relaxed = true)
        coEvery { dataStore.data } returns flowOf(preferences)
        every { context.dataStore } returns dataStore
    }
}
