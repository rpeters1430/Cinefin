package com.rpeters.jellyfin.ui.player.enhanced

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color

/**
 * Additional data classes and enums for enhanced video player components
 * Core data classes are in EnhancedVideoPlayerData.kt to avoid duplicates
 */

// Cast queue support - unique to this file
data class CastQueueItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val duration: Long,
    val thumbnailUrl: String? = null,
    val isCurrentlyPlaying: Boolean = false,
    val mediaUrl: String,
    val mediaType: String = "video",
)

// Subtitle settings - unique to this file
data class SubtitleSettings(
    val enabled: Boolean = false,
    val fontSize: Float = 16f,
    val fontColor: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.8f),
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
    val fontFamily: String = "Default",
)

// Cast state - unique to this file
enum class CastState {
    DISCONNECTED, CONNECTING, CONNECTED, CASTING, ERROR
}

// Media queue item - unique to this file
data class MediaQueueItem(
    val id: String,
    val title: String,
    val duration: Long,
    val thumbnailUrl: String? = null,
)

// Basic player state - unique to this file
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackState: PlaybackState = PlaybackState.IDLE,
)

enum class PlaybackState {
    IDLE, BUFFERING, READY, ENDED
}

// For compatibility with missing icons
object MissingIcons {
    val Adaptive = Icons.Default.Settings
    val Bandwidth = Icons.Default.Wifi
    val PowerSavingsMode = Icons.Default.BatteryChargingFull
    val Surround = Icons.Default.Speaker
    val HeadphonesOff = Icons.Default.Headset
    val AudioTrack = Icons.Default.VolumeUp
    val Play = Icons.Default.PlayArrow
}

// Color extensions for missing colors
object MissingColors {
    val Orange = Color(0xFFFF9800)
}
