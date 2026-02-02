package com.rpeters.jellyfin.ui.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberScreenWidthHeight
import com.rpeters.jellyfin.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

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
    exoPlayer: ExoPlayer?,
    supportsPip: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activityRef = remember(context) { WeakReference(context as? android.app.Activity) }
    val isTvDevice = remember { com.rpeters.jellyfin.utils.DeviceTypeUtils.isTvDevice(context) }

    // Get audio manager for volume control
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }

    // Track current brightness and volume for gesture controls
    var currentBrightness by remember(context) {
        mutableStateOf(activityRef.get()?.window?.attributes?.screenBrightness ?: -1f)
    }
    var currentVolume by remember {
        mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }
    var lastBrightnessUpdateMs by remember { mutableLongStateOf(0L) }
    var lastVolumeUpdateMs by remember { mutableLongStateOf(0L) }

    // Use TV-optimized player for TV devices
    if (isTvDevice) {
        val pipState = com.rpeters.jellyfin.ui.player.tv.rememberPictureInPictureState(
            supportsPip = supportsPip,
        ) {
            onPictureInPictureClick()
        }

        com.rpeters.jellyfin.ui.player.tv.TvVideoPlayerScreen(
            state = playerState,
            exoPlayer = exoPlayer,
            subtitleAppearance = subtitleAppearance,
            pipState = pipState,
            onBack = {
                // Handle back navigation - finish activity
                (context as? android.app.Activity)?.finish()
            },
            onPlayPause = onPlayPause,
            onSeekForward = {
                val newPosition = (playerState.currentPosition + 30_000)
                    .coerceAtMost(playerState.duration)
                onSeek(newPosition)
            },
            onSeekBackward = {
                val newPosition = (playerState.currentPosition - 30_000).coerceAtLeast(0L)
                onSeek(newPosition)
            },
            onSeekTo = onSeek,
            onShowAudio = onAudioTrackSelect,
            onShowSubtitles = onSubtitleTrackSelect,
            onErrorDismiss = onErrorDismiss,
            modifier = modifier,
        )
        return
    }

    val playerColors = rememberVideoPlayerColors()
    // Use cast availability from ViewModel state instead of deprecated sync API
    val isCastAvailable = playerState.isCastAvailable

    // Mobile/Tablet player UI below
    var controlsVisible by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    val dialogMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f

    // Gesture feedback states
    var showSeekFeedback by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf("") }
    var seekFeedbackIcon by remember { mutableStateOf(Icons.Default.FastForward) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastPlayerViewBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    remember(exoPlayer) { exoPlayer }

    // Auto-hide seek feedback
    LaunchedEffect(showSeekFeedback) {
        if (showSeekFeedback) {
            delay(1500)
            showSeekFeedback = false
        }
    }

    // Auto-hide controls after 5 seconds (increased from 3)
    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(5000) // Longer timeout for better UX
            controlsVisible = false
        }
    }

    // Show controls when video starts loading or pauses
    LaunchedEffect(playerState.isLoading, playerState.isPlaying) {
        if (playerState.isLoading || !playerState.isPlaying) {
            controlsVisible = true
        }
    }

    // Snackbar for showing errors
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // Show error in Snackbar when Cast errors occur
    LaunchedEffect(playerState.error) {
        playerState.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            onErrorDismiss()
        }
    }

    // Track real-time position for accurate double-tap seeking
    var currentPosMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(exoPlayer, playerState.isPlaying, playerState.isLoading) {
        val player = exoPlayer ?: run {
            currentPosMs = 0L
            return@LaunchedEffect
        }
        currentPosMs = player.currentPosition
        while (isActive && (playerState.isPlaying || playerState.isLoading)) {
            currentPosMs = player.currentPosition
            delay(500)
        }
    }

    val baseModifier = modifier
        .fillMaxSize()
        .background(playerColors.background)
    val gestureModifier = if (playerState.isCastConnected) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val currentTime = System.currentTimeMillis()
                        val doubleTapThreshold = VideoPlayerGestureConstants.DOUBLE_TAP_THRESHOLD_MS
                        val screenWidth = size.width.toFloat()

                        if (currentTime - lastTapTime <= doubleTapThreshold) {
                            // Double tap detected
                            val isRightSide = offset.x > screenWidth / 2
                            val seekAmount = if (isRightSide) {
                                VideoPlayerGestureConstants.SEEK_AMOUNT_MS
                            } else {
                                -VideoPlayerGestureConstants.SEEK_AMOUNT_MS
                            }
                            // Use real-time position from exoPlayer, fallback to state
                            val currentPos = exoPlayer?.currentPosition ?: currentPosMs
                            val newPosition = (currentPos + seekAmount).coerceIn(0L, playerState.duration)

                            onSeek(newPosition)

                            // Show feedback
                            showSeekFeedback = true
                            seekFeedbackIcon =
                                if (isRightSide) Icons.Default.FastForward else Icons.Default.FastRewind
                            seekFeedbackText = if (isRightSide) "+10s" else "-10s"
                            controlsVisible = false // Hide controls during seek
                        } else {
                            // Single tap - toggle controls
                            controlsVisible = !controlsVisible
                        }
                        lastTapTime = currentTime
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val startY = change.previousPosition.y
                    val currentY = change.position.y
                    val deltaY = startY - currentY
                    val screenHeight = size.height.toFloat()
                    if (screenHeight <= 0f) {
                        return@detectDragGestures
                    }
                    val isLeftSide = change.position.x < size.width / 2

                    // Only respond to significant vertical drags
                    if (kotlin.math.abs(deltaY) > VideoPlayerGestureConstants.MIN_VERTICAL_DRAG_PX) {
                        if (isLeftSide) {
                            // Left side - brightness control
                            val brightnessChange =
                                deltaY / (screenHeight * VideoPlayerGestureConstants.NORMALIZATION_FRACTION)
                            if (kotlin.math.abs(brightnessChange) > VideoPlayerGestureConstants.BRIGHTNESS_MIN_DELTA) {
                                val now = System.currentTimeMillis()
                                if (now - lastBrightnessUpdateMs <
                                    VideoPlayerGestureConstants.GESTURE_UPDATE_MIN_INTERVAL_MS
                                ) {
                                    return@detectDragGestures
                                }
                                lastBrightnessUpdateMs = now
                                val activity = activityRef.get()
                                if (activity == null) {
                                    return@detectDragGestures
                                }
                                // Get current brightness (-1 means system default)
                                val currentBrightnessValue = if (currentBrightness < 0f) {
                                    VideoPlayerGestureConstants.DEFAULT_BRIGHTNESS
                                } else {
                                    currentBrightness
                                }
                                // Calculate new brightness (0.0 to 1.0)
                                val newBrightness = (currentBrightnessValue + brightnessChange).coerceIn(0.0f, 1.0f)

                                // Apply brightness change
                                try {
                                    val layoutParams = activity.window.attributes
                                    layoutParams.screenBrightness = newBrightness
                                    activity.window.attributes = layoutParams

                                    // Update state and show feedback
                                    currentBrightness = newBrightness
                                    showSeekFeedback = true
                                    seekFeedbackIcon = Icons.Default.Brightness6
                                    seekFeedbackText = "${(newBrightness * 100).toInt()}%"
                                } catch (securityException: SecurityException) {
                                    // Ignore and keep UI responsive if brightness changes are blocked.
                                }
                            }
                        } else {
                            // Right side - volume control
                            if (maxVolume <= 0) {
                                return@detectDragGestures
                            }
                            val volumeChange =
                                (deltaY / (screenHeight * VideoPlayerGestureConstants.NORMALIZATION_FRACTION) * maxVolume)
                                    .toInt()
                            if (kotlin.math.abs(volumeChange) > 0) {
                                val now = System.currentTimeMillis()
                                if (now - lastVolumeUpdateMs <
                                    VideoPlayerGestureConstants.GESTURE_UPDATE_MIN_INTERVAL_MS
                                ) {
                                    return@detectDragGestures
                                }
                                lastVolumeUpdateMs = now
                                // Calculate new volume
                                val newVolume = (currentVolume + volumeChange).coerceIn(0, maxVolume)

                                // Apply volume change
                                audioManager.setStreamVolume(
                                    android.media.AudioManager.STREAM_MUSIC,
                                    newVolume,
                                    0,
                                )

                                // Update state and show feedback
                                currentVolume = newVolume
                                showSeekFeedback = true
                                seekFeedbackIcon = if (newVolume == 0) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                }
                                seekFeedbackText = "${(newVolume * 100 / maxVolume)}%"
                            }
                        }
                    }
                }
            }
    }

    Box(
        modifier = baseModifier.then(gestureModifier),
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
            // Video Player View
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false // We'll use custom controls
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        // Keep surface alive during lifecycle changes to prevent black screen
                        keepScreenOn = true
                    }
                },
                update = { playerView ->
                    // Set player and configuration
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                        // Force surface recreation and layout to ensure proper attachment
                        // This is critical for high-resolution HEVC content
                        playerView.post {
                            playerView.invalidate()
                            playerView.requestLayout()
                        }
                    }
                    playerView.resizeMode = playerState.selectedAspectRatio.resizeMode
                    applySubtitleAppearance(playerView, subtitleAppearance)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
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

            // Ensure the PlayerView detaches cleanly to avoid setOutputSurface errors
            DisposableEffect(Unit) {
                onDispose {
                    // The AndroidView manages lifecycle, but explicitly clear association
                    // to avoid surface detachment warnings during rapid teardown.
                    // We cannot reference the composited playerView instance here,
                    // but releasing ExoPlayer clears the surface; this hook remains for clarity.
                }
            }

            // Loading indicator - removed duplicate (now only shows in play button)

            // Gesture Feedback Overlay
            AnimatedVisibility(
                visible = showSeekFeedback,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = playerColors.overlayScrim,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.size(120.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = seekFeedbackIcon,
                            contentDescription = null,
                            tint = playerColors.overlayContent,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = seekFeedbackText,
                            color = playerColors.overlayContent,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Skip Intro/Outro buttons
            val showSkipIntro =
                remember(playerState.introStartMs, playerState.introEndMs, currentPosMs) {
                    val s = playerState.introStartMs
                    val e = playerState.introEndMs
                    s != null && e != null && currentPosMs in s..e
                }
            val showSkipOutro = remember(playerState.outroStartMs, currentPosMs) {
                val s = playerState.outroStartMs
                s != null && currentPosMs >= s
            }

            if (showSkipIntro) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp),
                ) {
                    Surface(
                        color = playerColors.overlayScrim,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            text = "Skip Intro",
                            color = playerColors.overlayContent,
                            modifier = Modifier
                                .clickable {
                                    val target = playerState.introEndMs ?: (currentPosMs + 10_000)
                                    onSeek(target)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (showSkipOutro) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 130.dp, end = 16.dp),
                ) {
                    Surface(
                        color = playerColors.overlayScrim,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 4.dp,
                    ) {
                        Text(
                            text = "Skip Credits",
                            color = playerColors.overlayContent,
                            modifier = Modifier
                                .clickable {
                                    val target =
                                        playerState.outroEndMs ?: (exoPlayer?.duration ?: currentPosMs)
                                    onSeek(target)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = playerState.showNextEpisodeCountdown,
                enter = fadeIn(animationSpec = MotionTokens.expressiveEnter),
                exit = fadeOut(animationSpec = MotionTokens.expressiveExit),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = playerColors.overlayScrim,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .padding(bottom = 100.dp)
                        .fillMaxWidth(0.85f),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Next Episode",
                            color = playerColors.overlayContent,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        playerState.nextEpisode?.let { nextEp ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = nextEp.name ?: "Episode ${nextEp.indexNumber}",
                                color = playerColors.overlayContent,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Starting in ${playerState.nextEpisodeCountdown}...",
                            color = playerColors.overlayContent.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TextButton(
                                onClick = {
                                    onCancelNextEpisode()
                                    onClose()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = playerColors.overlayContent,
                                ),
                            ) {
                                Text("Close")
                            }
                            Button(
                                onClick = onPlayNextEpisode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text("Play Now")
                            }
                        }
                    }
                }
            }

            // Video Controls Overlay
            ExpressiveVideoControls(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onSeekBy = { delta ->
                    val newPos = (playerState.currentPosition + delta).coerceIn(0L, playerState.duration)
                    onSeek(newPos)
                },
                onQualityClick = { showQualityMenu = true },
                onAudioClick = { showAudioDialog = true },
                onCastClick = onCastClick,
                onSubtitlesClick = { showSubtitleDialog = true },
                onAspectRatioChange = onAspectRatioChange,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                onBackClick = onClose,
                onFullscreenToggle = onOrientationToggle,
                isVisible = controlsVisible,
            )
        }

        // Error message
        playerState.error?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Audio Track Selection Dialog
        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Select Audio Track") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = dialogMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        playerState.availableAudioTracks.forEach { track ->
                            TextButton(
                                onClick = {
                                    onAudioTrackSelect(track)
                                    showAudioDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = track.displayName,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) { Text("Close") }
                },
            )
        }

        // Subtitle Track Selection Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text("Subtitles") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = dialogMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        TextButton(
                            onClick = {
                                onSubtitleTrackSelect(null)
                                showSubtitleDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Off") }

                        playerState.availableSubtitleTracks.forEach { track ->
                            TextButton(
                                onClick = {
                                    onSubtitleTrackSelect(track)
                                    showSubtitleDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = track.displayName,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) { Text("Close") }
                },
            )
        }

        // Subtitle Selection Dialog
        if (playerState.showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = onSubtitleDialogDismiss,
                title = { Text("Select Subtitles") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = dialogMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // Option to disable subtitles
                        TextButton(
                            onClick = { onSubtitleTrackSelect(null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Off",
                                fontWeight = if (playerState.selectedSubtitleTrack == null) FontWeight.Bold else FontWeight.Normal,
                            )
                        }

                        // List available subtitle tracks
                        playerState.availableSubtitleTracks.forEach { track ->
                            TextButton(
                                onClick = { onSubtitleTrackSelect(track) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = track.displayName,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onSubtitleDialogDismiss) {
                        Text("Close")
                    }
                },
            )
        }

        // Cast Device Selection Dialog
        if (playerState.showCastDialog) {
            AlertDialog(
                onDismissRequest = onCastDialogDismiss,
                title = { Text("Cast to Device") },
                text = {
                    Column {
                        if (playerState.availableCastDevices.isEmpty()) {
                            Text("No Cast devices found. Make sure your Chromecast or other Cast-enabled device is on the same network.")
                        } else {
                            playerState.availableCastDevices.forEach { device ->
                                TextButton(
                                    onClick = { onCastDeviceSelect(device) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(device)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCastDialogDismiss) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CastRemoteScreen(
    playerState: VideoPlayerState,
    onPauseCast: () -> Unit,
    onResumeCast: () -> Unit,
    onStopCast: () -> Unit,
    onSeekCast: (Long) -> Unit,
    onDisconnectCast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = playerState.castBackdropUrl ?: playerState.castPosterUrl
    val playerColors = rememberVideoPlayerColors()
    val deviceName = playerState.castDeviceName ?: "Cast device"

    Box(
        modifier = modifier
            .background(playerColors.background)
            .testTag(VideoPlayerTestTags.CastOverlay),
    ) {
        if (!artwork.isNullOrBlank()) {
            JellyfinAsyncImage(
                model = artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
                requestSize = rememberScreenWidthHeight(360.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            playerColors.gradientStops,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "Playing on $deviceName",
                        style = MaterialTheme.typography.labelLarge,
                        color = playerColors.overlayContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = playerState.itemName.ifBlank { "Casting" },
                        style = MaterialTheme.typography.titleMedium,
                        color = playerColors.overlayContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDisconnectCast,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = playerColors.overlayScrim,
                        contentColor = playerColors.overlayContent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disconnect cast",
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!playerState.castPosterUrl.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = playerColors.overlayScrim,
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .heightIn(max = 320.dp),
                        ) {
                            JellyfinAsyncImage(
                                model = playerState.castPosterUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                                contentScale = ContentScale.Crop,
                                requestSize = rememberScreenWidthHeight(320.dp),
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = playerColors.overlayContent.copy(alpha = 0.7f),
                            modifier = Modifier.size(72.dp),
                        )
                    }

                    if (!playerState.castOverview.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = playerState.castOverview ?: "",
                            color = playerColors.overlayContent.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val duration = playerState.castDuration
                val position = playerState.castPosition
                if (duration > 0L) {
                    Text(
                        text = "${formatDuration(position)} / ${formatDuration(duration)}",
                        color = playerColors.overlayContent.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = {
                            val seekBack = (position - 10_000).coerceAtLeast(0L)
                            onSeekCast(seekBack)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = playerColors.overlayScrim,
                            contentColor = playerColors.overlayContent,
                        ),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10 seconds",
                        )
                    }

                    FilledIconButton(
                        onClick = if (playerState.isCastPlaying) onPauseCast else onResumeCast,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            imageVector = if (playerState.isCastPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isCastPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            val seekForward = if (duration > 0L) {
                                (position + 10_000).coerceAtMost(duration)
                            } else {
                                position + 10_000
                            }
                            onSeekCast(seekForward)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = playerColors.overlayScrim,
                            contentColor = playerColors.overlayContent,
                        ),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10 seconds",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FilledIconButton(
                    onClick = onStopCast,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = playerColors.overlayScrim,
                        contentColor = playerColors.overlayContent,
                    ),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop casting",
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CastNowPlayingOverlay(
    playerState: VideoPlayerState,
    onPauseCast: () -> Unit,
    onResumeCast: () -> Unit,
    onStopCast: () -> Unit,
    onSeekCast: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = playerState.castBackdropUrl ?: playerState.castPosterUrl

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(VideoPlayerTestTags.CastOverlay),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        ),
    ) {
        Box {
            if (!artwork.isNullOrBlank()) {
                JellyfinAsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f,
                    requestSize = rememberScreenWidthHeight(220.dp),
                )

                val playerColors = rememberVideoPlayerColors()
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                playerColors.gradientStops,
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playerState.itemName.ifBlank { "Casting" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        playerState.castDeviceName?.let { deviceName ->
                            Text(
                                text = "Playing on $deviceName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val playerColors = rememberVideoPlayerColors()
                        ExpressiveIconButton(
                            icon = if (playerState.isCastPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isCastPlaying) "Pause cast playback" else "Resume cast playback",
                            onClick = if (playerState.isCastPlaying) onPauseCast else onResumeCast,
                            isActive = playerState.isCastPlaying,
                        )
                        ExpressiveIconButton(
                            icon = Icons.Default.Stop,
                            contentDescription = "Stop casting",
                            onClick = onStopCast,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }

                // Seek bar with position/duration
                if (playerState.castDuration > 0) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        var seekPosition by remember { mutableStateOf(playerState.castPosition.toFloat()) }
                        var isSeeking by remember { mutableStateOf(false) }

                        // Update seek position from state when not actively seeking
                        LaunchedEffect(playerState.castPosition) {
                            if (!isSeeking) {
                                seekPosition = playerState.castPosition.toFloat()
                            }
                        }

                        Slider(
                            value = seekPosition,
                            onValueChange = { newValue ->
                                isSeeking = true
                                seekPosition = newValue
                            },
                            onValueChangeFinished = {
                                onSeekCast(seekPosition.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..playerState.castDuration.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            ),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatDuration(if (isSeeking) seekPosition.toLong() else playerState.castPosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatDuration(playerState.castDuration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Volume control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (playerState.castVolume > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Volume",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Slider(
                        value = playerState.castVolume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        ),
                    )
                    Text(
                        text = "${(playerState.castVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                    )
                }

                val overview = playerState.castOverview
                if (!overview.isNullOrBlank()) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@UnstableApi
private data class VideoPlayerColors(
    val background: Color,
    val overlayContent: Color,
    val overlayScrim: Color,
    val gradientStops: List<Color>,
    val chipBackground: Color,
    val chipContent: Color,
    val inactiveTrack: Color,
    val controlContainer: Color,
    val disabledControlContainer: Color,
    val disabledIcon: Color,
)

private object VideoPlayerGestureConstants {
    const val DOUBLE_TAP_THRESHOLD_MS = 300L
    const val SEEK_AMOUNT_MS = 10_000L
    const val MIN_VERTICAL_DRAG_PX = 5f
    const val NORMALIZATION_FRACTION = 0.5f
    const val BRIGHTNESS_MIN_DELTA = 0.01f
    const val DEFAULT_BRIGHTNESS = 0.5f
    const val GESTURE_UPDATE_MIN_INTERVAL_MS = 50L
    val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
}

@OptIn(UnstableApi::class)
@Composable
private fun rememberVideoPlayerColors(): VideoPlayerColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        VideoPlayerColors(
            background = Color.Black,
            // Use white text over dark scrim (Material Design pattern for video overlays)
            overlayContent = Color.White,
            overlayScrim = scheme.scrim.copy(alpha = 0.75f),
            gradientStops = listOf(
                scheme.surfaceContainerHighest.copy(alpha = 0.96f),
                scheme.scrim.copy(alpha = 0.35f),
                scheme.surfaceContainerHighest.copy(alpha = 0.96f),
            ),
            chipBackground = scheme.surfaceContainerHighest.copy(alpha = 0.85f),
            chipContent = scheme.onSurface,
            inactiveTrack = scheme.onSurfaceVariant.copy(alpha = 0.3f),
            controlContainer = scheme.surfaceContainerHighest.copy(alpha = 0.75f),
            disabledControlContainer = scheme.surfaceContainer.copy(alpha = 0.4f),
            disabledIcon = scheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CastButton(
    isCasting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: VideoPlayerColors,
) {
    ExpressiveIconButton(
        icon = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
        contentDescription = if (isCasting) "Disconnect Cast" else "Cast to Device",
        onClick = onClick,
        isActive = isCasting,
        modifier = modifier,
    )
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

// Removed rememberIsCastAvailable() - now using playerState.isCastAvailable from ViewModel
// This avoids the deprecated blocking CastContext.getSharedInstance() call during composition
