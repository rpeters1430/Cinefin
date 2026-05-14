package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository
import com.rpeters.jellyfin.data.preferences.RadarrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.preferences.SonarrPreferences
import com.rpeters.jellyfin.data.repository.RadarrRepository
import com.rpeters.jellyfin.data.repository.SonarrRepository
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

@HiltViewModel
class MediaRequestSettingsViewModel @Inject constructor(
    private val seerrPrefsRepo: SeerrPreferencesRepository,
    private val arrPrefsRepo: ArrPreferencesRepository,
    private val seerrRepository: SeerrRepository,
    private val sonarrRepository: SonarrRepository,
    private val radarrRepository: RadarrRepository,
) : ViewModel() {

    // ── Seerr ────────────────────────────────────────────────────────────────

    val seerrPreferences: StateFlow<SeerrPreferences> = seerrPrefsRepo.seerrPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeerrPreferences.DEFAULT)

    private val _seerrTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val seerrTestState: StateFlow<ConnectionTestState> = _seerrTestState.asStateFlow()

    fun updateSeerrUrl(url: String) = viewModelScope.launch {
        seerrPrefsRepo.updateBaseUrl(url)
        _seerrTestState.value = ConnectionTestState.Idle
    }
    fun updateSeerrApiKey(key: String) = viewModelScope.launch {
        seerrPrefsRepo.updateApiKey(key)
        _seerrTestState.value = ConnectionTestState.Idle
    }
    fun setSeerrEnabled(enabled: Boolean) = viewModelScope.launch { seerrPrefsRepo.setEnabled(enabled) }

    fun testSeerr() {
        if (_seerrTestState.value is ConnectionTestState.Testing) return
        viewModelScope.launch {
            _seerrTestState.value = ConnectionTestState.Testing
            _seerrTestState.value = when (val r = seerrRepository.testConnection()) {
                is ApiResult.Success -> ConnectionTestState.Success
                is ApiResult.Error -> ConnectionTestState.Failure(r.message)
                is ApiResult.Loading -> ConnectionTestState.Failure("Unexpected response")
            }
        }
    }

    // ── Sonarr ───────────────────────────────────────────────────────────────

    val sonarrPreferences: StateFlow<SonarrPreferences> = arrPrefsRepo.sonarrPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SonarrPreferences.DEFAULT)

    private val _sonarrTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val sonarrTestState: StateFlow<ConnectionTestState> = _sonarrTestState.asStateFlow()

    fun updateSonarrUrl(url: String) = viewModelScope.launch {
        arrPrefsRepo.updateSonarrUrl(url)
        _sonarrTestState.value = ConnectionTestState.Idle
    }
    fun updateSonarrApiKey(key: String) = viewModelScope.launch {
        arrPrefsRepo.updateSonarrApiKey(key)
        _sonarrTestState.value = ConnectionTestState.Idle
    }
    fun setSonarrEnabled(enabled: Boolean) = viewModelScope.launch { arrPrefsRepo.setSonarrEnabled(enabled) }

    fun testSonarr() {
        if (_sonarrTestState.value is ConnectionTestState.Testing) return
        viewModelScope.launch {
            _sonarrTestState.value = ConnectionTestState.Testing
            _sonarrTestState.value = when (val r = sonarrRepository.testConnection()) {
                is ApiResult.Success -> ConnectionTestState.Success
                is ApiResult.Error -> ConnectionTestState.Failure(r.message)
                is ApiResult.Loading -> ConnectionTestState.Failure("Unexpected response")
            }
        }
    }

    // ── Radarr ───────────────────────────────────────────────────────────────

    val radarrPreferences: StateFlow<RadarrPreferences> = arrPrefsRepo.radarrPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RadarrPreferences.DEFAULT)

    private val _radarrTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val radarrTestState: StateFlow<ConnectionTestState> = _radarrTestState.asStateFlow()

    fun updateRadarrUrl(url: String) = viewModelScope.launch {
        arrPrefsRepo.updateRadarrUrl(url)
        _radarrTestState.value = ConnectionTestState.Idle
    }
    fun updateRadarrApiKey(key: String) = viewModelScope.launch {
        arrPrefsRepo.updateRadarrApiKey(key)
        _radarrTestState.value = ConnectionTestState.Idle
    }
    fun setRadarrEnabled(enabled: Boolean) = viewModelScope.launch { arrPrefsRepo.setRadarrEnabled(enabled) }

    fun testRadarr() {
        if (_radarrTestState.value is ConnectionTestState.Testing) return
        viewModelScope.launch {
            _radarrTestState.value = ConnectionTestState.Testing
            _radarrTestState.value = when (val r = radarrRepository.testConnection()) {
                is ApiResult.Success -> ConnectionTestState.Success
                is ApiResult.Error -> ConnectionTestState.Failure(r.message)
                is ApiResult.Loading -> ConnectionTestState.Failure("Unexpected response")
            }
        }
    }
}
