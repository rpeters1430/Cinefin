package com.rpeters.jellyfin.ui.player.tv

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rpeters.jellyfin.ui.player.TrackInfo
import com.rpeters.jellyfin.ui.player.VideoPlayerState
import com.rpeters.jellyfin.ui.player.VideoPlayerViewModel
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import kotlinx.coroutines.delay
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText

private const val SEEK_INTERVAL_MS = 30_000L

@UnstableApi
@Composable
fun TvVideoPlayerRoute(
    itemId: String,
    itemName: String,
    startPositionMs: Long,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val supportsPip = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val pipState = rememberPictureInPictureState(supportsPip = supportsPip) {
        if (supportsPip && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    LaunchedEffect(itemId) {
        viewModel.initializePlayer(itemId, itemName, startPositionMs)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.releasePlayer() }
    }

    val onSeekForward = remember(playerState.currentPosition, playerState.duration) {
        {
            val base = playerState.currentPosition + SEEK_INTERVAL_MS
            val target = if (playerState.duration > 0) {
                base.coerceAtMost(playerState.duration)
            } else {
                base
            }
            viewModel.seekTo(target)
        }
    }
    val onSeekBackward = remember(playerState.currentPosition) {
        {
            val target = (playerState.currentPosition - SEEK_INTERVAL_MS).coerceAtLeast(0L)
            viewModel.seekTo(target)
        }
    }

    TvVideoPlayerScreen(
        modifier = modifier,
        state = playerState,
        exoPlayer = viewModel.exoPlayer,
        pipState = pipState,
        onBack = onExit,
        onPlayPause = viewModel::togglePlayPause,
        onSeekForward = onSeekForward,
        onSeekBackward = onSeekBackward,
        onSeekTo = viewModel::seekTo,
        onShowAudio = viewModel::selectAudioTrack,
        onShowSubtitles = viewModel::selectSubtitleTrack,
        onErrorDismiss = viewModel::clearError,
    )
}

@UnstableApi
@Composable
fun TvVideoPlayerScreen(
    state: VideoPlayerState,
    exoPlayer: ExoPlayer?,
    pipState: TvPictureInPictureState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onShowAudio: (TrackInfo) -> Unit,
    onShowSubtitles: (TrackInfo?) -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var controlsVisible by remember { mutableStateOf(true) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    val playPauseRequester = remember { FocusRequester() }
    var sliderPosition by remember { mutableStateOf(state.currentPosition.coerceAtLeast(0L).toFloat()) }
    var sliderDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        playPauseRequester.requestFocus()
    }

    LaunchedEffect(state.isPlaying, controlsVisible) {
        if (state.isPlaying && controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) controlsVisible = true
    }

    // Show error in Toast for TV
    val context = LocalContext.current
    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            android.widget.Toast.makeText(
                context,
                errorMessage,
                android.widget.Toast.LENGTH_LONG,
            ).show()
            onErrorDismiss()
        }
    }

    LaunchedEffect(state.currentPosition, state.duration) {
        if (!sliderDragging) {
            val safeDuration = state.duration.coerceAtLeast(1L)
            sliderPosition = state.currentPosition
                .coerceIn(0L, safeDuration)
                .toFloat()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .tvKeyboardHandler(
                focusManager = focusManager,
                onBack = {
                    onBack()
                },
                onPlayPause = {
                    controlsVisible = true
                    onPlayPause()
                },
                onMenu = {
                    showAudioDialog = true
                },
                onMore = {
                    showSubtitleDialog = true
                },
                onSeekForward = {
                    controlsVisible = true
                    onSeekForward()
                },
                onSeekBackward = {
                    controlsVisible = true
                    onSeekBackward()
                },
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionLeft, Key.MediaRewind -> {
                        controlsVisible = true
                        onSeekBackward()
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        controlsVisible = true
                        onSeekForward()
                        true
                    }
                    Key.Back -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            update = { it.player = exoPlayer },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvText(
                            text = state.itemName,
                            style = TvMaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatPlaybackPosition(state.currentPosition, state.duration),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { newValue ->
                                controlsVisible = true
                                sliderDragging = true
                                sliderPosition = newValue
                            },
                            onValueChangeFinished = {
                                sliderDragging = false
                                onSeekTo(sliderPosition.toLong())
                            },
                            valueRange = 0f..state.duration.coerceAtLeast(1L).toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusable()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) controlsVisible = true
                                },
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (state.duration > 0) {
                                    (state.bufferedPosition / state.duration.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(
                            text = "Rewind 30s",
                            icon = Icons.Default.FastRewind,
                            onClick = {
                                controlsVisible = true
                                onSeekBackward()
                            },
                            modifier = Modifier.onFocusChanged { if (it.isFocused) controlsVisible = true },
                        )
                        ControlButton(
                            text = if (state.isPlaying) "Pause" else "Play",
                            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            onClick = {
                                controlsVisible = true
                                onPlayPause()
                            },
                            modifier = Modifier
                                .focusRequester(playPauseRequester)
                                .onFocusChanged { if (it.isFocused) controlsVisible = true },
                        )
                        ControlButton(
                            text = "Forward 30s",
                            icon = Icons.Default.FastForward,
                            onClick = {
                                controlsVisible = true
                                onSeekForward()
                            },
                            modifier = Modifier.onFocusChanged { if (it.isFocused) controlsVisible = true },
                        )
                        ControlButton(
                            text = "Audio",
                            icon = Icons.Default.Audiotrack,
                            onClick = {
                                controlsVisible = true
                                showAudioDialog = true
                            },
                            enabled = state.availableAudioTracks.isNotEmpty(),
                            modifier = Modifier.onFocusChanged { if (it.isFocused) controlsVisible = true },
                        )
                        ControlButton(
                            text = "Subtitles",
                            icon = Icons.Default.ClosedCaption,
                            onClick = {
                                controlsVisible = true
                                showSubtitleDialog = true
                            },
                            enabled = state.availableSubtitleTracks.isNotEmpty(),
                            modifier = Modifier.onFocusChanged { if (it.isFocused) controlsVisible = true },
                        )
                        if (pipState.isSupported) {
                            ControlButton(
                                text = "Picture-in-picture",
                                icon = Icons.Default.PictureInPictureAlt,
                                onClick = {
                                    controlsVisible = true
                                    pipState.enterPictureInPicture()
                                },
                                modifier = Modifier.onFocusChanged { if (it.isFocused) controlsVisible = true },
                            )
                        }
                    }
                }
            }
        }

        if (showAudioDialog) {
            TrackSelectionDialog(
                title = "Audio Tracks",
                options = state.availableAudioTracks,
                selectedId = state.selectedAudioTrack?.displayName,
                onSelect = { track ->
                    showAudioDialog = false
                    onShowAudio(track)
                },
                onDismiss = { showAudioDialog = false },
            )
        }

        if (showSubtitleDialog) {
            TrackSelectionDialog(
                title = "Subtitles",
                options = state.availableSubtitleTracks,
                selectedId = state.selectedSubtitleTrack?.displayName,
                onSelect = { track ->
                    showSubtitleDialog = false
                    onShowSubtitles(track)
                },
                onDismiss = { showSubtitleDialog = false },
                includeNoneOption = true,
                onNoneSelected = {
                    showSubtitleDialog = false
                    onShowSubtitles(null)
                },
            )
        }
    }
}

@Composable
private fun ControlButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TvButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.focusable(),
        colors = TvButtonDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
            contentColor = TvMaterialTheme.colorScheme.onSurface,
            disabledContainerColor = TvMaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
            disabledContentColor = TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            TvText(text = text, style = TvMaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TrackSelectionDialog(
    title: String,
    options: List<TrackInfo>,
    selectedId: String?,
    onSelect: (TrackInfo) -> Unit,
    onDismiss: () -> Unit,
    includeNoneOption: Boolean = false,
    onNoneSelected: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        TvSurface(
            modifier = Modifier
                .width(600.dp)
                .padding(24.dp),
            shape = TvMaterialTheme.shapes.large,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TvText(
                    text = title,
                    style = TvMaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (includeNoneOption && onNoneSelected != null) {
                        item("none") {
                            TvCard(
                                onClick = onNoneSelected,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TvCardDefaults.colors(
                                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TvText(text = "Off", style = TvMaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    items(
                        items = options,
                        key = { track -> "${track.groupIndex}-${track.trackIndex}-${track.displayName}" },
                    ) { track ->
                        val isSelected = selectedId == track.displayName
                        TvCard(
                            onClick = { onSelect(track) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TvCardDefaults.colors(
                                containerColor = if (isSelected) {
                                    TvMaterialTheme.colorScheme.primaryContainer
                                } else {
                                    TvMaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TvText(
                                        text = track.displayName,
                                        style = TvMaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    track.format.language?.takeIf { it.isNotBlank() }?.let { language ->
                                        Text(
                                            text = language,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text(
                                        text = "Selected",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ControlButton(
                        text = "Close",
                        icon = Icons.Default.Close,
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

private fun formatPlaybackPosition(positionMs: Long, durationMs: Long): String {
    fun Long.toTimeComponents(): Triple<Long, Long, Long> {
        val totalSeconds = this / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return Triple(hours, minutes, seconds)
    }

    val (currentH, currentM, currentS) = positionMs.toTimeComponents()
    val (totalH, totalM, totalS) = durationMs.toTimeComponents()

    val currentFormatted = if (currentH > 0) {
        String.format("%d:%02d:%02d", currentH, currentM, currentS)
    } else {
        String.format("%02d:%02d", currentM, currentS)
    }
    val totalFormatted = if (durationMs <= 0L) {
        "--:--"
    } else if (totalH > 0) {
        String.format("%d:%02d:%02d", totalH, totalM, totalS)
    } else {
        String.format("%02d:%02d", totalM, totalS)
    }

    return "$currentFormatted / $totalFormatted"
}

@Composable
fun rememberPictureInPictureState(
    supportsPip: Boolean,
    enterPip: () -> Unit,
): TvPictureInPictureState {
    return remember(supportsPip, enterPip) {
        TvPictureInPictureState(supportsPip, enterPip)
    }
}

class TvPictureInPictureState internal constructor(
    val isSupported: Boolean,
    private val onEnter: () -> Unit,
) {
    fun enterPictureInPicture() {
        if (isSupported) {
            onEnter()
        }
    }
}

@Preview(name = "TV Player 1080p", device = "spec:width=1920px,height=1080px,dpi=320")
@Composable
private fun TvVideoPlayerScreen1080Preview() {
    TvVideoPlayerScreenPreview()
}

@Preview(name = "TV Player 4K", device = "spec:width=3840px,height=2160px,dpi=640")
@Composable
private fun TvVideoPlayerScreen4KPreview() {
    TvVideoPlayerScreenPreview()
}

@Composable
private fun TvVideoPlayerScreenPreview() {
    val sampleState = VideoPlayerState(
        itemName = "Sample Movie: The Compose Adventure",
        isPlaying = false,
        currentPosition = 90 * 60 * 1000L,
        duration = 120 * 60 * 1000L,
        bufferedPosition = 100 * 60 * 1000L,
        availableAudioTracks = listOf(
            TrackInfo(0, 0, androidx.media3.common.Format.Builder().setLanguage("en").build(), true, "English 5.1"),
            TrackInfo(1, 0, androidx.media3.common.Format.Builder().setLanguage("es").build(), false, "Spanish Stereo"),
        ),
        selectedAudioTrack = TrackInfo(0, 0, androidx.media3.common.Format.Builder().setLanguage("en").build(), true, "English 5.1"),
        availableSubtitleTracks = listOf(
            TrackInfo(2, 0, androidx.media3.common.Format.Builder().setLanguage("en").build(), true, "English CC"),
            TrackInfo(3, 0, androidx.media3.common.Format.Builder().setLanguage("fr").build(), false, "French"),
        ),
        selectedSubtitleTrack = TrackInfo(2, 0, androidx.media3.common.Format.Builder().setLanguage("en").build(), true, "English CC"),
    )

    TvVideoPlayerScreen(
        state = sampleState,
        exoPlayer = null,
        pipState = TvPictureInPictureState(isSupported = true) {},
        onBack = {},
        onPlayPause = {},
        onSeekForward = {},
        onSeekBackward = {},
        onSeekTo = {},
        onShowAudio = {},
        onShowSubtitles = {},
        onErrorDismiss = {},
    )
}
