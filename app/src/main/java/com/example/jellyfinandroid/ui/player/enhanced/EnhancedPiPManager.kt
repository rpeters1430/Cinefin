package com.example.jellyfinandroid.ui.player.enhanced

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class PiPConfiguration(
    val aspectRatio: Rational = Rational(16, 9),
    val enableAutoEnterOnUserLeave: Boolean = true,
    val enableSeekBar: Boolean = true,
    val showTitle: Boolean = true,
    val enablePlayPause: Boolean = true,
    val enableSkipControls: Boolean = true,
    val enableCloseButton: Boolean = true,
    val customActions: List<PiPAction> = emptyList(),
    val minAspectRatio: Rational = Rational(1, 2), // 1:2 (portrait)
    val maxAspectRatio: Rational = Rational(2, 1), // 2:1 (wide)
)

data class PiPAction(
    val icon: Int, // Resource ID for the icon
    val title: String,
    val requestCode: Int,
    val intent: Intent,
)

enum class PiPState {
    DISABLED,
    AVAILABLE,
    ENTERING,
    ACTIVE,
    EXITING,
}

data class PiPVideoState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val title: String = "",
    val subtitle: String = "",
    val canSeek: Boolean = true,
    val canSkip: Boolean = true,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

class EnhancedPiPManager(private val activity: ComponentActivity) {

    companion object {
        const val ACTION_MEDIA_CONTROL = "media_control"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val CONTROL_PLAY = "play"
        const val CONTROL_PAUSE = "pause"
        const val CONTROL_NEXT = "next"
        const val CONTROL_PREVIOUS = "previous"
        const val CONTROL_CLOSE = "close"
        const val CONTROL_SEEK_FORWARD = "seek_forward"
        const val CONTROL_SEEK_BACKWARD = "seek_backward"
    }

    private var pipReceiver: BroadcastReceiver? = null
    private var currentConfiguration: PiPConfiguration = PiPConfiguration()

    private var onPlayPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onSeekForward: (() -> Unit)? = null
    private var onSeekBackward: (() -> Unit)? = null
    private var onClose: (() -> Unit)? = null

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPictureInPictureMode(
        configuration: PiPConfiguration = PiPConfiguration(),
        videoState: PiPVideoState = PiPVideoState(),
        onPlayPause: () -> Unit = {},
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        onSeekForward: () -> Unit = {},
        onSeekBackward: () -> Unit = {},
        onClose: () -> Unit = {},
    ): Boolean {
        if (!isPiPSupported()) return false

        this.currentConfiguration = configuration
        this.onPlayPause = onPlayPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onSeekForward = onSeekForward
        this.onSeekBackward = onSeekBackward
        this.onClose = onClose

        registerPiPReceiver()

        val params = createPiPParams(configuration, videoState)
        return activity.enterPictureInPictureMode(params)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updatePictureInPictureParams(
        configuration: PiPConfiguration? = null,
        videoState: PiPVideoState,
    ) {
        if (!activity.isInPictureInPictureMode) return

        val config = configuration ?: currentConfiguration
        val params = createPiPParams(config, videoState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.setPictureInPictureParams(params)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPiPParams(
        configuration: PiPConfiguration,
        videoState: PiPVideoState,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(configuration.aspectRatio)

        // Add custom actions
        val actions = mutableListOf<android.app.RemoteAction>()

        // Previous button
        if (configuration.enableSkipControls && videoState.hasPrevious) {
            actions.add(
                createRemoteAction(
                    iconRes = android.R.drawable.ic_media_previous,
                    title = "Previous",
                    description = "Previous episode",
                    requestCode = 1,
                    controlType = CONTROL_PREVIOUS,
                ),
            )
        }

        // Seek backward button
        actions.add(
            createRemoteAction(
                iconRes = android.R.drawable.ic_media_rew,
                title = "Rewind",
                description = "Rewind 10 seconds",
                requestCode = 2,
                controlType = CONTROL_SEEK_BACKWARD,
            ),
        )

        // Play/Pause button
        if (configuration.enablePlayPause) {
            actions.add(
                createRemoteAction(
                    iconRes = if (videoState.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    title = if (videoState.isPlaying) "Pause" else "Play",
                    description = if (videoState.isPlaying) "Pause video" else "Play video",
                    requestCode = 3,
                    controlType = if (videoState.isPlaying) CONTROL_PAUSE else CONTROL_PLAY,
                ),
            )
        }

        // Seek forward button
        actions.add(
            createRemoteAction(
                iconRes = android.R.drawable.ic_media_ff,
                title = "Fast Forward",
                description = "Fast forward 30 seconds",
                requestCode = 4,
                controlType = CONTROL_SEEK_FORWARD,
            ),
        )

        // Next button
        if (configuration.enableSkipControls && videoState.hasNext) {
            actions.add(
                createRemoteAction(
                    iconRes = android.R.drawable.ic_media_next,
                    title = "Next",
                    description = "Next episode",
                    requestCode = 5,
                    controlType = CONTROL_NEXT,
                ),
            )
        }

        // Close button
        if (configuration.enableCloseButton) {
            actions.add(
                createRemoteAction(
                    iconRes = android.R.drawable.ic_menu_close_clear_cancel,
                    title = "Close",
                    description = "Exit PiP mode",
                    requestCode = 6,
                    controlType = CONTROL_CLOSE,
                ),
            )
        }

        // Add custom actions
        configuration.customActions.forEach { customAction ->
            actions.add(
                android.app.RemoteAction(
                    android.graphics.drawable.Icon.createWithResource(activity, customAction.icon),
                    customAction.title,
                    customAction.title,
                    PendingIntent.getBroadcast(
                        activity,
                        customAction.requestCode,
                        customAction.intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ),
            )
        }

        builder.setActions(actions)

        // Set source rect hint for smooth transition (if available)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(configuration.enableAutoEnterOnUserLeave)
        }

        return builder.build()
    }

    private fun createRemoteAction(
        iconRes: Int,
        title: String,
        description: String,
        requestCode: Int,
        controlType: String,
    ): android.app.RemoteAction {
        val intent = Intent(ACTION_MEDIA_CONTROL).apply {
            putExtra(EXTRA_CONTROL_TYPE, controlType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(activity, iconRes),
            title,
            description,
            pendingIntent,
        )
    }

    private fun registerPiPReceiver() {
        unregisterPiPReceiver() // Unregister any existing receiver

        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_MEDIA_CONTROL) {
                    val controlType = intent.getStringExtra(EXTRA_CONTROL_TYPE)
                    when (controlType) {
                        CONTROL_PLAY, CONTROL_PAUSE -> onPlayPause?.invoke()
                        CONTROL_NEXT -> onNext?.invoke()
                        CONTROL_PREVIOUS -> onPrevious?.invoke()
                        CONTROL_SEEK_FORWARD -> onSeekForward?.invoke()
                        CONTROL_SEEK_BACKWARD -> onSeekBackward?.invoke()
                        CONTROL_CLOSE -> onClose?.invoke()
                    }
                }
            }
        }

        val intentFilter = IntentFilter(ACTION_MEDIA_CONTROL)
        activity.registerReceiver(pipReceiver, intentFilter)
    }

    private fun unregisterPiPReceiver() {
        pipReceiver?.let { receiver ->
            try {
                activity.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                // Receiver was not registered
            }
            pipReceiver = null
        }
    }

    fun isPiPSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun isInPictureInPictureMode(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.isInPictureInPictureMode
    }

    fun onDestroy() {
        unregisterPiPReceiver()
    }
}

@Composable
fun FloatingVideoPlayer(
    videoState: PiPVideoState,
    isVisible: Boolean,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + scaleOut() + slideOutVertically(targetOffsetY = { it }),
    ) {
        DraggableFloatingPlayer(
            videoState = videoState,
            onPlayPause = onPlayPause,
            onSeekBackward = onSeekBackward,
            onSeekForward = onSeekForward,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onEnterFullscreen = onEnterFullscreen,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

@Composable
private fun DraggableFloatingPlayer(
    videoState: PiPVideoState,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isExpanded by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var playerWidth by remember { mutableStateOf(0) }
    var playerHeight by remember { mutableStateOf(0) }

    // Auto-hide controls after delay
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        showControls = true
                    },
                ) { change, dragAmount ->
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    change.consume()
                }
            }
            .onGloballyPositioned { coordinates ->
                playerWidth = coordinates.size.width
                playerHeight = coordinates.size.height
            },
    ) {
        FloatingPlayerContent(
            videoState = videoState,
            isExpanded = isExpanded,
            showControls = showControls,
            onExpandToggle = {
                isExpanded = !isExpanded
                showControls = true
            },
            onPlayPause = onPlayPause,
            onSeekBackward = onSeekBackward,
            onSeekForward = onSeekForward,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onEnterFullscreen = onEnterFullscreen,
            onClose = onClose,
            onTap = { showControls = !showControls },
        )
    }
}

@Composable
private fun FloatingPlayerContent(
    videoState: PiPVideoState,
    isExpanded: Boolean,
    showControls: Boolean,
    onExpandToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedWidth by animateFloatAsState(
        targetValue = if (isExpanded) 300f else 160f,
        animationSpec = tween(300),
        label = "width",
    )

    val animatedHeight by animateFloatAsState(
        targetValue = if (isExpanded) 200f else 90f,
        animationSpec = tween(300),
        label = "height",
    )

    Card(
        modifier = modifier
            .width(animatedWidth.dp)
            .height(animatedHeight.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onTap() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.9f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Video preview area (would contain actual video surface in real implementation)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "VIDEO",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Progress bar
            if (videoState.duration > 0) {
                LinearProgressIndicator(
                    progress = { (videoState.currentPosition.toFloat() / videoState.duration.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            // Controls overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                ) {
                    // Top controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Expand/Collapse button
                        FloatingActionButton(
                            onClick = onExpandToggle,
                            modifier = Modifier.size(32.dp),
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.PictureInPictureAlt else Icons.Default.OpenInFull,
                                contentDescription = if (isExpanded) "Minimize" else "Expand",
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        Row {
                            // Fullscreen button
                            FloatingActionButton(
                                onClick = onEnterFullscreen,
                                modifier = Modifier.size(32.dp),
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Close button
                            FloatingActionButton(
                                onClick = onClose,
                                modifier = Modifier.size(32.dp),
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    // Center play/pause button
                    FloatingActionButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(if (isExpanded) 56.dp else 40.dp)
                            .align(Alignment.Center),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = if (videoState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (videoState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(if (isExpanded) 32.dp else 24.dp),
                        )
                    }

                    // Bottom controls (only when expanded)
                    if (isExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Skip previous
                            if (videoState.hasPrevious) {
                                IconButton(
                                    onClick = onSkipPrevious,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.6f),
                                            CircleShape,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            // Seek backward
                            IconButton(
                                onClick = onSeekBackward,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        CircleShape,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "Rewind",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Seek forward
                            IconButton(
                                onClick = onSeekForward,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        CircleShape,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Fast Forward",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Skip next
                            if (videoState.hasNext) {
                                IconButton(
                                    onClick = onSkipNext,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.6f),
                                            CircleShape,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Title overlay (when expanded)
                    if (isExpanded && videoState.title.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = videoState.title,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (videoState.subtitle.isNotEmpty()) {
                                Text(
                                    text = videoState.subtitle,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PiPLifecycleHandler(
    pipManager: EnhancedPiPManager,
    videoState: PiPVideoState,
    configuration: PiPConfiguration = PiPConfiguration(),
    onEnterPiP: () -> Unit = {},
    onExitPiP: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (configuration.enableAutoEnterOnUserLeave && pipManager.isPiPSupported()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            pipManager.enterPictureInPictureMode(
                                configuration = configuration,
                                videoState = videoState,
                                onPlayPause = onPlayPause,
                                onNext = onNext,
                                onPrevious = onPrevious,
                                onSeekForward = onSeekForward,
                                onSeekBackward = onSeekBackward,
                                onClose = onClose,
                            )
                        }
                        onEnterPiP()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (pipManager.isInPictureInPictureMode()) {
                        onExitPiP()
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    pipManager.onDestroy()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update PiP params when video state changes
    LaunchedEffect(videoState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pipManager.updatePictureInPictureParams(
                configuration = configuration,
                videoState = videoState,
            )
        }
    }
}
