package com.rpeters.jellyfin.ui.player.enhanced

import com.rpeters.jellyfin.ui.player.AspectRatioMode
import com.rpeters.jellyfin.ui.player.VideoQuality

/**
 * Enhanced player data classes - main definitions
 */

// Chapter support
data class Chapter(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val thumbnailUrl: String? = null,
)

// Subtitle support
data class SubtitleTrack(
    val id: String,
    val title: String,
    val language: String,
    val displayName: String,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val format: String,
    val url: String,
)

enum class SubtitlePosition {
    TOP, CENTER, BOTTOM
}

data class ExternalSubtitle(
    val title: String,
    val language: String,
    val url: String,
    val format: String,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
)

// Cast support
data class CastDevice(
    val id: String,
    val name: String,
    val modelName: String,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val signalStrength: Int = 100, // 0-100
    val supportsAudio: Boolean = true,
    val supportsVideo: Boolean = true,
    val iconUrl: String? = null,
)

// Enhanced player state that matches the EnhancedVideoPlayerScreen expectations
data class EnhancedVideoPlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val volume: Float = 1.0f,
    val brightness: Float = 0.5f,
    val playbackSpeed: Float = 1.0f,
    val selectedQuality: VideoQuality? = null,
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedAspectRatio: AspectRatioMode = AspectRatioMode.FILL,
    val availableAspectRatios: List<AspectRatioMode> = AspectRatioMode.values().toList(),
    val showSettings: Boolean = false,
    val isBuffering: Boolean = false,
    // Additional properties needed by components
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val itemName: String = "",
    val isCasting: Boolean = false,
    val castDeviceName: String = "",
    val isFullscreen: Boolean = false,
    val isLoading: Boolean = false,
    val isMinimized: Boolean = false,
    val availableSpeeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    val error: String? = null,
)
