package com.rpeters.jellyfin.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import dagger.hilt.android.AndroidEntryPoint

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {

    private val playerViewModel: VideoPlayerViewModel by viewModels()

    companion object {
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_ITEM_NAME = "extra_item_name"
        private const val EXTRA_START_POSITION = "extra_start_position"

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

            // Initialize player with error handling
            try {
                playerViewModel.initializePlayer(itemId, itemName, startPosition)
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerActivity", "Failed to initialize player", e)
                finish()
                return
            }

            setContent {
                JellyfinAndroidTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val playerState by playerViewModel.playerState.collectAsState()

                        VideoPlayerScreen(
                            playerState = playerState,
                            onPlayPause = playerViewModel::togglePlayPause,
                            onSeek = playerViewModel::seekTo,
                            onQualityChange = playerViewModel::changeQuality,
                            onPlaybackSpeedChange = playerViewModel::setPlaybackSpeed,
                            onAspectRatioChange = playerViewModel::changeAspectRatio,
                            onCastClick = playerViewModel::showCastDialog,
                            onSubtitlesClick = playerViewModel::showSubtitleDialog,
                            onPictureInPictureClick = ::enterPictureInPictureModeCustom,
                            onOrientationToggle = ::toggleOrientation,
                            onAudioTrackSelect = playerViewModel::selectAudioTrack,
                            onSubtitleTrackSelect = playerViewModel::selectSubtitleTrack,
                            onSubtitleDialogDismiss = playerViewModel::hideSubtitleDialog,
                            onCastDeviceSelect = playerViewModel::selectCastDevice,
                            onCastDialogDismiss = playerViewModel::hideCastDialog,
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


    override fun onResume() {
        super.onResume()
        // Don't auto-start playback in onResume - let the user control this
        // playerViewModel.startPlayback()
    }

    override fun onPause() {
        super.onPause()
        // Only pause if we're actually leaving the activity (not just transitioning)
        if (isFinishing || !isChangingConfigurations) {
            playerViewModel.pausePlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.releasePlayer()
    }

    private fun setupFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun enterPictureInPictureModeCustom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE
            )
        ) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun toggleOrientation() {
        requestedOrientation = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}
