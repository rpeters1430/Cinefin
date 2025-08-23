package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.MotionTokens

/**
 * Material 3 Expressive Floating Toolbar
 * Perfect for media player controls and contextual actions
 */
@Composable
fun ExpressiveFloatingToolbar(
    isVisible: Boolean,
    onPlayClick: () -> Unit,
    onQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCastClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryAction: ToolbarAction = ToolbarAction.PLAY
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it }
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it }
        ) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(28.dp)
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary action (larger FAB)
                when (primaryAction) {
                    ToolbarAction.PLAY -> {
                        FloatingActionButton(
                            onClick = onPlayClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    ToolbarAction.DOWNLOAD -> {
                        FloatingActionButton(
                            onClick = onDownloadClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    ToolbarAction.FAVORITE -> {
                        FloatingActionButton(
                            onClick = onFavoriteClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Secondary actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveToolbarButton(
                        icon = Icons.Default.Queue,
                        contentDescription = "Add to Queue",
                        onClick = onQueueClick
                    )

                    ExpressiveToolbarButton(
                        icon = Icons.Default.Cast,
                        contentDescription = "Cast",
                        onClick = onCastClick
                    )

                    ExpressiveToolbarButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share",
                        onClick = onShareClick
                    )

                    ExpressiveToolbarButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        onClick = onMoreClick
                    )
                }
            }
        }
    }
}

/**
 * Compact floating toolbar for minimal contexts
 */
@Composable
fun ExpressiveCompactToolbar(
    isVisible: Boolean,
    actions: List<ToolbarActionItem>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it }
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it }
        ) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEach { action ->
                    ExpressiveToolbarButton(
                        icon = action.icon,
                        contentDescription = action.contentDescription,
                        onClick = action.onClick,
                        isActive = action.isActive
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.1f else 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "toolbar_button_scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Video player expressive toolbar
 */
@Composable
fun ExpressiveVideoToolbar(
    isVisible: Boolean,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onCastClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it }
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it }
        ) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 0f,
                        endY = 200f
                    )
                )
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip Previous
                    SmallFloatingActionButton(
                        onClick = onSkipPreviousClick,
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, // Replace with skip previous
                            contentDescription = "Skip Previous"
                        )
                    }

                    // Play/Pause (larger)
                    FloatingActionButton(
                        onClick = onPlayPauseClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Default.PlayArrow // Replace with pause icon
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Skip Next
                    SmallFloatingActionButton(
                        onClick = onSkipNextClick,
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, // Replace with skip next
                            contentDescription = "Skip Next"
                        )
                    }
                }
            }
        }
    }
}

enum class ToolbarAction {
    PLAY, DOWNLOAD, FAVORITE
}

data class ToolbarActionItem(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val isActive: Boolean = false
)