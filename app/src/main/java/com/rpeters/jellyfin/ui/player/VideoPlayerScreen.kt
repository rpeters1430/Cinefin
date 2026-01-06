package com.rpeters.jellyfin.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberScreenWidthHeight
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun VideoPlayerScreen(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onCastClick: () -> Unit,
    onCastPause: () -> Unit,
    onCastResume: () -> Unit,
    onCastStop: () -> Unit,
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
    onPlayerViewBoundsChanged: (android.graphics.Rect) -> Unit = {},
    exoPlayer: ExoPlayer?,
    supportsPip: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTvDevice = remember { com.rpeters.jellyfin.utils.DeviceTypeUtils.isTvDevice(context) }

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
    val isCastAvailable = rememberIsCastAvailable()

    // Mobile/Tablet player UI below
    var controlsVisible by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(playerColors.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val currentTime = System.currentTimeMillis()
                        val doubleTapThreshold = 300L // milliseconds
                        val screenWidth = size.width.toFloat()

                        if (currentTime - lastTapTime <= doubleTapThreshold) {
                            // Double tap detected
                            val isRightSide = offset.x > screenWidth / 2
                            val seekAmount = if (isRightSide) 10000L else -10000L
                            val newPosition =
                                (playerState.currentPosition + seekAmount).coerceAtLeast(0L)

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
                    val isLeftSide = change.position.x < size.width / 2

                    // Only respond to significant vertical drags
                    if (kotlin.math.abs(deltaY) > 5f) {
                        if (isLeftSide) {
                            // Left side - brightness control (visual feedback only)
                            val brightnessChange = deltaY / (screenHeight * 0.3f)
                            if (kotlin.math.abs(brightnessChange) > 0.1f) {
                                showSeekFeedback = true
                                seekFeedbackIcon = Icons.Default.Brightness6
                                seekFeedbackText = "Brightness"
                            }
                        } else {
                            // Right side - volume control (visual feedback only)
                            val volumeChange = deltaY / (screenHeight * 0.3f)
                            if (kotlin.math.abs(volumeChange) > 0.1f) {
                                showSeekFeedback = true
                                seekFeedbackIcon = Icons.AutoMirrored.Filled.VolumeUp
                                seekFeedbackText = "Volume"
                            }
                        }
                    }
                }
            },
    ) {
        // Video Player View
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false // We'll use custom controls
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { playerView ->
                // Set player and configuration
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                    // Force a surface layout after player assignment
                    playerView.requestLayout()
                }
                playerView.resizeMode = playerState.selectedAspectRatio.resizeMode
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

        // Periodically sample current position from the player for UI elements like skip buttons
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

        // Loading indicator - removed duplicate (now only shows in play button)

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
                shape = CircleShape,
                modifier = Modifier.size(100.dp),
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
                    .padding(16.dp),
            ) {
                Surface(color = playerColors.overlayScrim, shape = CircleShape) {
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
                    .padding(top = 64.dp, end = 16.dp),
            ) {
                Surface(color = playerColors.overlayScrim, shape = CircleShape) {
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

        // Video Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            VideoControlsOverlay(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onQualityChange = onQualityChange,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                onAspectRatioChange = onAspectRatioChange,
                onCastClick = onCastClick,
                onAudioTracksClick = { showAudioDialog = true },
                onSubtitlesClick = { showSubtitleDialog = true },
                onPictureInPictureClick = onPictureInPictureClick,
                onOrientationToggle = onOrientationToggle,
                onClose = onClose,
                showQualityMenu = showQualityMenu,
                onShowQualityMenu = { showQualityMenu = it },
                showAspectRatioMenu = showAspectRatioMenu,
                onShowAspectRatioMenu = { showAspectRatioMenu = it },
                showSpeedMenu = showSpeedMenu,
                onShowSpeedMenu = { showSpeedMenu = it },
                supportsPip = supportsPip,
                playerColors = playerColors,
                showCastButton = isCastAvailable,
            )
        }

        AnimatedVisibility(
            visible = playerState.isCastConnected,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
        ) {
            CastNowPlayingOverlay(
                playerState = playerState,
                onPauseCast = onCastPause,
                onResumeCast = onCastResume,
                onStopCast = onCastStop,
            )
        }

        // Audio Track Selection Dialog
        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Select Audio Track") },
                text = {
                    Column {
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
                    Column {
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
                    Column {
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

@Composable
private fun CastNowPlayingOverlay(
    playerState: VideoPlayerState,
    onPauseCast: () -> Unit,
    onResumeCast: () -> Unit,
    onStopCast: () -> Unit,
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
                        .height(180.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f,
                    requestSize = rememberScreenWidthHeight(180.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            colors = playerColors,
                        )
                        ExpressiveIconButton(
                            icon = Icons.Default.Stop,
                            contentDescription = "Stop casting",
                            onClick = onStopCast,
                            modifier = Modifier.padding(end = 4.dp),
                            colors = playerColors,
                        )
                    }
                }

                val overview = playerState.castOverview
                if (!overview.isNullOrBlank()) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun VideoControlsOverlay(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onQualityChange: (VideoQuality) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    onCastClick: () -> Unit,
    onAudioTracksClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onOrientationToggle: () -> Unit,
    onClose: () -> Unit,
    showQualityMenu: Boolean,
    onShowQualityMenu: (Boolean) -> Unit,
    showAspectRatioMenu: Boolean,
    onShowAspectRatioMenu: (Boolean) -> Unit,
    showSpeedMenu: Boolean,
    onShowSpeedMenu: (Boolean) -> Unit,
    supportsPip: Boolean,
    playerColors: VideoPlayerColors,
    showCastButton: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        playerColors.overlayScrim,
                        playerColors.overlayScrim.copy(alpha = 0.4f),
                        Color.Transparent,
                        playerColors.overlayScrim,
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY,
                ),
            ),
    ) {
        // Top bar with close button, item name, and casting button
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Close/Back button - top left
            ExpressiveIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close player",
                onClick = onClose,
                modifier = Modifier.padding(end = 8.dp),
                colors = playerColors,
            )

            // Item name with expressive styling
            Text(
                text = playerState.itemName,
                color = playerColors.overlayContent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Casting button - top right
            // Using custom button instead of MediaRouteButton to prevent unintended navigation
            if (showCastButton) {
                ExpressiveIconButton(
                    icon = if (playerState.isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                    contentDescription = if (playerState.isCasting) "Disconnect Cast" else "Cast to Device",
                    onClick = onCastClick,
                    isActive = playerState.isCasting,
                    modifier = Modifier.padding(start = 8.dp),
                    colors = playerColors,
                )
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // Main playback controls at the bottom with expressive styling
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Progress bar with time indicators
            if (playerState.duration > 0) {
                val duration = playerState.duration.toFloat()
                val progress = playerState.currentPosition.toFloat() / duration
                val buffered = playerState.bufferedPosition.toFloat() / duration

                // Time indicators above progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(playerState.currentPosition),
                        color = playerColors.overlayContent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = formatTime(playerState.duration),
                        color = playerColors.overlayContent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // Progress bar with buffering indicator and expressive styling
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Main progress slider with expressive styling
                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            val newPosition = (newProgress * playerState.duration).toLong()
                            onSeek(newPosition)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = playerColors.inactiveTrack,
                        ),
                    )
                }
            }

            // Main control bar with the requested layout:
            // Play/Pause | Stop | Progress Bar | Subtitles | Audio Format | Quality | Fullscreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Play/Pause button (left) with expressive styling
                ExpressivePlayButton(
                    icon = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                    isLoading = playerState.isLoading,
                    modifier = Modifier.size(48.dp),
                )

                // Stop button - stops playback and closes player
                ExpressiveIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Stop and close player",
                    onClick = onClose,
                    modifier = Modifier.padding(start = 8.dp),
                    colors = playerColors,
                )

                // Spacer to push buttons to the right
                Spacer(modifier = Modifier.weight(1f))

                // Subtitles button with expressive styling
                if (playerState.availableSubtitleTracks.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveIconButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = onSubtitlesClick,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag(VideoPlayerTestTags.SubtitlesButton),
                            colors = playerColors,
                        )
                        val subLabel = playerState.selectedSubtitleTrack?.format?.language?.take(2)
                            ?.uppercase()
                            ?: "Off"
                        Surface(
                            color = playerColors.chipBackground,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = subLabel,
                                color = playerColors.chipContent,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Audio format selection button with expressive styling
                if (playerState.availableAudioTracks.size > 1 || playerState.availableAudioTracks.any {
                        it.displayName.contains(
                            "AD",
                            true,
                        ) || it.displayName.contains("Commentary", true)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveIconButton(
                            icon = Icons.Default.Audiotrack,
                            contentDescription = "Audio Tracks",
                            onClick = onAudioTracksClick,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag(VideoPlayerTestTags.AudioTracksButton),
                            colors = playerColors,
                        )
                        val a = playerState.selectedAudioTrack?.format
                        val lang = a?.language?.take(2)?.uppercase() ?: "--"
                        val ch = when (val c = a?.channelCount ?: 0) {
                            in 6..8 -> "5.1"
                            2 -> "2.0"
                            else -> if (c > 0) "$c" else ""
                        }
                        val audioLabel = if (ch.isNotEmpty()) "$lang $ch" else lang
                        Surface(
                            color = playerColors.chipBackground,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = audioLabel,
                                color = playerColors.chipContent,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Quality selection button with dynamic icon and expressive styling
                Box {
                    ExpressiveIconButton(
                        icon = getQualityIcon(playerState.selectedQuality?.label),
                        contentDescription = "Quality: ${playerState.selectedQuality?.label ?: "Auto"}",
                        onClick = { onShowQualityMenu(true) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = playerColors,
                    )

                    // Enhanced quality selection dropdown with all options
                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { onShowQualityMenu(false) },
                    ) {
                        // Auto option
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Auto",
                                        fontWeight = if (playerState.selectedQuality == null) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                    if (playerState.selectedQuality == null) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                // TODO: Implement auto quality selection
                                onShowQualityMenu(false)
                            },
                        )

                        // Available qualities from player state
                        playerState.availableQualities.forEach { quality ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = quality.label,
                                            fontWeight = if (quality == playerState.selectedQuality) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                        if (quality == playerState.selectedQuality) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onQualityChange(quality)
                                    onShowQualityMenu(false)
                                },
                            )
                        }
                    }
                }

                // Playback Speed control (chip with dropdown)
                Box {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .clickable { onShowSpeedMenu(true) },
                        color = playerColors.chipBackground,
                        shape = CircleShape,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Playback speed",
                                tint = playerColors.chipContent,
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = String.format("%.2fx", playerState.playbackSpeed),
                                color = playerColors.chipContent,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { onShowSpeedMenu(false) },
                    ) {
                        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                        speeds.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = String.format("%.2fx", s),
                                            fontWeight = if (s == playerState.playbackSpeed) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (s == playerState.playbackSpeed) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onPlaybackSpeedChange(s)
                                    onShowSpeedMenu(false)
                                },
                            )
                        }
                    }
                }

                // Fullscreen button (right) - triggers PiP if not fullscreen with expressive styling
                Surface(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .testTag(VideoPlayerTestTags.PipButton),
                    color = if (supportsPip) playerColors.controlContainer else playerColors.disabledControlContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen / Picture in Picture",
                        tint = if (supportsPip) playerColors.overlayContent else playerColors.disabledIcon,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp)
                            .let { mod -> if (supportsPip) mod.clickable { onPictureInPictureClick() } else mod },
                    )
                }
            }
        }
    }
}

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

@Composable
private fun rememberVideoPlayerColors(): VideoPlayerColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        VideoPlayerColors(
            background = scheme.surface,
            overlayContent = scheme.onSurface,
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

// Expressive Icon Button component
@Composable
private fun ExpressiveIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    colors: VideoPlayerColors,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "icon_button_scale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            colors.controlContainer
        },
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else colors.overlayContent,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Preview(name = "Video Player Controls", showBackground = true, backgroundColor = 0xFF111111)
@Composable
private fun VideoPlayerControlsPreview() {
    JellyfinAndroidTheme(themePreferences = ThemePreferences.DEFAULT) {
        val playerColors = rememberVideoPlayerColors()
        val format = androidx.media3.common.Format.Builder()
            .setLanguage("en")
            .setChannelCount(6)
            .build()
        val audioTrack = TrackInfo(0, 0, format, true, "English 5.1")
        val subtitleTrack = TrackInfo(0, 0, format, true, "English")
        val previewState = VideoPlayerState(
            isPlaying = true,
            currentPosition = 2 * 60 * 1000L,
            duration = 90 * 60 * 1000L,
            bufferedPosition = 8 * 60 * 1000L,
            itemName = "Interstellar (2014)",
            availableQualities = listOf(
                VideoQuality("auto", "Auto", 0, 0, 0),
                VideoQuality("1080p", "1080p", 8000, 1920, 1080),
            ),
            selectedQuality = VideoQuality("1080p", "1080p", 8000, 1920, 1080),
            availableAudioTracks = listOf(audioTrack),
            selectedAudioTrack = audioTrack,
            availableSubtitleTracks = listOf(subtitleTrack),
            selectedSubtitleTrack = subtitleTrack,
            playbackSpeed = 1.25f,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            VideoControlsOverlay(
                playerState = previewState,
                onPlayPause = {},
                onSeek = {},
                onQualityChange = {},
                onPlaybackSpeedChange = {},
                onAspectRatioChange = {},
                onCastClick = {},
                onAudioTracksClick = {},
                onSubtitlesClick = {},
                onPictureInPictureClick = {},
                onOrientationToggle = {},
                onClose = {},
                showQualityMenu = false,
                onShowQualityMenu = {},
                showAspectRatioMenu = false,
                onShowAspectRatioMenu = {},
                showSpeedMenu = false,
                onShowSpeedMenu = {},
                supportsPip = true,
                playerColors = playerColors,
                showCastButton = false,
            )
        }
    }
}

@Preview(name = "Cast Overlay", showBackground = true, backgroundColor = 0xFF111111)
@Composable
private fun CastOverlayPreview() {
    JellyfinAndroidTheme(themePreferences = ThemePreferences.DEFAULT) {
        CastNowPlayingOverlay(
            playerState = VideoPlayerState(
                itemName = "The Bear",
                castDeviceName = "Living Room TV",
                castOverview = "Carmen brings fine dining back home while the kitchen finds its rhythm.",
            ),
            onPauseCast = {},
            onResumeCast = {},
            onStopCast = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

// Expressive Play Button component
@Composable
private fun ExpressivePlayButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isLoading) 0.9f else 1f,
        animationSpec = MotionTokens.mediaPlayEasing,
        label = "play_button_scale",
    )

    FilledIconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .scale(scale),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        AnimatedContent(
            targetState = isLoading,
            label = "play_button_content",
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// Helper function to get quality icon based on quality text
private fun getQualityIcon(qualityLabel: String?): ImageVector {
    return when {
        qualityLabel?.contains("4K", ignoreCase = true) == true -> Icons.Default.HighQuality
        qualityLabel?.contains("HD", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("SD", ignoreCase = true) == true -> Icons.Default.Sd
        qualityLabel?.contains("1080", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("720", ignoreCase = true) == true -> Icons.Default.Hd
        qualityLabel?.contains("480", ignoreCase = true) == true -> Icons.Default.Sd
        else -> Icons.Default.Movie // Default fallback icon
    }
}

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
        colors = colors,
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

@Composable
private fun rememberIsCastAvailable(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        try {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
            true
        } catch (e: Exception) {
            false
        }
    }
}
