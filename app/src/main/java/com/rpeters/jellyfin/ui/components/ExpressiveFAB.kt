@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(MotionTokens.fabMenuExpand) + fadeIn(),
        exit = scaleOut(MotionTokens.fabMenuCollapse) + fadeOut(),
        modifier = modifier,
    ) {
        FloatingActionButtonMenu(
            expanded = isExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = isExpanded,
                    onCheckedChange = { isExpanded = it },
                ) {
                    Icon(
                        imageVector = if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isExpanded) "Close menu" else "Open menu",
                        modifier = Modifier.animateIcon(checkedProgress = { checkedProgress }),
                    )
                }
            },
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    onPlayClick()
                    isExpanded = false
                },
                text = { Text("Play Now") },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onQueueClick()
                    isExpanded = false
                },
                text = { Text("Add to Queue") },
                icon = { Icon(Icons.Default.Queue, contentDescription = null) },
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onDownloadClick()
                    isExpanded = false
                },
                text = { Text("Download") },
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onFavoriteClick()
                    isExpanded = false
                },
                text = { Text("Add to Favorites") },
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
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
