package com.rpeters.jellyfin.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.SubtitleAppearancePreferencesViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class VideoPlayerActivity : FragmentActivity() {

    private val playerViewModel: VideoPlayerViewModel by viewModels()
    private val subtitlePreferencesViewModel: SubtitleAppearancePreferencesViewModel by viewModels()
    private var isInPipMode = false
    private var currentItemName: String = ""
    private var pipSourceRect: Rect? = null
    private var isHdrModeEnabled = false

    @Inject
    lateinit var deviceCapabilities: DeviceCapabilities

    // Receiver for PiP control actions
    private val playerCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(PipActionReceiver.EXTRA_COMMAND)) {
                PipActionReceiver.COMMAND_PLAY_PAUSE -> playerViewModel.togglePlayPause()
                PipActionReceiver.COMMAND_SKIP_FORWARD -> {
                    val playerState = playerViewModel.playerState.value
                    val newPosition = (playerState.currentPosition + 30_000)
                        .coerceAtMost(playerState.duration)
                    playerViewModel.seekTo(newPosition)
                }
                PipActionReceiver.COMMAND_SKIP_BACKWARD -> {
                    val playerState = playerViewModel.playerState.value
                    val newPosition = (playerState.currentPosition - 30_000)
                        .coerceAtLeast(0L)
                    playerViewModel.seekTo(newPosition)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_ITEM_NAME = "extra_item_name"
        private const val EXTRA_START_POSITION = "extra_start_position"

        @JvmStatic
        @VisibleForTesting
        internal fun shouldPausePlayback(
            isInPictureInPictureMode: Boolean,
            isFinishing: Boolean,
            isChangingConfigurations: Boolean,
        ): Boolean {
            return !isInPictureInPictureMode && (isFinishing || !isChangingConfigurations)
        }

        @JvmStatic
        @VisibleForTesting
        internal fun shouldAutoEnterPip(
            sdkInt: Int,
            isPipSupported: Boolean,
            isPlaying: Boolean,
        ): Boolean {
            return sdkInt >= Build.VERSION_CODES.O &&
                sdkInt < Build.VERSION_CODES.S &&
                isPipSupported &&
                isPlaying
        }

        fun createIntent(
            context: Context,
            itemId: String,
            itemName: String,
            startPosition: Long = 0L,
        ): Intent {
            return Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_ITEM_NAME, itemName)
                putExtra(EXTRA_START_POSITION, startPosition)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Set up full screen and landscape orientation
            setupFullScreenMode()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            // Keep screen on during playback
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Extract intent data
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
            if (itemId.isNullOrEmpty()) {
                android.util.Log.e("VideoPlayerActivity", "Missing item ID")
                finish()
                return
            }
            val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: ""
            val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)

            // Store item name for PiP title
            currentItemName = itemName

            // Register receiver for PiP control commands
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    playerCommandReceiver,
                    IntentFilter(PipActionReceiver.ACTION_PLAYER_COMMAND),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(
                    playerCommandReceiver,
                    IntentFilter(PipActionReceiver.ACTION_PLAYER_COMMAND),
                )
            }

            // Initialize player with error handling
            lifecycleScope.launch {
                try {
                    playerViewModel.initializePlayer(itemId, itemName, startPosition)
                } catch (e: CancellationException) {
                    throw e
                }
            }

            lifecycleScope.launch {
                playerViewModel.playerState.collect {
                    if (isInPipMode) {
                        updatePipParams()
                    }
                    updateHdrMode(it.isHdrContent)
                    // Allow portrait orientation when casting since video isn't on phone
                    updateOrientation(it.isCastConnected)
                }
            }

            setContent {
                JellyfinAndroidTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val playerState by playerViewModel.playerState.collectAsState()
                        val subtitleAppearance by subtitlePreferencesViewModel.preferences.collectAsStateWithLifecycle()

                        // Auto-finish when video ends without next episode
                        androidx.compose.runtime.LaunchedEffect(playerState.hasEnded, playerState.nextEpisode) {
                            if (playerState.hasEnded && playerState.nextEpisode == null && !playerState.showNextEpisodeCountdown) {
                                // Wait a brief moment before finishing to allow smooth transition
                                kotlinx.coroutines.delay(500)
                                finish()
                            }
                        }

                        VideoPlayerScreen(
                            playerState = playerState,
                            subtitleAppearance = subtitleAppearance,
                            onPlayPause = playerViewModel::togglePlayPause,
                            onSeek = playerViewModel::seekTo,
                            onQualityChange = playerViewModel::changeQuality,
                            onPlaybackSpeedChange = playerViewModel::setPlaybackSpeed,
                            onAspectRatioChange = playerViewModel::changeAspectRatio,
                            onCastClick = playerViewModel::handleCastButtonClick,
                            onCastPause = playerViewModel::pauseCastPlayback,
                            onCastResume = playerViewModel::resumeCastPlayback,
                            onCastStop = playerViewModel::stopCastPlayback,
                            onCastSeek = playerViewModel::seekCastPlayback,
                            onCastVolumeChange = playerViewModel::setCastVolume,
                            onSubtitlesClick = playerViewModel::showSubtitleDialog,
                            onPictureInPictureClick = ::enterPictureInPictureModeCustom,
                            onOrientationToggle = ::toggleOrientation,
                            onAudioTrackSelect = playerViewModel::selectAudioTrack,
                            onSubtitleTrackSelect = playerViewModel::selectSubtitleTrack,
                            onSubtitleDialogDismiss = playerViewModel::hideSubtitleDialog,
                            onCastDeviceSelect = playerViewModel::selectCastDevice,
                            onCastDialogDismiss = playerViewModel::hideCastDialog,
                            onErrorDismiss = playerViewModel::clearError,
                            onClose = { finish() },
                            onPlayNextEpisode = playerViewModel::playNextEpisode,
                            onCancelNextEpisode = playerViewModel::cancelNextEpisodeCountdown,
                            onPlayerViewBoundsChanged = { pipSourceRect = it },
                            exoPlayer = playerViewModel.exoPlayer,
                            supportsPip = isPipSupported(),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerActivity", "Critical error in onCreate", e)
            finish()
        }
    }

    private fun isPipSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val playerState = playerViewModel.playerState.value
        if (playerState.showCastDialog) {
            // Close cast dialog instead of navigating back
            playerViewModel.hideCastDialog()
            return
        }

        // Use NonCancellable to ensure stop playback is reported before finishing
        lifecycleScope.launch {
            withContext(NonCancellable) {
                playerViewModel.releasePlayer()
            }
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        // Don't auto-start playback in onResume - let the user control this
        // playerViewModel.startPlayback()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            return
        }
        // Only pause if we're actually leaving the activity (not just transitioning)
        if (shouldPausePlayback(isInPictureInPictureMode, isFinishing, isChangingConfigurations)) {
            playerViewModel.pausePlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateHdrMode(false)
        try {
            unregisterReceiver(playerCommandReceiver)
        } catch (e: Exception) {
            SecureLogger.w("VideoPlayerActivity", "Error unregistering receiver: ${e.message}")
        }

        // Use NonCancellable to ensure stop playback is reported before activity is destroyed
        lifecycleScope.launch {
            withContext(NonCancellable) {
                playerViewModel.releasePlayer()
            }
        }
    }

    private fun setupFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun updateHdrMode(isHdrContent: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val shouldEnable = isHdrContent && deviceCapabilities.supportsHdrDisplay()
        if (shouldEnable == isHdrModeEnabled) return
        window.setColorMode(
            if (shouldEnable) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT,
        )
        isHdrModeEnabled = shouldEnable
        SecureLogger.d("VideoPlayerActivity", "HDR mode ${if (shouldEnable) "enabled" else "disabled"}")
    }

    /**
     * Update screen orientation based on cast state.
     * When casting, allow portrait orientation since video is on Cast device.
     * When not casting, lock to landscape for optimal viewing.
     */
    private fun updateOrientation(isCasting: Boolean) {
        requestedOrientation = if (isCasting) {
            // Allow sensor-based rotation when casting
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            // Lock to landscape when playing locally
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun enterPictureInPictureModeCustom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )
        ) {
            val playerState = playerViewModel.playerState.value

            // Calculate aspect ratio from video dimensions, fallback to 16:9
            val aspectRatio = if (playerState.videoWidth > 0 && playerState.videoHeight > 0) {
                Rational(playerState.videoWidth, playerState.videoHeight)
            } else {
                Rational(16, 9)
            }

            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)

                // Add custom actions for Android 8.0+
                val actions = buildPipActions(playerState.isPlaying)
                builder.setActions(actions)
                pipSourceRect?.let { builder.setSourceRectHint(it) }

                // Add title for Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder
                        .setTitle(currentItemName)
                        .setSubtitle(formatDuration(playerState.currentPosition) + " / " + formatDuration(playerState.duration))
                        .setAutoEnterEnabled(true) // Auto-enter when user presses home
                        .setSeamlessResizeEnabled(true) // Smooth resize transitions
                }

                builder.build()
            } else {
                return // PiP not supported
            }

            try {
                enterPictureInPictureMode(params)
                SecureLogger.d("VideoPlayerActivity", "Entered PiP mode successfully")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Build custom remote actions for PiP window controls
     */
    private fun buildPipActions(isPlaying: Boolean): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        val actions = mutableListOf<RemoteAction>()

        // Skip Backward action
        val skipBackwardIntent = PendingIntent.getBroadcast(
            this,
            PipActionReceiver.REQUEST_SKIP_BACKWARD,
            Intent(PipActionReceiver.ACTION_SKIP_BACKWARD).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_replay_30),
                "Skip Backward",
                "Skip backward 30 seconds",
                skipBackwardIntent,
            ),
        )

        // Play/Pause action
        val playPauseIcon = if (isPlaying) {
            Icon.createWithResource(this, R.drawable.ic_pause)
        } else {
            Icon.createWithResource(this, R.drawable.ic_play_arrow)
        }
        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            PipActionReceiver.REQUEST_PLAY_PAUSE,
            Intent(PipActionReceiver.ACTION_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        actions.add(
            RemoteAction(
                playPauseIcon,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) "Pause playback" else "Resume playback",
                playPauseIntent,
            ),
        )

        // Skip Forward action
        val skipForwardIntent = PendingIntent.getBroadcast(
            this,
            PipActionReceiver.REQUEST_SKIP_FORWARD,
            Intent(PipActionReceiver.ACTION_SKIP_FORWARD).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_forward_30),
                "Skip Forward",
                "Skip forward 30 seconds",
                skipForwardIntent,
            ),
        )

        return actions
    }

    /**
     * Format duration in milliseconds to MM:SS format
     */
    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun toggleOrientation() {
        requestedOrientation = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * Automatically enter PiP mode when user presses home button or switches apps
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldAutoEnterPip(
                sdkInt = Build.VERSION.SDK_INT,
                isPipSupported = isPipSupported(),
                isPlaying = playerViewModel.playerState.value.isPlaying,
            )
        ) {
            enterPictureInPictureModeCustom()
        }
    }

    /**
     * Track PiP mode changes and update UI accordingly
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        isInPipMode = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            // Entered PiP mode - hide UI controls (handled by Compose UI state)
            SecureLogger.d("VideoPlayerActivity", "Entered PiP mode")
            updatePipParams()
        } else {
            // Exited PiP mode - restore full screen
            SecureLogger.d("VideoPlayerActivity", "Exited PiP mode")
            setupFullScreenMode()
        }
    }

    /**
     * Update PiP params dynamically (for play/pause button updates)
     */
    fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPipMode) {
            try {
                val playerState = playerViewModel.playerState.value
                val actions = buildPipActions(playerState.isPlaying)

                val params = PictureInPictureParams.Builder()
                    .setActions(actions)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setSubtitle(
                        formatDuration(playerState.currentPosition) + " / " +
                            formatDuration(playerState.duration),
                    )
                }
                pipSourceRect?.let { params.setSourceRectHint(it) }

                setPictureInPictureParams(params.build())
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}
