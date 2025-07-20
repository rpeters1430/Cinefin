package com.example.jellyfinandroid.ui.player

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
import com.example.jellyfinandroid.ui.theme.JellyfinAndroidTheme
import dagger.hilt.android.AndroidEntryPoint

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {
    
    private val playerViewModel: VideoPlayerViewModel by viewModels()
    
    companion object {
        private const val EXTRA_ITEM_ID = "extra_item_id"
        private const val EXTRA_ITEM_NAME = "extra_item_name"
        private const val EXTRA_STREAM_URL = "extra_stream_url"
        private const val EXTRA_START_POSITION = "extra_start_position"
        
        fun createIntent(
            context: Context,
            itemId: String,
            itemName: String,
            streamUrl: String,
            startPosition: Long = 0L
        ): Intent {
            return Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_ITEM_NAME, itemName)
                putExtra(EXTRA_STREAM_URL, streamUrl)
                putExtra(EXTRA_START_POSITION, startPosition)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up full screen and landscape orientation
        setupFullScreenMode()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Extract intent data
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: ""
        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: ""
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
        val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        
        // Initialize player
        playerViewModel.initializePlayer(itemId, itemName, streamUrl, startPosition)
        
        setContent {
            JellyfinAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val playerState by playerViewModel.playerState.collectAsState()
                    
                    VideoPlayerScreen(
                        playerState = playerState,
                        onPlayPause = playerViewModel::togglePlayPause,
                        onSeek = playerViewModel::seekTo,
                        onQualityChange = playerViewModel::changeQuality,
                        onCastClick = playerViewModel::startCasting,
                        onPictureInPictureClick = ::enterPictureInPictureModeCustom,
                        onBackClick = ::finish,
                        onOrientationToggle = ::toggleOrientation,
                        exoPlayer = playerViewModel.exoPlayer
                    )
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        playerViewModel.startPlayback()
    }
    
    override fun onStop() {
        super.onStop()
        playerViewModel.pausePlayback()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.releasePlayer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureModeCustom()
        }
    }
    
    private fun setupFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    private fun enterPictureInPictureModeCustom() {
        val aspectRatio = Rational(16, 9)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .apply {
                // setAutoEnterEnabled requires API 31+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                }
            }
            .build()
        enterPictureInPictureMode(params)
    }
    
    private fun toggleOrientation() {
        requestedOrientation = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}