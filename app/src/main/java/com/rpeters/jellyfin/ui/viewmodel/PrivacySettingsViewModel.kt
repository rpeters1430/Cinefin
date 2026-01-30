package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.preferences.CredentialSecurityPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacySecurityState(
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricWeakOnly: Boolean = false,
    val requireStrongBiometric: Boolean = false,
    val requireDeviceAuthForCredentials: Boolean = false,
    val isUpdatingCredentialSecurity: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val secureCredentialManager: SecureCredentialManager,
    private val credentialSecurityPreferencesRepository: CredentialSecurityPreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacySecurityState())
    val state: StateFlow<PrivacySecurityState> = _state.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                context.dataStore.data,
                credentialSecurityPreferencesRepository.preferences,
            ) { loginPrefs, securityPrefs ->
                val biometricEnabled = loginPrefs[PreferencesKeys.BIOMETRIC_AUTH_ENABLED] ?: false
                val requireStrongBiometric = loginPrefs[PreferencesKeys.BIOMETRIC_REQUIRE_STRONG] ?: false
                val capability = secureCredentialManager.getBiometricCapability(requireStrongBiometric)
                PrivacySecurityState(
                    biometricEnabled = biometricEnabled,
                    biometricAvailable = capability.isAvailable,
                    biometricWeakOnly = capability.isWeakOnly && capability.isAvailable,
                    requireStrongBiometric = requireStrongBiometric,
                    requireDeviceAuthForCredentials = securityPrefs.requireStrongAuthForCredentials,
                )
            }.collect { snapshot ->
                _state.update { current ->
                    current.copy(
                        biometricEnabled = snapshot.biometricEnabled,
                        biometricAvailable = snapshot.biometricAvailable,
                        biometricWeakOnly = snapshot.biometricWeakOnly,
                        requireStrongBiometric = snapshot.requireStrongBiometric,
                        requireDeviceAuthForCredentials = snapshot.requireDeviceAuthForCredentials,
                    )
                }
            }
        }
    }

    fun setBiometricLoginEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && _state.value.requireDeviceAuthForCredentials) {
                setRequireDeviceAuthForCredentials(false)
            }
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.BIOMETRIC_AUTH_ENABLED] = enabled
            }
        }
    }

    fun setRequireStrongBiometric(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.BIOMETRIC_REQUIRE_STRONG] = enabled
            }
        }
    }

    fun setRequireDeviceAuthForCredentials(enabled: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isUpdatingCredentialSecurity = true, errorMessage = null) }
            try {
                secureCredentialManager.applyCredentialAuthenticationRequirement(enabled)
                context.dataStore.edit { preferences ->
                    preferences[PreferencesKeys.BIOMETRIC_REQUIRE_STRONG] = enabled
                    if (enabled) {
                        preferences[PreferencesKeys.BIOMETRIC_AUTH_ENABLED] = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _state.update { it.copy(isUpdatingCredentialSecurity = false) }
            }
        }
    }
}
