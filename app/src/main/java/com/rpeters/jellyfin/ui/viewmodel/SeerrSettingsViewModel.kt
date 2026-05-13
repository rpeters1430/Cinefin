package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    object Success : ConnectionTestState()
    data class Failure(val message: String) : ConnectionTestState()
}

@HiltViewModel
class SeerrSettingsViewModel @Inject constructor(
    private val preferencesRepository: SeerrPreferencesRepository,
    private val seerrRepository: SeerrRepository,
    private val cinefinPluginRepository: com.rpeters.jellyfin.data.repository.CinefinPluginRepository,
) : ViewModel() {

    val seerrPreferences: StateFlow<SeerrPreferences> = preferencesRepository.seerrPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SeerrPreferences.DEFAULT
        )

    private val _isPluginConfigured = MutableStateFlow(false)
    val isPluginConfigured: StateFlow<Boolean> = _isPluginConfigured.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val infoResult = cinefinPluginRepository.getPluginInfo()) {
                is ApiResult.Success -> {
                    _isPluginConfigured.value = infoResult.data.isConfigured
                }
                else -> {
                    _isPluginConfigured.value = false
                }
            }
        }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.updateBaseUrl(url)
            _connectionTestState.value = ConnectionTestState.Idle
        }
    }

    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesRepository.updateApiKey(apiKey)
            _connectionTestState.value = ConnectionTestState.Idle
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setEnabled(enabled)
        }
    }

    fun testConnection() {
        if (_connectionTestState.value is ConnectionTestState.Testing) return
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            _connectionTestState.value = when (val result = seerrRepository.testConnection()) {
                is ApiResult.Success -> ConnectionTestState.Success
                is ApiResult.Error -> ConnectionTestState.Failure(result.message)
                else -> ConnectionTestState.Failure("Unexpected response")
            }
        }
    }
}
