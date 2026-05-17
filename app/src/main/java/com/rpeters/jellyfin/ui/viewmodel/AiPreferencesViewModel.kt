package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.AiPreferences
import com.rpeters.jellyfin.data.preferences.AiPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiPreferencesViewModel @Inject constructor(
    private val aiPreferencesRepository: AiPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<AiPreferences> = aiPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiPreferences.DEFAULT,
    )

    fun updateEnableSmartContentWarnings(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.updateEnableSmartContentWarnings(enabled)
        }
    }

    fun updateEnableAiChapterMarkers(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.updateEnableAiChapterMarkers(enabled)
        }
    }
}
