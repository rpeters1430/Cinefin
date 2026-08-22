package com.rpeters.jellyfin.ui.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import org.jellyfin.sdk.model.api.BaseItemDto
import com.rpeters.jellyfin.ui.player.cast.DiscoveryState

@UnstableApi
enum class AspectRatioMode(val label: String, val description: String, val resizeMode: Int) {
    AUTO(
        "Auto",
        "Maintains aspect ratio with letterboxing",
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
    ),
    FILL(
        "Fill",
        "Stretches to fill screen (may distort)",
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
    ),
    CROP(
        "Crop",
        "Zooms to fill screen (may crop edges)",
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    ),
}

@UnstableApi
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val format: androidx.media3.common.Format,
    val isSelected: Boolean,
    val displayName: String,
)

data class VideoQuality(
    val id: String,
    val label: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
)

data class SubtitleSpec(
    val url: String,
    val mimeType: String,
    val language: String?,
    val label: String? = null,
    val isForced: Boolean = false,
) {
    companion object {
        fun fromUrl(url: String, language: String?, label: String? = null, isForced: Boolean = false): SubtitleSpec {
            val extension = url.substringAfterLast(".", "").lowercase()
            val mimeType = when (extension) {
                "vtt" -> MimeTypes.TEXT_VTT
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "ttml" -> MimeTypes.APPLICATION_TTML
                "ssa", "ass" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.TEXT_VTT // Default to VTT
            }
            return SubtitleSpec(url, mimeType, language, label, isForced)
        }
    }
}

@UnstableApi
data class VideoPlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val error: String? = null,
    val itemId: String = "",
    val itemName: String = "",
    val aspectRatio: Float = 16f / 9f,
    val selectedAspectRatio: AspectRatioMode = AspectRatioMode.AUTO,
    val availableAspectRatios: List<AspectRatioMode> = AspectRatioMode.entries.toList(),
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val isHdrContent: Boolean = false,
    val isControlsVisible: Boolean = true,
    val showSubtitleDialog: Boolean = false,
    val showAudioDialog: Boolean = false,
    val showQualityDialog: Boolean = false,
    val showCastDialog: Boolean = false,
    val availableCastDevices: List<String> = emptyList(),
    val castDiscoveryState: DiscoveryState = DiscoveryState.IDLE,
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isCastAvailable: Boolean = false,
    val isCasting: Boolean = false,
    val isCastConnected: Boolean = false,
    val castDeviceName: String? = null,
    val isCastPlaying: Boolean = false,
    val castPosterUrl: String? = null,
    // Transcoding information
    val isDirectPlaying: Boolean = false,
    val isDirectStreaming: Boolean = false,
    val isTranscoding: Boolean = false,
    val transcodingReason: String? = null,
    val playbackMethod: String = "Unknown",
    val castBackdropUrl: String? = null,
    val castOverview: String? = null,
    // Cast playback position and volume
    val castPosition: Long = 0L,
    val castDuration: Long = 0L,
    val castVolume: Float = 1.0f,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: TrackInfo? = null,
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val selectedSubtitleTrack: TrackInfo? = null,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    // Skip segment markers (ms)
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null,
    // Autoplay next episode
    val nextEpisode: BaseItemDto? = null,
    val isNextEpisodePromptDismissed: Boolean = false,
    val showNextEpisodeCountdown: Boolean = false,
    val nextEpisodeCountdown: Int = 0, // seconds remaining
    val hasEnded: Boolean = false,
    val playlistId: String? = null,
    // Adaptive bitrate quality recommendation
    val qualityRecommendation: com.rpeters.jellyfin.data.playback.QualityRecommendation? = null,
)

sealed class VideoPlayerIntent {
    data class Initialize(
        val itemId: String,
        val itemName: String,
        val startPosition: Long = 0,
        val subtitleIndex: Int? = null,
        val audioIndex: Int? = null,
        val forceOffline: Boolean = false,
        val playlistId: String? = null,
    ) : VideoPlayerIntent()
    object TogglePlayPause : VideoPlayerIntent()
    data class SeekTo(val positionMs: Long) : VideoPlayerIntent()
    data class ChangeQuality(val quality: VideoQuality?) : VideoPlayerIntent()
    data class SetPlaybackSpeed(val speed: Float) : VideoPlayerIntent()
    object ToggleMute : VideoPlayerIntent()
    data class ChangeAspectRatio(val aspectRatio: AspectRatioMode) : VideoPlayerIntent()
    object PlayNextEpisode : VideoPlayerIntent()
    object CancelNextEpisodeCountdown : VideoPlayerIntent()
    object DismissNextEpisodePrompt : VideoPlayerIntent()
    object ToggleControls : VideoPlayerIntent()
    data class SetControlsVisible(val visible: Boolean) : VideoPlayerIntent()
    object HandleCastButtonClick : VideoPlayerIntent()
    object ShowCastDialog : VideoPlayerIntent()
    object HideCastDialog : VideoPlayerIntent()
    data class SelectCastDevice(val deviceName: String) : VideoPlayerIntent()
    object PauseCast : VideoPlayerIntent()
    object ResumeCast : VideoPlayerIntent()
    object StopCast : VideoPlayerIntent()
    object DisconnectCast : VideoPlayerIntent()
    data class SeekCast(val positionMs: Long) : VideoPlayerIntent()
    data class SetCastVolume(val volume: Float) : VideoPlayerIntent()
    object ShowSubtitleDialog : VideoPlayerIntent()
    object HideSubtitleDialog : VideoPlayerIntent()
    object ShowAudioDialog : VideoPlayerIntent()
    object HideAudioDialog : VideoPlayerIntent()
    object ShowQualityDialog : VideoPlayerIntent()
    object HideQualityDialog : VideoPlayerIntent()
    data class SelectAudioTrack(val track: TrackInfo) : VideoPlayerIntent()
    data class SelectSubtitleTrack(val track: TrackInfo?) : VideoPlayerIntent()
    object AcceptQualityRecommendation : VideoPlayerIntent()
    object DismissQualityRecommendation : VideoPlayerIntent()
    object ClearError : VideoPlayerIntent()
    object ClosePlayer : VideoPlayerIntent()
    object ToggleOrientation : VideoPlayerIntent()
    object EnterPip : VideoPlayerIntent()
    object PausePlayback : VideoPlayerIntent()
    object ReleasePlayer : VideoPlayerIntent()
}

sealed class VideoPlayerSideEffect {
    object ClosePlayer : VideoPlayerSideEffect()
    data class ShowToast(val message: String) : VideoPlayerSideEffect()
}
