package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class TVEpisodeDetailState(
    val playbackAnalysis: PlaybackCapabilityAnalysis? = null,
)

@HiltViewModel
class TVEpisodeDetailViewModel @Inject constructor(
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(TVEpisodeDetailState())
    val state: StateFlow<TVEpisodeDetailState> = _state.asStateFlow()

    fun loadEpisodeAnalysis(episode: BaseItemDto) {
        viewModelScope.launch {
            val analysis = try {
                enhancedPlaybackUtils.analyzePlaybackCapabilities(episode)
            } catch (e: Exception) {
                android.util.Log.e("TVEpisodeDetailVM", "Failed to analyze playback capabilities for episode ${episode.id}", e)
                null
            }
            _state.value = _state.value.copy(playbackAnalysis = analysis)
        }
    }
}
