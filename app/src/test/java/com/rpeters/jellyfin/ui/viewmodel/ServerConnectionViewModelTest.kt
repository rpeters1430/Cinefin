package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.viewmodel.PreferencesKeys.BIOMETRIC_AUTH_ENABLED
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
    private lateinit var context: Context

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        repository = mockk(relaxed = true)
        secureCredentialManager = mockk(relaxed = true)

        every { repository.isConnected } returns MutableStateFlow(false)
        every { secureCredentialManager.isBiometricAuthAvailable() } returns true

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

            coVerify(exactly = 1) {
                repository.authenticateUser("https://example.com", "user", "biometricPassword")
            }

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
