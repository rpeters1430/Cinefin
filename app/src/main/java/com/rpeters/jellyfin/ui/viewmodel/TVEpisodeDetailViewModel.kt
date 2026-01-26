package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class TVEpisodeDetailState(
    val episode: BaseItemDto? = null,
    val seriesInfo: BaseItemDto? = null,
    val previousEpisode: BaseItemDto? = null,
    val nextEpisode: BaseItemDto? = null,
    val playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class TVEpisodeDetailViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
) : ViewModel() {

    private val _state = MutableStateFlow(TVEpisodeDetailState())
    val state: StateFlow<TVEpisodeDetailState> = _state.asStateFlow()

    fun loadEpisodeDetails(episode: BaseItemDto, seriesInfo: BaseItemDto? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                episode = episode,
                seriesInfo = seriesInfo,
                isLoading = true,
            )

            // Load playback analysis
            loadEpisodeAnalysis(episode)

            // Load series info if not provided
            if (seriesInfo == null) {
                episode.seriesId?.let { seriesId ->
                    loadSeriesInfo(seriesId.toString())
                }
            }

            // Load adjacent episodes
            episode.seasonId?.let { seasonId ->
                loadAdjacentEpisodes(seasonId.toString(), episode.indexNumber)
            }

            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private fun loadEpisodeAnalysis(episode: BaseItemDto) {
        viewModelScope.launch {
            val analysis = try {
                enhancedPlaybackUtils.analyzePlaybackCapabilities(episode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TVEpisodeDetailVM", "Failed to analyze playback capabilities for episode ${episode.id}", e)
                null
            }
            _state.value = _state.value.copy(playbackAnalysis = analysis)
        }
    }

    private suspend fun loadSeriesInfo(seriesId: String) {
        when (val result = mediaRepository.getSeriesDetails(seriesId)) {
            is ApiResult.Success -> {
                _state.value = _state.value.copy(seriesInfo = result.data)
            }
            is ApiResult.Error -> {
                android.util.Log.w("TVEpisodeDetailVM", "Failed to load series info: ${result.message}")
            }
            is ApiResult.Loading -> {
                // no-op
            }
        }
    }

    private suspend fun loadAdjacentEpisodes(seasonId: String, currentEpisodeIndex: Int?) {
        if (currentEpisodeIndex == null) return

        when (val result = mediaRepository.getEpisodesForSeason(seasonId)) {
            is ApiResult.Success -> {
                val episodes = result.data.sortedBy { it.indexNumber }
                val currentIndex = episodes.indexOfFirst { it.indexNumber == currentEpisodeIndex }

                if (currentIndex >= 0) {
                    val previous = episodes.getOrNull(currentIndex - 1)
                    val next = episodes.getOrNull(currentIndex + 1)

                    _state.value = _state.value.copy(
                        previousEpisode = previous,
                        nextEpisode = next,
                    )
                }
            }
            is ApiResult.Error -> {
                android.util.Log.w("TVEpisodeDetailVM", "Failed to load adjacent episodes: ${result.message}")
            }
            is ApiResult.Loading -> {
                // no-op
            }
        }
    }
}
