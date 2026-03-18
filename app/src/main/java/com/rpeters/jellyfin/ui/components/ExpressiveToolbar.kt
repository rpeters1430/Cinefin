@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
    primaryAction: ToolbarAction = ToolbarAction.PLAY,
    showQueueAction: Boolean = true,
    showCastAction: Boolean = true,
    showShareAction: Boolean = true,
    showMoreAction: Boolean = true,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
        ) + fadeOut(),
        modifier = modifier,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.padding(16.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            floatingActionButton = {
                FloatingToolbarDefaults.StandardFloatingActionButton(
                    onClick = when (primaryAction) {
                        ToolbarAction.PLAY -> onPlayClick
                        ToolbarAction.DOWNLOAD -> onDownloadClick
                        ToolbarAction.FAVORITE -> onFavoriteClick
                    },
                ) {
                    Icon(
                        imageVector = when (primaryAction) {
                            ToolbarAction.PLAY -> Icons.Default.PlayArrow
                            ToolbarAction.DOWNLOAD -> Icons.Default.Download
                            ToolbarAction.FAVORITE -> Icons.Default.Favorite
                        },
                        contentDescription = when (primaryAction) {
                            ToolbarAction.PLAY -> "Play"
                            ToolbarAction.DOWNLOAD -> "Download"
                            ToolbarAction.FAVORITE -> "Favorite"
                        },
                    )
                }
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showQueueAction) {
                    ExpressiveToolbarButton(
                        icon = Icons.Default.Queue,
                        contentDescription = "Add to Queue",
                        onClick = onQueueClick,
                    )
                }

                if (showCastAction) {
                    ExpressiveToolbarButton(
                        icon = Icons.Default.Cast,
                        contentDescription = "Cast",
                        onClick = onCastClick,
                    )
                }

                if (showShareAction) {
                    ExpressiveToolbarButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Share",
                        onClick = onShareClick,
                    )
                }

                if (showMoreAction) {
                    ExpressiveToolbarButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        onClick = onMoreClick,
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
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
        ) + fadeOut(),
        modifier = modifier,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.padding(16.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        ) {
            actions.forEach { action ->
                ExpressiveToolbarButton(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    onClick = action.onClick,
                    isActive = action.isActive,
                )
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
    isActive: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(20.dp),
        )
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
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
        ) + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f),
                        ),
                        startY = 0f,
                        endY = 200f,
                    ),
                ),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(28.dp),
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Skip Previous
                    SmallFloatingActionButton(
                        onClick = onSkipPreviousClick,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, // Replace with skip previous
                            contentDescription = "Skip Previous",
                        )
                    }

                    // Play/Pause (larger)
                    FloatingActionButton(
                        onClick = onPlayPauseClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Default.PlayArrow // Replace with pause icon
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    // Skip Next
                    SmallFloatingActionButton(
                        onClick = onSkipNextClick,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, // Replace with skip next
                            contentDescription = "Skip Next",
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
    val isActive: Boolean = false,
)
