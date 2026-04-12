package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackBreakdownItem
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import javax.inject.Inject

@HiltViewModel
class TranscodingDiagnosticsViewModel @Inject constructor(
    private val jellyfinRepository: IJellyfinRepository,
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    sealed class UiState {
        object Loading : UiState()
        data class Success(val videos: List<VideoAnalysis>) : UiState()
        data class Error(val message: String) : UiState()
    }

    data class VideoAnalysis(
        val id: String,
        val name: String,
        val videoCodec: String,
        val audioCodec: String,
        val container: String,
        val resolution: String,
        val needsTranscoding: Boolean,
        val methodLabel: String,
        val transcodingReasons: List<String>,
        val breakdown: List<PlaybackBreakdownItem>,
        val item: BaseItemDto, // Full item for navigation
        val itemType: String, // "Movie" or "Episode"
    )

    fun loadLibraryVideos() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // Request extra fields to get codec and resolution information
            val fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS)

            // Get all movie and episode items
            val movieResult = jellyfinRepository.getLibraryItems(
                itemTypes = "Movie",
                limit = 500,
                fields = fields,
            )

            val episodeResult = jellyfinRepository.getLibraryItems(
                itemTypes = "Episode",
                limit = 500,
                fields = fields,
            )

            val allVideos = mutableListOf<BaseItemDto>()

            when (movieResult) {
                is ApiResult.Success -> allVideos.addAll(movieResult.data)
                is ApiResult.Error -> {
                    SecureLogger.e("TranscodingDiagnostics", "Failed to load movies: ${movieResult.message}")
                }
                is ApiResult.Loading -> {
                    // Ignore loading state
                }
            }

            when (episodeResult) {
                is ApiResult.Success -> allVideos.addAll(episodeResult.data)
                is ApiResult.Error -> {
                    SecureLogger.e("TranscodingDiagnostics", "Failed to load episodes: ${episodeResult.message}")
                }
                is ApiResult.Loading -> {
                    // Ignore loading state
                }
            }

            if (allVideos.isEmpty()) {
                _uiState.value = UiState.Error("No videos found in library")
                return@launch
            }

            // Analyze each video
            val analyses = allVideos.mapNotNull { item ->
                analyzeVideo(item)
            }.sortedWith(
                compareByDescending<VideoAnalysis> { it.needsTranscoding }
                    .thenBy { it.itemType } // Group Movies and Episodes
                    .thenBy { it.name },
            )

            _uiState.value = UiState.Success(analyses)
        }
    }

    private suspend fun analyzeVideo(item: BaseItemDto): VideoAnalysis? {
        val name = item.name ?: "Unknown"
        val id = item.id.toString()
        val itemType = item.type.name

        val mediaSource = item.mediaSources?.firstOrNull()
        val videoStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.VIDEO }
        val audioStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.AUDIO }
        val analysis = runCatching { enhancedPlaybackUtils.analyzePlaybackCapabilities(item) }.getOrElse { error ->
            SecureLogger.e("TranscodingDiagnostics", "Failed to analyze item $id", error)
            return null
        }

        val container = mediaSource?.container

        return VideoAnalysis(
            id = id,
            name = name,
            videoCodec = videoStream?.codec?.uppercase() ?: "UNKNOWN",
            audioCodec = audioStream?.codec?.uppercase() ?: "UNKNOWN",
            container = container?.uppercase() ?: "UNKNOWN",
            resolution = buildResolutionString(videoStream),
            needsTranscoding = analysis.preferredMethod != com.rpeters.jellyfin.ui.utils.PlaybackMethod.DIRECT_PLAY,
            methodLabel = analysis.methodLabel,
            transcodingReasons = analysis.transcodeReasons,
            breakdown = analysis.breakdown,
            item = item,
            itemType = itemType,
        )
    }

    private fun buildResolutionString(stream: MediaStream?): String {
        val width = stream?.width
        val height = stream?.height

        return when {
            height == null || width == null -> "Unknown"
            height >= 2160 -> "4K (${width}x$height)"
            height >= 1440 -> "1440p (${width}x$height)"
            height >= 1080 -> "1080p (${width}x$height)"
            height >= 720 -> "720p (${width}x$height)"
            else -> "${width}x$height"
        }
    }
}
