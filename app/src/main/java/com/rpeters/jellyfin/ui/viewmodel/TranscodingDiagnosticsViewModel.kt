package com.rpeters.jellyfin.ui.viewmodel

import android.media.MediaCodecList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

        // Get media streams
        val videoStream = item.mediaSources?.firstOrNull()?.mediaStreams?.find {
            it.type == MediaStreamType.VIDEO
        }

        val audioStream = item.mediaSources?.firstOrNull()?.mediaStreams?.find {
            it.type == MediaStreamType.AUDIO
        }

        val container = item.mediaSources?.firstOrNull()?.container?.uppercase() ?: "UNKNOWN"

        // Extract codec info
        val videoCodec = videoStream?.codec?.uppercase() ?: "UNKNOWN"
        val audioCodec = audioStream?.codec?.uppercase() ?: "UNKNOWN"
        val resolution = buildResolutionString(videoStream)

        // Analyze if transcoding is needed
        val reasons = mutableListOf<String>()

        // Check video codec support
        if (!isVideoCodecSupported(videoCodec)) {
            reasons.add("Video codec '$videoCodec' not hardware supported")
        }

        // Check audio codec support
        if (!isAudioCodecSupported(audioCodec)) {
            reasons.add("Audio codec '$audioCodec' needs transcoding to AAC")
        }

        // Check container format
        if (!isContainerSupported(container)) {
            reasons.add("Container '$container' requires remuxing to MP4/TS")
        }

        // Check bitrate (if very high)
        val videoBitrate = videoStream?.bitRate
        if (videoBitrate != null && videoBitrate > 80_000_000) { // > 80 Mbps
            reasons.add("Very high bitrate (${videoBitrate / 1_000_000}Mbps) may need transcoding")
        }

        return VideoAnalysis(
            id = id,
            name = name,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            container = container,
            resolution = resolution,
            needsTranscoding = reasons.isNotEmpty(),
            transcodingReasons = reasons,
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

    private fun isVideoCodecSupported(codec: String): Boolean {
        // Check if codec is hardware accelerated on this device
        val supportedCodecs = listOf(
            "H264",
            "AVC", // H.264 - widely supported
            "MPEG4", // MPEG-4 Part 2
        )

        // Check if any supported codec matches
        if (supportedCodecs.any { codec.contains(it, ignoreCase = true) }) {
            return true
        }

        // Check for H.265/HEVC, VP9, AV1 hardware support
        return when {
            codec.contains("HEVC", ignoreCase = true) ||
                codec.contains("H265", ignoreCase = true) ->
                isCodecHardwareAccelerated("video/hevc")

            codec.contains("VP9", ignoreCase = true) ->
                isCodecHardwareAccelerated("video/x-vnd.on2.vp9")

            codec.contains("AV1", ignoreCase = true) ->
                isCodecHardwareAccelerated("video/av01")

            else -> false
        }
    }

    private fun isAudioCodecSupported(codec: String): Boolean {
        // AAC is universally supported and preferred
        if (codec.contains("AAC", ignoreCase = true) ||
            codec.contains("MP3", ignoreCase = true) ||
            codec.contains("OPUS", ignoreCase = true)
        ) {
            return true
        }

        // These typically need transcoding to AAC
        val needsTranscoding = listOf(
            "AC3",
            "EAC3",
            "E-AC-3", // Dolby Digital
            "DTS",
            "DTS-HD", // DTS
            "TRUEHD", // Dolby TrueHD
            "FLAC", // Lossless
            "PCM", // Uncompressed
        )

        return !needsTranscoding.any { codec.contains(it, ignoreCase = true) }
    }

    private fun isContainerSupported(container: String): Boolean {
        // Containers that work well with ExoPlayer without remuxing
        val supportedContainers = listOf(
            "MP4",
            "M4V", // MPEG-4
            "TS",
            "M2TS", // MPEG-TS
            "3GP",
            "3G2", // Mobile formats
            "WEBM", // WebM
        )

        return supportedContainers.any { container.contains(it, ignoreCase = true) }
    }

    private fun isCodecHardwareAccelerated(mimeType: String): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val codecInfos = codecList.codecInfos

            // Check if any decoder supports this mime type
            codecInfos.any { codecInfo ->
                !codecInfo.isEncoder &&
                    codecInfo.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        } catch (e: Exception) {
            SecureLogger.e("TranscodingDiagnostics", "Error checking codec support: ${e.message}")
            false
        }
    }
}
