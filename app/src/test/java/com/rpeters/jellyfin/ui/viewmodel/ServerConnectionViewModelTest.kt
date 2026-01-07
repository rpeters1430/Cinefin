package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rpeters.jellyfin.data.BiometricCapability
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.security.CertificatePinningManager
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.BIOMETRIC_AUTH_ENABLED
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.BIOMETRIC_REQUIRE_STRONG
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.REMEMBER_LOGIN
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.SERVER_URL
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.USERNAME
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: JellyfinRepository
    private lateinit var secureCredentialManager: SecureCredentialManager
    private lateinit var certificatePinningManager: CertificatePinningManager
    private lateinit var context: Context

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        repository = mockk(relaxed = true)
        secureCredentialManager = mockk(relaxed = true)
        certificatePinningManager = mockk(relaxed = true)

        every { repository.isConnected } returns MutableStateFlow(false)
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

        context.dataStore.edit { preferences ->
            preferences.clear()
            preferences[SERVER_URL] = "https://example.com"
            preferences[USERNAME] = "user"
            preferences[REMEMBER_LOGIN] = true
            preferences[BIOMETRIC_AUTH_ENABLED] = true
        }
    }

    @Test
    fun autoLoginWithBiometric_triggersConnectToServer_whenAuthenticationSucceeds() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { secureCredentialManager.getPassword("https://example.com", "user") } returns "storedPassword"
            coEvery {
                secureCredentialManager.getPassword(
                    "https://example.com",
                    "user",
                    any<FragmentActivity>(),
                    any(),
                )
            } returns "biometricPassword"
            coEvery { secureCredentialManager.savePassword(any(), any(), any()) } returns Unit

            coEvery { repository.testServerConnection("https://example.com") } returns ApiResult.Success(
                mockk<PublicSystemInfo>(relaxed = true),
            )
            coEvery {
                repository.authenticateUser("https://example.com", "user", "biometricPassword")
            } returns ApiResult.Success(mockk<AuthenticationResult>(relaxed = true))

            val viewModel = ServerConnectionViewModel(
                repository,
                secureCredentialManager,
                certificatePinningManager,
                context,
            )

            advanceUntilIdle()

            val fragmentActivity = mockk<FragmentActivity>(relaxed = true)

            viewModel.autoLoginWithBiometric(fragmentActivity)

            advanceUntilIdle()

            coVerify(exactly = 1) {
                repository.authenticateUser("https://example.com", "user", "biometricPassword")
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
        every { secureCredentialManager.getBiometricCapability(true) } returns weakOnlyCapability

        val viewModel = ServerConnectionViewModel(repository, secureCredentialManager, context)

        advanceUntilIdle()

        viewModel.setRequireStrongBiometric(true)

        advanceUntilIdle()

        val preferences = context.dataStore.data.first()
        assertThat(preferences[BIOMETRIC_REQUIRE_STRONG]).isTrue()
        val connectionState = viewModel.connectionState.value
        assertThat(connectionState.requireStrongBiometric).isTrue()
        assertThat(connectionState.isUsingWeakBiometric).isFalse()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun init_persistsRememberLoginDefaultForFirstTimeUser() = runTest(mainDispatcherRule.dispatcher) {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        every { repository.isConnected } returns MutableStateFlow(false)

        val viewModel = ServerConnectionViewModel(
            repository,
            secureCredentialManager,
            certificatePinningManager,
            context,
        )

        advanceUntilIdle()

        val preferences = context.dataStore.data.first()
        assertThat(preferences[REMEMBER_LOGIN]).isTrue()
        assertThat(viewModel.connectionState.value.rememberLogin).isTrue()
        assertThat(preferences[BIOMETRIC_AUTH_ENABLED]).isNull()
        assertThat(viewModel.connectionState.value.isBiometricAuthEnabled).isFalse()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun autoLoginWithBiometric_respectsStrongBiometricRequirement() =
        runTest(mainDispatcherRule.dispatcher) {
            context.dataStore.edit { preferences ->
                preferences[BIOMETRIC_REQUIRE_STRONG] = true
                preferences[BIOMETRIC_AUTH_ENABLED] = true
            }
            coEvery { secureCredentialManager.getPassword("https://example.com", "user") } returns "storedPassword"
            coEvery {
                secureCredentialManager.getPassword(
                    "https://example.com",
                    "user",
                    any<FragmentActivity>(),
                    true,
                )
            } returns "biometricPassword"
            coEvery { secureCredentialManager.savePassword(any(), any(), any()) } returns Unit
            coEvery { repository.testServerConnection("https://example.com") } returns ApiResult.Success(
                mockk<PublicSystemInfo>(relaxed = true),
            )
            coEvery {
                repository.authenticateUser("https://example.com", "user", "biometricPassword")
            } returns ApiResult.Success(mockk<AuthenticationResult>(relaxed = true))

            val viewModel = ServerConnectionViewModel(repository, secureCredentialManager, context)

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
        every { repository.isConnected } returns MutableStateFlow(false)
        coEvery { repository.testServerConnection("https://example.com") } returns ApiResult.Success(
            mockk<PublicSystemInfo>(relaxed = true),
        )
        coEvery {
            repository.authenticateUser("https://example.com", "user", "password")
        } returns ApiResult.Success(mockk<AuthenticationResult>(relaxed = true))
        coEvery { secureCredentialManager.savePassword(any(), any(), any()) } returns Unit

        val viewModel = ServerConnectionViewModel(
            repository,
            secureCredentialManager,
            certificatePinningManager,
            context,
        )

        advanceUntilIdle()

        viewModel.connectToServer("https://example.com", "user", "password")

        advanceUntilIdle()

        val preferences = context.dataStore.data.first()
        assertThat(preferences[REMEMBER_LOGIN]).isFalse()
        coVerify(exactly = 0) { secureCredentialManager.savePassword(any(), any(), any()) }
        viewModel.viewModelScope.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : org.junit.rules.TestWatcher() {

    override fun starting(description: org.junit.runner.Description) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: org.junit.runner.Description) {
        kotlinx.coroutines.Dispatchers.resetMain()
    }
}
