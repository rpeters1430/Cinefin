package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import javax.inject.Inject

@HiltViewModel
class TranscodingDiagnosticsViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val deviceCapabilities: DeviceCapabilities,
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
        val transcodingReasons: List<String>,
    )

    fun loadLibraryVideos() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // Get all movie and episode items
            val movieResult = jellyfinRepository.getLibraryItems(
                itemTypes = "Movie",
                limit = 500,
            )

            val episodeResult = jellyfinRepository.getLibraryItems(
                itemTypes = "Episode",
                limit = 500,
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
                    .thenBy { it.name },
            )

            _uiState.value = UiState.Success(analyses)
        }
    }

    private fun analyzeVideo(item: BaseItemDto): VideoAnalysis? {
        val name = item.name ?: "Unknown"
        val id = item.id.toString()

        val mediaSource = item.mediaSources?.firstOrNull()
        val videoStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.VIDEO }
        val audioStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.AUDIO }

        val container = mediaSource?.container
        val videoCodec = videoStream?.codec
        val audioCodec = audioStream?.codec
        val width = videoStream?.width ?: 0
        val height = videoStream?.height ?: 0
        val bitrate = mediaSource?.bitrate ?: 0

        // Use the central DeviceCapabilities for analysis
        val analysis = deviceCapabilities.analyzeDirectPlayCompatibility(
            container = container,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            width = width,
            height = height,
            bitrate = bitrate,
        )

        return VideoAnalysis(
            id = id,
            name = name,
            videoCodec = videoCodec?.uppercase() ?: "UNKNOWN",
            audioCodec = audioCodec?.uppercase() ?: "UNKNOWN",
            container = container?.uppercase() ?: "UNKNOWN",
            resolution = buildResolutionString(videoStream),
            needsTranscoding = !analysis.canDirectPlay,
            transcodingReasons = analysis.issues,
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
