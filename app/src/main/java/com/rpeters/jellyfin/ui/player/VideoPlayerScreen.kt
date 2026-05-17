package com.rpeters.jellyfin.ui.player

import android.content.res.Configuration
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import com.rpeters.jellyfin.ui.player.components.SyncPlayDialog
import com.rpeters.jellyfin.ui.player.components.VideoPlayerOverlays
import com.rpeters.jellyfin.ui.player.components.toOverlayState
import com.rpeters.jellyfin.ui.viewmodel.SyncPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    subtitleAppearance: SubtitleAppearancePreferences,
    onPictureInPictureClick: () -> Unit,
    onOrientationToggle: () -> Unit,
    onPlayerViewBoundsChanged: (android.graphics.Rect) -> Unit = {},
    onClose: () -> Unit = {},
    supportsPip: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is VideoPlayerSideEffect.ClosePlayer -> onClose()
            is VideoPlayerSideEffect.ShowToast -> {
                // In a real app, you'd show a toast or snackbar
            }
        }
    }

    val activityRef = remember(context) { WeakReference(context as? android.app.Activity) }
    val isTvDevice = remember { com.rpeters.jellyfin.utils.DeviceTypeUtils.isTvDevice(context) }

    // Audio and Volume
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }

    // Gesture State
    var currentBrightness by remember(context) { mutableStateOf(activityRef.get()?.window?.attributes?.screenBrightness ?: -1f) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }

    // TV Hand-off
    if (isTvDevice) {
        val pipState = com.rpeters.jellyfin.ui.player.tv.rememberPictureInPictureState(supportsPip = supportsPip) {
            onPictureInPictureClick()
        }
        com.rpeters.jellyfin.ui.player.tv.TvVideoPlayerScreen(
            state = state,
            exoPlayer = viewModel.exoPlayer,
            subtitleAppearance = subtitleAppearance,
            pipState = pipState,
            onBack = { (context as? android.app.Activity)?.finish() },
            onPlayPause = { viewModel.onIntent(VideoPlayerIntent.TogglePlayPause) },
            onSeekForward = { viewModel.onIntent(VideoPlayerIntent.SeekTo((state.currentPosition + 30_000).coerceAtMost(state.duration))) },
            onSeekBackward = { viewModel.onIntent(VideoPlayerIntent.SeekTo((state.currentPosition - 30_000).coerceAtLeast(0L))) },
            onSeekTo = { viewModel.onIntent(VideoPlayerIntent.SeekTo(it)) },
            onSetPlaybackSpeed = { viewModel.onIntent(VideoPlayerIntent.SetPlaybackSpeed(it)) },
            onToggleMute = { viewModel.onIntent(VideoPlayerIntent.ToggleMute) },
            onChangeAspectRatio = { viewModel.onIntent(VideoPlayerIntent.ChangeAspectRatio(it)) },
            onShowAudio = { viewModel.onIntent(VideoPlayerIntent.SelectAudioTrack(it)) },
            onShowSubtitles = { viewModel.onIntent(VideoPlayerIntent.SelectSubtitleTrack(it)) },
            onPlayNextEpisode = { viewModel.onIntent(VideoPlayerIntent.PlayNextEpisode) },
            onDismissNextEpisodePrompt = { viewModel.onIntent(VideoPlayerIntent.DismissNextEpisodePrompt) },
            onCancelNextEpisodeCountdown = { viewModel.onIntent(VideoPlayerIntent.CancelNextEpisodeCountdown) },
            onErrorDismiss = { viewModel.onIntent(VideoPlayerIntent.ClearError) },
            modifier = modifier,
        )
        return
    }

    val playerColors = rememberVideoPlayerColors()

    val overlayState = state.toOverlayState()

    // Gesture feedback states
    var showSeekFeedback by remember { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf("") }
    var seekFeedbackIcon by remember { mutableStateOf(Icons.Default.FastForward) }
    var lastPlayerViewBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Coroutine scope for managing the gesture feedback dismiss timer.
    val feedbackScope = rememberCoroutineScope()
    val feedbackDismissJobRef = remember { java.util.concurrent.atomic.AtomicReference<Job?>(null) }

    val showFeedback: () -> Unit = remember(feedbackScope) {
        {
            showSeekFeedback = true
            feedbackDismissJobRef.getAndSet(null)?.cancel()
            feedbackDismissJobRef.set(
                feedbackScope.launch {
                    delay(1500)
                    showSeekFeedback = false
                },
            )
        }
    }

    // Errors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.onIntent(VideoPlayerIntent.ClearError)
        }
    }

    var showSyncPlayDialog by remember { mutableStateOf(false) }
    val syncPlayViewModel: SyncPlayViewModel = hiltViewModel()
    val syncPlayState by syncPlayViewModel.state.collectAsStateWithLifecycle()

    // Real-time position for gestures
    var currentPosMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(viewModel.exoPlayer, state.isPlaying, state.isLoading) {
        val player = viewModel.exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            currentPosMs = player.currentPosition
            delay(500)
        }
    }

    // Control visibility timers
    LaunchedEffect(state.isControlsVisible, state.isPlaying) {
        if (state.isControlsVisible && state.isPlaying) {
            delay(5000)
            viewModel.onIntent(VideoPlayerIntent.SetControlsVisible(false))
        }
    }
    LaunchedEffect(state.isPlaying, overlayState.showPrimaryLoadingUi) {
        if (!state.isPlaying || overlayState.showPrimaryLoadingUi) {
            viewModel.onIntent(VideoPlayerIntent.SetControlsVisible(true))
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(playerColors.background)
            .videoPlayerGestures(
                enableVerticalDragGestures = isLandscape,
                onTap = { isCenterTap ->
                    if (isCenterTap) {
                        viewModel.onIntent(VideoPlayerIntent.TogglePlayPause)
                        seekFeedbackIcon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
                        seekFeedbackText = if (state.isPlaying) "Pause" else "Play"
                        showFeedback()
                    } else {
                        viewModel.onIntent(VideoPlayerIntent.ToggleControls)
                    }
                },
                onDoubleTap = { isRightSide ->
                    val seekAmount = if (isRightSide) 10_000L else -10_000L
                    viewModel.onIntent(VideoPlayerIntent.SeekTo((currentPosMs + seekAmount).coerceIn(0L, state.duration)))
                    seekFeedbackIcon = if (isRightSide) Icons.Default.FastForward else Icons.Default.FastRewind
                    seekFeedbackText = if (isRightSide) "+10s" else "-10s"
                    showFeedback()
                    viewModel.onIntent(VideoPlayerIntent.SetControlsVisible(false))
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
                        seekFeedbackIcon = Icons.Default.Brightness6
                        seekFeedbackText = "${(next * 100).toInt()}%"
                        showFeedback()
                    } else {
                        val volumeChange = (deltaY / (screenHeight * 0.5f) * maxVolume).toInt()
                        val next = (currentVolume + volumeChange).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0)
                        currentVolume = next
                        seekFeedbackIcon = if (next == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                        seekFeedbackText = "${(next * 100 / maxVolume)}%"
                        showFeedback()
                    }
                },
            ),
    ) {
        if (state.isCastConnected) {
            CastRemoteScreen(
                playerState = state,
                onPauseCast = { viewModel.onIntent(VideoPlayerIntent.PauseCast) },
                onResumeCast = { viewModel.onIntent(VideoPlayerIntent.ResumeCast) },
                onStopCast = { viewModel.onIntent(VideoPlayerIntent.StopCast) },
                onSeekCast = { viewModel.onIntent(VideoPlayerIntent.SeekCast(it)) },
                onDisconnectCast = { viewModel.onIntent(VideoPlayerIntent.DisconnectCast) },
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
                    if (view.player != viewModel.exoPlayer) view.player = viewModel.exoPlayer
                    view.resizeMode = state.selectedAspectRatio.resizeMode
                    applySubtitleAppearance(view, subtitleAppearance)
                    configureHdrSupport(view, state.isHdrContent)
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

            VideoPlayerOverlays(
                state = state,
                overlayState = overlayState,
                feedbackVisible = showSeekFeedback,
                feedbackIcon = seekFeedbackIcon,
                feedbackText = seekFeedbackText,
                overlayScrim = playerColors.overlayScrim,
                overlayContent = playerColors.overlayContent,
                currentPosMs = currentPosMs,
                onIntent = viewModel::onIntent,
                onClose = onClose,
                onPictureInPictureClick = onPictureInPictureClick,
                onSyncPlayClick = { showSyncPlayDialog = true },
                supportsPip = supportsPip,
            )
        }

        if (state.showAudioDialog) {
            AudioTrackSelectionDialog(
                availableTracks = state.availableAudioTracks,
                onTrackSelect = { viewModel.onIntent(VideoPlayerIntent.SelectAudioTrack(it)) },
                onDismiss = { viewModel.onIntent(VideoPlayerIntent.HideAudioDialog) },
            )
        }

        if (state.showQualityDialog) {
            QualitySelectionDialog(
                availableQualities = state.availableQualities,
                selectedQuality = state.selectedQuality,
                onQualitySelect = { viewModel.onIntent(VideoPlayerIntent.ChangeQuality(it)) },
                onDismiss = { viewModel.onIntent(VideoPlayerIntent.HideQualityDialog) },
            )
        }

        if (state.showSubtitleDialog) {
            SubtitleTrackSelectionDialog(
                availableTracks = state.availableSubtitleTracks,
                selectedTrack = state.selectedSubtitleTrack,
                onTrackSelect = { viewModel.onIntent(VideoPlayerIntent.SelectSubtitleTrack(it)) },
                onDismiss = { viewModel.onIntent(VideoPlayerIntent.HideSubtitleDialog) },
            )
        }

        if (state.showCastDialog) {
            CastDeviceSelectionDialog(
                availableDevices = state.availableCastDevices,
                discoveryState = state.castDiscoveryState,
                onDeviceSelect = { viewModel.onIntent(VideoPlayerIntent.SelectCastDevice(it)) },
                onDismiss = { viewModel.onIntent(VideoPlayerIntent.HideCastDialog) },
            )
        }

        if (showSyncPlayDialog) {
            LaunchedEffect(Unit) {
                syncPlayViewModel.loadGroups()
            }

            SyncPlayDialog(
                state = syncPlayState,
                onDismissRequest = { showSyncPlayDialog = false },
                onCreateGroup = { name -> 
                    syncPlayViewModel.createGroup(name)
                },
                onJoinGroup = { id -> 
                    syncPlayViewModel.joinGroup(id)
                },
                onLeaveGroup = { 
                    syncPlayViewModel.leaveGroup()
                },
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private fun configureHdrSupport(playerView: PlayerView, isHdrContent: Boolean) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return

    try {
        val surfaceView = findSurfaceView(playerView) ?: return

        if (isHdrContent) {
            surfaceView.holder.setFormat(android.graphics.PixelFormat.RGBA_1010102)
            com.rpeters.jellyfin.utils.SecureLogger.d(
                "VideoPlayerScreen",
                "Configured SurfaceView for HDR content with RGBA_1010102 format",
            )
        } else {
            surfaceView.holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
        }
    } catch (e: Exception) {
        com.rpeters.jellyfin.utils.SecureLogger.w(
            "VideoPlayerScreen",
            "Failed to configure HDR support: ${e.message}",
        )
    }
}

private fun findSurfaceView(view: android.view.View): android.view.SurfaceView? {
    if (view is android.view.SurfaceView) {
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
