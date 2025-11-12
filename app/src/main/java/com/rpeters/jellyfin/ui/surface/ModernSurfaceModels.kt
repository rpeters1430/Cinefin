package com.rpeters.jellyfin.ui.surface

import com.rpeters.jellyfin.ui.surface.SurfaceLifecycleState.BACKGROUND

/**
 * Snapshot describing the current content that should be reflected across
 * Android surfaces such as widgets, launcher shortcuts, notifications, and
 * quick settings tiles. All fields are optional and will be ignored by
 * components that do not rely on them.
 */
data class ModernSurfaceSnapshot(
    val continueWatching: List<SurfaceMediaItem> = emptyList(),
    val nowPlaying: SurfaceNowPlaying? = null,
    val downloads: List<SurfaceDownloadSummary> = emptyList(),
    val notifications: List<SurfaceNotification> = emptyList(),
    val lifecycleState: SurfaceLifecycleState = BACKGROUND,
)

/**
 * Shared representation of a media item that may be surfaced through widgets,
 * shortcuts, notifications, or tiles.
 */
data class SurfaceMediaItem(
    val id: String,
    val title: String,
    val navigationRoute: String,
    val type: SurfaceMediaType,
    val seriesName: String? = null,
    val episodeMetadata: SurfaceEpisodeMetadata? = null,
    val playbackProgress: SurfacePlaybackProgress? = null,
)

/** Metadata specific to episodic content. */
data class SurfaceEpisodeMetadata(
    val seasonNumber: Int?,
    val episodeNumber: Int?,
)

/** Playback progress for a media item. */
data class SurfacePlaybackProgress(
    val percentage: Double?,
    val positionMs: Long?,
)

/** Simplified representation of an offline download. */
data class SurfaceDownloadSummary(
    val id: String,
    val itemId: String,
    val title: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val status: String,
)

/** Generic notification payload that can be rendered or synced. */
data class SurfaceNotification(
    val id: String,
    val title: String,
    val message: String,
    val channelId: String,
    val priority: Int = 0,
)

/** Current playback state used by widgets and quick settings tiles. */
data class SurfaceNowPlaying(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val progress: SurfacePlaybackProgress?,
    val isPlaying: Boolean,
)

/** Indicates whether the process is in the foreground or background. */
enum class SurfaceLifecycleState {
    FOREGROUND,
    BACKGROUND,
}

/** Supported media types for surface content. */
enum class SurfaceMediaType {
    MOVIE,
    EPISODE,
    OTHER,
}
