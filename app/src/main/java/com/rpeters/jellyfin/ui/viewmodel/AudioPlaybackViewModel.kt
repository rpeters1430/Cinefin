package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.rpeters.jellyfin.ui.player.audio.AudioPlaybackState
import com.rpeters.jellyfin.ui.player.audio.AudioServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AudioPlaybackViewModel @Inject constructor(
    private val audioServiceConnection: AudioServiceConnection,
) : ViewModel() {

    val playbackState: StateFlow<AudioPlaybackState> = audioServiceConnection.playbackState
    val queue: StateFlow<List<MediaItem>> = audioServiceConnection.queueState

    init {
        viewModelScope.launch {
            audioServiceConnection.ensureController()
            audioServiceConnection.refreshState()
        }
    }

    fun togglePlayPause() {
        audioServiceConnection.togglePlayPause()
    }

    fun toggleShuffle() {
        audioServiceConnection.toggleShuffle()
    }

    fun skipToNext() {
        audioServiceConnection.skipToNext()
    }
}

