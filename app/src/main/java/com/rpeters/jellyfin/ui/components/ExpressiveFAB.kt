package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.MotionTokens

/**
 * Material 3 Expressive FAB Menu component
 * Follows the new M3 Expressive FAB Menu pattern from 2024-2025
 */
@Composable
fun ExpressiveFABMenu(
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    isVisible: Boolean = true,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = MotionTokens.fabMenuExpand,
        label = "fab_rotation",
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(MotionTokens.fabMenuExpand) + fadeIn(),
        exit = scaleOut(MotionTokens.fabMenuCollapse) + fadeOut(),
        modifier = modifier,
    ) {
        Box {
            // FAB Menu Items
            AnimatedVisibility(
                visible = isExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                ) + fadeOut(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 72.dp),
                ) {
                    ExpressiveFABMenuItem(
                        icon = Icons.Default.PlayArrow,
                        label = "Play Now",
                        onClick = {
                            onPlayClick()
                            isExpanded = false
                        },
                    )

                    ExpressiveFABMenuItem(
                        icon = Icons.Default.Queue,
                        label = "Add to Queue",
                        onClick = {
                            onQueueClick()
                            isExpanded = false
                        },
                    )

                    ExpressiveFABMenuItem(
                        icon = Icons.Default.Download,
                        label = "Download",
                        onClick = {
                            onDownloadClick()
                            isExpanded = false
                        },
                    )

                    ExpressiveFABMenuItem(
                        icon = Icons.Default.Favorite,
                        label = "Add to Favorites",
                        onClick = {
                            onFavoriteClick()
                            isExpanded = false
                        },
                    )
                }
            }

            // Main FAB with toggle
            FloatingActionButton(
                onClick = { isExpanded = !isExpanded },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (isExpanded) "Close menu" else "Open menu",
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun ExpressiveFABMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Extended FAB with expressive styling for main actions
 */
@Composable
fun ExpressiveExtendedFAB(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    )
}
