package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.MotionTokens

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenuItem
import com.rpeters.jellyfin.OptInAppExperimentalApis

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@OptInAppExperimentalApis
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
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
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
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isExpanded) "Close menu" else "Open menu",
                    )
                }
            }
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    onPlayClick()
                    isExpanded = false
                },
                text = { Text("Play Now") },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Play Now") }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onQueueClick()
                    isExpanded = false
                },
                text = { Text("Add to Queue") },
                icon = { Icon(Icons.Default.Queue, contentDescription = "Add to Queue") }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onDownloadClick()
                    isExpanded = false
                },
                text = { Text("Download") },
                icon = { Icon(Icons.Default.Download, contentDescription = "Download") }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    onFavoriteClick()
                    isExpanded = false
                },
                text = { Text("Add to Favorites") },
                icon = { Icon(Icons.Default.Favorite, contentDescription = "Add to Favorites") }
            )
        }
    }
}

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
