package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.AudioChannelPreference
import com.rpeters.jellyfin.data.preferences.PlaybackPreferences
import com.rpeters.jellyfin.data.preferences.PlaybackPreferencesRepository
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing playback preferences.
 */
@HiltViewModel
class PlaybackPreferencesViewModel @Inject constructor(
    private val repository: PlaybackPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<PlaybackPreferences> = repository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackPreferences.DEFAULT,
        )

    fun setMaxBitrateWifi(bitrate: Int) {
        viewModelScope.launch {
            repository.setMaxBitrateWifi(bitrate)
        }
    }

    fun setMaxBitrateCellular(bitrate: Int) {
        viewModelScope.launch {
            repository.setMaxBitrateCellular(bitrate)
        }
    }

    fun setTranscodingQuality(quality: TranscodingQuality) {
        viewModelScope.launch {
            repository.setTranscodingQuality(quality)
        }
    }

    fun setAudioChannels(preference: AudioChannelPreference) {
        viewModelScope.launch {
            repository.setAudioChannels(preference)
        }
    }
}
