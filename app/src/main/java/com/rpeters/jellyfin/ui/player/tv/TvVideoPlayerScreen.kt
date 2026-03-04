package com.rpeters.jellyfin.ui.player.tv

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import com.rpeters.jellyfin.ui.player.TrackInfo
import com.rpeters.jellyfin.ui.player.VideoPlayerState
import com.rpeters.jellyfin.ui.player.VideoPlayerViewModel
import com.rpeters.jellyfin.ui.player.applySubtitleAppearance
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.SubtitleAppearancePreferencesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.compose.ui.tooling.preview.Preview

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
    subtitlePreferencesViewModel: SubtitleAppearancePreferencesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val supportsPip = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val subtitleAppearance by subtitlePreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val pipState = rememberPictureInPictureState(supportsPip = supportsPip) {
        if (supportsPip && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(itemId) {
        viewModel.initializePlayer(itemId, itemName, startPositionMs)
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                viewModel.releasePlayer()
            }
        }
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
        subtitleAppearance = subtitleAppearance,
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
    subtitleAppearance: SubtitleAppearancePreferences,
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
    var controlsVisible by remember { mutableStateOf(true) }
    var showQuickSettings by remember { mutableStateOf(false) }

    val playPauseRequester = remember { FocusRequester() }
    val quickSettingsCloseRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        playPauseRequester.requestFocus()
    }

    // Auto-hide controls
    LaunchedEffect(state.isPlaying, controlsVisible, showQuickSettings) {
        if (state.isPlaying && controlsVisible && !showQuickSettings) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(showQuickSettings) {
        if (showQuickSettings) {
            controlsVisible = true
            quickSettingsCloseRequester.requestFocus()
        } else {
            playPauseRequester.requestFocus()
        }
    }

    // Show error in Toast
    val context = LocalContext.current
    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
            onErrorDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                
                // If settings is open, let it handle keys or close it on back
                if (showQuickSettings) {
                    if (keyEvent.key == Key.Back) {
                        showQuickSettings = false
                        return@onKeyEvent true
                    }
                    // Trap unhandled keys while settings is open so playback controls
                    // underneath do not react unexpectedly.
                    return@onKeyEvent true
                }

                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        controlsVisible = true
                        true
                    }
                    Key.DirectionDown -> {
                        if (controlsVisible) {
                            showQuickSettings = true
                        } else {
                            controlsVisible = true
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                        } else {
                            onPlayPause()
                        }
                        true
                    }
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
                        if (controlsVisible) {
                            controlsVisible = false
                        } else {
                            onBack()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Video Layer
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                applySubtitleAppearance(playerView, subtitleAppearance)
            },
        )

        // Controls Layer
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
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f),
                            ),
                        )
                    )
            ) {
                // Top Info
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(56.dp)
                ) {
                    TvText(
                        text = state.itemName,
                        style = TvMaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 64.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Enhanced Seek Bar
                    TvSeekBar(
                        currentPosition = state.currentPosition,
                        duration = state.duration,
                        bufferedPosition = state.bufferedPosition,
                        onSeekTo = onSeekTo
                    )

                    // Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvPlayerButton(
                            icon = Icons.Default.FastRewind,
                            onClick = onSeekBackward
                        )
                        
                        TvPlayerButton(
                            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(64.dp)
                                .focusRequester(playPauseRequester),
                            isPrimary = true
                        )
                        
                        TvPlayerButton(
                            icon = Icons.Default.FastForward,
                            onClick = onSeekForward
                        )

                        Spacer(modifier = Modifier.width(32.dp))

                        TvPlayerButton(
                            icon = Icons.Default.Settings,
                            onClick = {
                                controlsVisible = true
                                showQuickSettings = true
                            },
                            label = "Settings"
                        )
                    }
                }
            }
        }

        // Quick Settings Side Drawer
        AnimatedVisibility(
            visible = showQuickSettings,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            TvQuickSettingsDrawer(
                state = state,
                onShowAudio = { onShowAudio(it); showQuickSettings = false },
                onShowSubtitles = { onShowSubtitles(it); showQuickSettings = false },
                onClose = { showQuickSettings = false },
                closeButtonFocusRequester = quickSettingsCloseRequester,
            )
        }
    }
}

@Composable
fun TvSeekBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = duration.coerceAtLeast(1L)
    var isFocused by remember { mutableStateOf(false) }
    var pendingPosition by remember(duration) { mutableStateOf(currentPosition.coerceIn(0L, safeDuration)) }
    val progress = if (duration > 0) pendingPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f

    LaunchedEffect(currentPosition, safeDuration, isFocused) {
        if (!isFocused) {
            pendingPosition = currentPosition.coerceIn(0L, safeDuration)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        pendingPosition = (pendingPosition - SEEK_INTERVAL_MS).coerceAtLeast(0L)
                        onSeekTo(pendingPosition)
                        true
                    }
                    Key.DirectionRight -> {
                        pendingPosition = (pendingPosition + SEEK_INTERVAL_MS).coerceAtMost(safeDuration)
                        onSeekTo(pendingPosition)
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        onSeekTo(pendingPosition)
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            // Background
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.2f), TvMaterialTheme.shapes.small))
            
            // Buffered
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.3f), TvMaterialTheme.shapes.small)
            )
            
            // Progress
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(TvMaterialTheme.colorScheme.primary, TvMaterialTheme.shapes.small)
            )
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TvText(
                text = formatTime(pendingPosition),
                style = TvMaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            TvText(
                text = formatTime(duration),
                style = TvMaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun TvPlayerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    isPrimary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TvSurface(
            onClick = onClick,
            modifier = modifier
                .size(if (isPrimary) 64.dp else 48.dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(TvMaterialTheme.shapes.extraLarge),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isPrimary) TvMaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f),
                focusedContainerColor = if (isPrimary) TvMaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.25f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                TvIcon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(if (isPrimary) 32.dp else 24.dp),
                    tint = if (isPrimary) TvMaterialTheme.colorScheme.onPrimary else Color.White
                )
            }
        }
        if (label != null) {
            TvText(
                text = label,
                style = TvMaterialTheme.typography.labelSmall,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun TvQuickSettingsDrawer(
    state: VideoPlayerState,
    onShowAudio: (TrackInfo) -> Unit,
    onShowSubtitles: (TrackInfo?) -> Unit,
    onClose: () -> Unit,
    closeButtonFocusRequester: FocusRequester? = null,
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Audio, 1: Subtitles

    TvSurface(
        modifier = Modifier
            .fillMaxHeight()
            .width(400.dp),
        colors = androidx.tv.material3.SurfaceDefaults.colors(
            containerColor = Color(0xFF1A1A1A).copy(alpha = 0.95f)
        ),
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            TvText(
                text = "Settings",
                style = TvMaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tabs
            Row(modifier = Modifier.fillMaxWidth()) {
                TvTabButton(text = "Audio", selected = selectedTab == 0, onClick = { selectedTab = 0 }, modifier = Modifier.weight(1f))
                TvTabButton(text = "Subtitles", selected = selectedTab == 1, onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Track List
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == 0) {
                    items(state.availableAudioTracks) { track ->
                        val isSelected = state.selectedAudioTrack?.displayName == track.displayName
                        TvTrackItem(
                            title = track.displayName,
                            isSelected = isSelected,
                            onClick = { onShowAudio(track) }
                        )
                    }
                } else {
                    item {
                        TvTrackItem(
                            title = "None",
                            isSelected = state.selectedSubtitleTrack == null,
                            onClick = { onShowSubtitles(null) }
                        )
                    }
                    items(state.availableSubtitleTracks) { track ->
                        val isSelected = state.selectedSubtitleTrack?.displayName == track.displayName
                        TvTrackItem(
                            title = track.displayName,
                            isSelected = isSelected,
                            onClick = { onShowSubtitles(track) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            TvButton(
                onClick = onClose,
                modifier = if (closeButtonFocusRequester != null) {
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(closeButtonFocusRequester)
                } else {
                    Modifier.fillMaxWidth()
                },
            ) {
                TvText(stringResource(id = R.string.close))
            }
        }
    }
}

@Composable
fun TvTabButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.height(40.dp).padding(horizontal = 4.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvMaterialTheme.colorScheme.primary else Color.Transparent,
            focusedContainerColor = if (selected) TvMaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f)
        ),
        shape = ClickableSurfaceDefaults.shape(TvMaterialTheme.shapes.medium)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            TvText(text = text, style = TvMaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
fun TvTrackItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.25f)
        ),
        shape = ClickableSurfaceDefaults.shape(TvMaterialTheme.shapes.small)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TvText(text = title, style = TvMaterialTheme.typography.bodyLarge, color = Color.White)
            if (isSelected) {
                TvIcon(Icons.Default.Check, null, tint = TvMaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
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
                        contentType = { "audio_track_option" },
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
        subtitleAppearance = SubtitleAppearancePreferences.DEFAULT,
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
