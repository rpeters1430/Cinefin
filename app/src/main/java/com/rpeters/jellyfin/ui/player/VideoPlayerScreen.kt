package com.rpeters.jellyfin.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import android.graphics.PixelFormat
import android.os.Build
import android.view.SurfaceView
import android.view.View

@UnstableApi
@Composable
fun VideoPlayerScreen(
    playerState: VideoPlayerState,
    subtitleAppearance: SubtitleAppearancePreferences,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onQualityChange: (VideoQuality?) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onCastClick: () -> Unit,
    onCastPause: () -> Unit,
    onCastResume: () -> Unit,
    onCastStop: () -> Unit,
    onCastDisconnect: () -> Unit,
    onCastSeek: (Long) -> Unit,
    onCastVolumeChange: (Float) -> Unit,
    onSubtitlesClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onOrientationToggle: () -> Unit,
    onAudioTrackSelect: (TrackInfo) -> Unit,
    onSubtitleTrackSelect: (TrackInfo?) -> Unit,
    onSubtitleDialogDismiss: () -> Unit,
    onCastDeviceSelect: (String) -> Unit,
    onCastDialogDismiss: () -> Unit,
    onErrorDismiss: () -> Unit,
    onClose: () -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onCancelNextEpisode: () -> Unit = {},
    onPlayerViewBoundsChanged: (android.graphics.Rect) -> Unit = {},
    onAcceptQualityRecommendation: () -> Unit = {},
    onDismissQualityRecommendation: () -> Unit = {},
    exoPlayer: ExoPlayer?,
    supportsPip: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activityRef = remember(context) { WeakReference(context as? android.app.Activity) }
    val isTvDevice = remember { com.rpeters.jellyfin.utils.DeviceTypeUtils.isTvDevice(context) }

    // Audio and Volume
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }

    // Gesture State
    var currentBrightness by remember(context) { mutableStateOf(activityRef.get()?.window?.attributes?.screenBrightness ?: -1f) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }
    var lastBrightnessUpdateMs by remember { mutableLongStateOf(0L) }
    var lastVolumeUpdateMs by remember { mutableLongStateOf(0L) }

    // TV Hand-off
    if (isTvDevice) {
        val pipState = com.rpeters.jellyfin.ui.player.tv.rememberPictureInPictureState(supportsPip = supportsPip) {
            onPictureInPictureClick()
        }
        com.rpeters.jellyfin.ui.player.tv.TvVideoPlayerScreen(
            state = playerState,
            exoPlayer = exoPlayer,
            subtitleAppearance = subtitleAppearance,
            pipState = pipState,
            onBack = { (context as? android.app.Activity)?.finish() },
            onPlayPause = onPlayPause,
            onSeekForward = { onSeek((playerState.currentPosition + 30_000).coerceAtMost(playerState.duration)) },
            onSeekBackward = { onSeek((playerState.currentPosition - 30_000).coerceAtLeast(0L)) },
            onSeekTo = onSeek,
            onShowAudio = onAudioTrackSelect,
            onShowSubtitles = onSubtitleTrackSelect,
            onErrorDismiss = onErrorDismiss,
            modifier = modifier,
        )
        return
    }

    val playerColors = rememberVideoPlayerColors()
    var controlsVisible by remember { mutableStateOf(true) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    // Gesture feedback states
    var showSeekFeedback by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf("") }
    var seekFeedbackIcon by remember { mutableStateOf(Icons.Default.FastForward) }
    var lastPlayerViewBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Control visibility timers
    LaunchedEffect(showSeekFeedback) {
        if (showSeekFeedback) {
            delay(1500)
            showSeekFeedback = false
        }
    }
    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }
    LaunchedEffect(playerState.isLoading, playerState.isPlaying) {
        if (playerState.isLoading || !playerState.isPlaying) controlsVisible = true
    }

    // Errors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(playerState.error) {
        playerState.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            onErrorDismiss()
        }
    }

    // Real-time position for gestures
    var currentPosMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exoPlayer, playerState.isPlaying, playerState.isLoading) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            currentPosMs = player.currentPosition
            delay(500)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(playerColors.background)
            .videoPlayerGestures(
                onTap = { controlsVisible = !controlsVisible },
                onDoubleTap = { isRightSide ->
                    val seekAmount = if (isRightSide) 10_000L else -10_000L
                    onSeek((currentPosMs + seekAmount).coerceIn(0L, playerState.duration))
                    showSeekFeedback = true
                    seekFeedbackIcon = if (isRightSide) Icons.Default.FastForward else Icons.Default.FastRewind
                    seekFeedbackText = if (isRightSide) "+10s" else "-10s"
                    controlsVisible = false
                },
                onVerticalDrag = { isLeftSide, deltaY ->
                    val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
                    if (isLeftSide) {
                        val brightnessChange = deltaY / (screenHeight * 0.5f)
                        val activity = activityRef.get() ?: return@videoPlayerGestures
                        val current = if (currentBrightness < 0f) 0.5f else currentBrightness
                        val next = (current + brightnessChange).coerceIn(0f, 1f)
                        activity.window.attributes = activity.window.attributes.apply { screenBrightness = next }
                        currentBrightness = next
                        showSeekFeedback = true
                        seekFeedbackIcon = Icons.Default.Brightness6
                        seekFeedbackText = "${(next * 100).toInt()}%"
                    } else {
                        val volumeChange = (deltaY / (screenHeight * 0.5f) * maxVolume).toInt()
                        val next = (currentVolume + volumeChange).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
                        currentVolume = next
                        showSeekFeedback = true
                        seekFeedbackIcon = if (next == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                        seekFeedbackText = "${(next * 100 / maxVolume)}%"
                    }
                },
            ),
    ) {
        if (playerState.isCastConnected) {
            CastRemoteScreen(
                playerState = playerState,
                onPauseCast = onCastPause,
                onResumeCast = onCastResume,
                onStopCast = onCastStop,
                onSeekCast = onCastSeek,
                onDisconnectCast = onCastDisconnect,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        keepScreenOn = true
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) view.player = exoPlayer
                    view.resizeMode = playerState.selectedAspectRatio.resizeMode
                    applySubtitleAppearance(view, subtitleAppearance)
                    // Configure HDR support when HDR content is detected
                    configureHdrSupport(view, playerState.isHdrContent)
                },
                modifier = Modifier.fillMaxSize().onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    val rect = android.graphics.Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt(),
                    )
                    if (rect.width() > 0 && rect.height() > 0 && rect != lastPlayerViewBounds) {
                        lastPlayerViewBounds = rect
                        onPlayerViewBoundsChanged(rect)
                    }
                },
            )

            GestureFeedbackOverlay(
                visible = showSeekFeedback,
                icon = seekFeedbackIcon,
                text = seekFeedbackText,
                overlayScrim = playerColors.overlayScrim,
                overlayContent = playerColors.overlayContent,
                modifier = Modifier.align(Alignment.Center),
            )

            SkipIntroOutroButtons(
                playerState = playerState,
                currentPosMs = currentPosMs,
                overlayScrim = playerColors.overlayScrim,
                overlayContent = playerColors.overlayContent,
                onSeek = onSeek,
            )

            NextEpisodeCountdownOverlay(
                visible = playerState.showNextEpisodeCountdown,
                nextEpisode = playerState.nextEpisode,
                countdown = playerState.nextEpisodeCountdown,
                overlayScrim = playerColors.overlayScrim,
                overlayContent = playerColors.overlayContent,
                onCancel = onCancelNextEpisode,
                onPlayNow = onPlayNextEpisode,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            ExpressiveVideoControls(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onSeekBy = { delta -> onSeek((playerState.currentPosition + delta).coerceIn(0L, playerState.duration)) },
                onQualityClick = { onQualityChange(null) /* Simplified for now */ },
                onAudioClick = { showAudioDialog = true },
                onCastClick = onCastClick,
                onSubtitlesClick = { showSubtitleDialog = true },
                onAspectRatioChange = onAspectRatioChange,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                onBackClick = onClose,
                onFullscreenToggle = onOrientationToggle,
                onPictureInPictureClick = onPictureInPictureClick,
                supportsPip = supportsPip,
                isVisible = controlsVisible,
            )
        }

        if (showAudioDialog) {
            AudioTrackSelectionDialog(
                availableTracks = playerState.availableAudioTracks,
                onTrackSelect = onAudioTrackSelect,
                onDismiss = { showAudioDialog = false },
            )
        }

        if (showSubtitleDialog) {
            SubtitleTrackSelectionDialog(
                availableTracks = playerState.availableSubtitleTracks,
                selectedTrack = playerState.selectedSubtitleTrack,
                onTrackSelect = onSubtitleTrackSelect,
                onDismiss = { showSubtitleDialog = false },
            )
        }

        if (playerState.showCastDialog) {
            CastDeviceSelectionDialog(
                availableDevices = playerState.availableCastDevices,
                onDeviceSelect = onCastDeviceSelect,
                onDismiss = onCastDialogDismiss,
            )
        }

        // Show quality recommendation notification when adaptive bitrate monitor suggests a change
        playerState.qualityRecommendation?.let { recommendation ->
            QualityRecommendationNotification(
                recommendation = recommendation,
                onAccept = onAcceptQualityRecommendation,
                onDismiss = onDismissQualityRecommendation,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
            )
        }

        // Thin wavy progress indicator at bottom when controls are hidden
        if (!controlsVisible && !playerState.isCastConnected && playerState.duration > 0) {
            androidx.compose.material3.LinearWavyProgressIndicator(
                progress = {
                    if (playerState.duration > 0) {
                        playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(3.dp),
                color = playerColors.overlayContent.copy(alpha = 0.8f),
                trackColor = Color.Transparent,
                amplitude = { 0.15f },
                wavelength = 48.dp,
                waveSpeed = 24.dp,
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Configure HDR support on the PlayerView's SurfaceView.
 * When HDR content is detected, the SurfaceView needs to be configured to handle HDR color spaces.
 * Without this configuration, HDR video will show a black screen even though audio plays.
 */
private fun configureHdrSupport(playerView: PlayerView, isHdrContent: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

    try {
        // Find the SurfaceView inside PlayerView
        val surfaceView = findSurfaceView(playerView) ?: return

        if (isHdrContent) {
            // Enable HDR rendering by setting the surface to support wide color gamut
            // This allows the surface to display HDR10 and HLG content properly
            surfaceView.holder.setFormat(PixelFormat.RGBA_1010102)

            com.rpeters.jellyfin.utils.SecureLogger.d(
                "VideoPlayerScreen",
                "Configured SurfaceView for HDR content with RGBA_1010102 format"
            )
        } else {
            // Use default format for SDR content
            surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        }
    } catch (e: Exception) {
        com.rpeters.jellyfin.utils.SecureLogger.w(
            "VideoPlayerScreen",
            "Failed to configure HDR support: ${e.message}"
        )
    }
}

/**
 * Recursively find the SurfaceView within a PlayerView's view hierarchy.
 */
private fun findSurfaceView(view: View): SurfaceView? {
    if (view is SurfaceView) {
        return view
    }

    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val surfaceView = findSurfaceView(child)
            if (surfaceView != null) {
                return surfaceView
            }
        }
    }

    return null
}
