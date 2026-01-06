package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.preferences.CredentialSecurityPreferences
import com.rpeters.jellyfin.data.preferences.CredentialSecurityPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class CredentialSecurityPreferencesViewModel @Inject constructor(
    private val repository: CredentialSecurityPreferencesRepository,
    private val secureCredentialManager: SecureCredentialManager,
) : ViewModel() {

    private val _preferences = MutableStateFlow(CredentialSecurityPreferences.DEFAULT)
    val preferences: StateFlow<CredentialSecurityPreferences> = _preferences.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.preferences.collectLatest { prefs ->
                _preferences.value = prefs
            }
        }
    }

    fun setStrongAuthRequired(enabled: Boolean) {
        viewModelScope.launch {
            _isUpdating.value = true
            _errorMessage.value = null
            try {
                secureCredentialManager.applyCredentialAuthenticationRequirement(enabled)
            } catch (exception: Exception) {
                _errorMessage.value = exception.message
            } finally {
                _isUpdating.value = false
            }
        }
    }
}
