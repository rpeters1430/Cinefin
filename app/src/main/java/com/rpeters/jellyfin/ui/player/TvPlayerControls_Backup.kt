package com.rpeters.jellyfin.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.rpeters.jellyfin.OptInAppExperimentalApis
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

/**
 * TV-optimized video player screen with D-pad navigation and remote control support
 * Features:
 * - Large, visible controls optimized for 10-foot viewing
 * - D-pad navigation for all interactive elements
 * - Focus indicators on all controls
 * - Auto-hide controls with easy recall
 * - Skip intro/credits buttons
 * - TV Picture-in-Picture support
 */
@UnstableApi
@OptInAppExperimentalApis
@Composable
fun TvVideoPlayerScreen(
    playerState: VideoPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onAudioTrackSelect: (TrackInfo) -> Unit,
    onSubtitleTrackSelect: (TrackInfo?) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    exoPlayer: ExoPlayer?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }

    // Periodically update current position for skip buttons
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosMs = exoPlayer?.currentPosition ?: 0L
            delay(500)
        }
    }

    // Handle D-pad center button to toggle controls
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (!showSettings) {
                                controlsVisible = !controlsVisible
                                true
                            } else {
                                false
                            }
                        }
                        Key.Back, Key.Escape -> {
                            if (showSettings) {
                                showSettings = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        Key.Menu -> {
                            showSettings = !showSettings
                            true
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            onPlayPause()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        // Video Player View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                    playerView.requestLayout()
                }
                playerView.resizeMode = playerState.selectedAspectRatio.resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        DisposableEffect(Unit) {
            onDispose {
                // PlayerView cleanup handled by AndroidView
            }
        }

        // Loading indicator
        if (playerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = TvMaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(64.dp),
                    strokeWidth = 6.dp,
                )
            }
        }

        // Error message
        playerState.error?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    onClick = { /* Non-interactive */ },
                    colors = CardDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.padding(56.dp),
                ) {
                    TvText(
                        text = error,
                        color = TvMaterialTheme.colorScheme.onErrorContainer,
                        style = TvMaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(48.dp),
                    )
                }
            }
        }

        // Skip Intro button
        val showSkipIntro = remember(playerState.introStartMs, playerState.introEndMs, currentPosMs) {
            val s = playerState.introStartMs
            val e = playerState.introEndMs
            s != null && e != null && currentPosMs in s..e
        }
        if (showSkipIntro) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(48.dp),
            ) {
                Surface(
                    color = TvMaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.clickable {
                        val target = playerState.introEndMs ?: (currentPosMs + 10_000)
                        onSeek(target)
                    },
                ) {
                    TvText(
                        text = "Skip Intro",
                        style = TvMaterialTheme.typography.titleLarge,
                        color = TvMaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
            }
        }

        // Skip Credits button
        val showSkipOutro = remember(playerState.outroStartMs, currentPosMs) {
            val s = playerState.outroStartMs
            s != null && currentPosMs >= s
        }
        if (showSkipOutro) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 128.dp, end = 48.dp),
            ) {
                Surface(
                    color = TvMaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.clickable {
                        val target = playerState.outroEndMs ?: (exoPlayer?.duration ?: currentPosMs)
                        onSeek(target)
                    },
                ) {
                    TvText(
                        text = "Skip Credits",
                        style = TvMaterialTheme.typography.titleLarge,
                        color = TvMaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
            }
        }

        // TV Controls Overlay
        if (!showSettings) {
            TvVideoPlayerControls(
                playerState = playerState,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onSeekBack = {
                    val newPosition = (playerState.currentPosition - 10_000).coerceAtLeast(0L)
                    onSeek(newPosition)
                },
                onSeekForward = {
                    val newPosition = (playerState.currentPosition + 10_000)
                        .coerceAtMost(playerState.duration)
                    onSeek(newPosition)
                },
                onShowSettings = { showSettings = true },
                onBack = onBack,
                controlsVisible = controlsVisible,
                onHideControls = { controlsVisible = false },
            )
        }

        // Settings Dialog
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TvPlayerSettingsDialog(
                playerState = playerState,
                onDismiss = { showSettings = false },
                onAudioTrackSelect = onAudioTrackSelect,
                onSubtitleTrackSelect = onSubtitleTrackSelect,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
            )
        }
    }
}

/**
 * TV Picture-in-Picture support
 * Android TV supports PiP starting from API 26 (Android 8.0)
 */
@UnstableApi
fun enterTvPictureInPictureMode(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = context as? Activity ?: return

        // Check if device supports PiP
        if (!activity.packageManager.hasSystemFeature("android.software.picture_in_picture")) {
            return
        }

        // TV PiP uses a different aspect ratio than mobile
        // TV screens are typically 16:9, so we use that aspect ratio
        val aspectRatio = Rational(16, 9)

        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setAutoEnterEnabled(true) // Auto-enter PiP when user presses home
                .setSeamlessResizeEnabled(true) // Smooth transitions
                .build()
        } else {
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
        }

        activity.enterPictureInPictureMode(params)
    }
}

/**
 * Check if TV PiP is supported on the current device
 */
fun isTvPipSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return false
    }
    return context.packageManager.hasSystemFeature("android.software.picture_in_picture")
}
