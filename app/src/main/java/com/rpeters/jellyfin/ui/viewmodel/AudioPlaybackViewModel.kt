package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.rpeters.jellyfin.ui.player.audio.AudioPlaybackState
import com.rpeters.jellyfin.ui.player.audio.AudioServiceConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioPlaybackViewModel @Inject constructor(
    private val audioServiceConnection: AudioServiceConnection,
) : ViewModel() {

    val playbackState: StateFlow<AudioPlaybackState> = audioServiceConnection.playbackState
    val queue: StateFlow<List<MediaItem>> = audioServiceConnection.queueState

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                audioServiceConnection.ensureController()
                audioServiceConnection.refreshState()
            } catch (e: Exception) {
                // Connection errors are handled gracefully
            }
        }
        viewModelScope.launch {
            playbackState.collectLatest { state ->
                _currentPosition.value = state.currentPosition
                _duration.value = state.duration
            }
        }
    }

    fun togglePlayPause() {
        audioServiceConnection.togglePlayPause()
    }

    fun toggleShuffle() {
        audioServiceConnection.toggleShuffle()
    }

    fun toggleRepeat() {
        audioServiceConnection.toggleRepeat()
    }

    fun setPlaybackSpeed(speed: Float) {
        audioServiceConnection.setPlaybackSpeed(speed)
    }

    fun skipToNext() {
        audioServiceConnection.skipToNext()
    }

    fun skipToPrevious() {
        audioServiceConnection.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        audioServiceConnection.seekTo(positionMs)
    }

    fun seekForward(amountMs: Long = 10000L) {
        audioServiceConnection.seekForward(amountMs)
    }

    fun seekBackward(amountMs: Long = 10000L) {
        audioServiceConnection.seekBackward(amountMs)
    }

    fun playMediaItem(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        audioServiceConnection.playNow(mediaItem, startPositionMs)
    }

    fun playQueue(
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
    ) {
        audioServiceConnection.playQueue(mediaItems, startIndex, startPositionMs)
    }

    fun addToQueue(mediaItem: MediaItem) {
        audioServiceConnection.enqueue(mediaItem)
    }

    fun removeFromQueue(index: Int) {
        audioServiceConnection.removeFromQueue(index)
    }

    fun clearQueue() {
        audioServiceConnection.clearQueue()
    }

    fun skipToQueueItem(index: Int) {
        audioServiceConnection.skipToQueueItem(index)
    }

    fun updatePlaybackProgress(position: Long, duration: Long) {
        _currentPosition.value = position
        _duration.value = duration
    }
}
