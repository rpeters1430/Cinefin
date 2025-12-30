package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Menu Components
 * Wrapper components for the new expressive menu items introduced in Material 3 1.5.0-alpha09+
 *
 * Features:
 * - Toggleable menu items with switch
 * - Selectable menu items with checkboxes
 * - Menu groups with dividers
 * - Enhanced expressive styling
 */

/**
 * Expressive dropdown menu for media item actions
 */
@Composable
fun ExpressiveMediaActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPlayClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = DpOffset(0.dp, 4.dp),
    ) {
        // Play action
        DropdownMenuItem(
            text = {
                Text(
                    "Play",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = {
                onPlayClick()
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            colors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.onSurface,
                leadingIconColor = MaterialTheme.colorScheme.primary,
            ),
        )

        // Add to queue action
        DropdownMenuItem(
            text = {
                Text(
                    "Add to Queue",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = {
                onAddToQueueClick()
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Queue,
                    contentDescription = "Add to Queue",
                )
            },
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Download action
        DropdownMenuItem(
            text = {
                Text(
                    "Download",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = {
                onDownloadClick()
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                )
            },
        )

        // Favorite action (toggleable)
        ExpressiveToggleableMenuItem(
            text = "Favorite",
            checked = isFavorite,
            onCheckedChange = { checked ->
                onFavoriteClick()
                if (!checked) {
                    onDismissRequest()
                }
            },
            icon = Icons.Default.Favorite,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Share action
        DropdownMenuItem(
            text = {
                Text(
                    "Share",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = {
                onShareClick()
                onDismissRequest()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                )
            },
        )
    }
}

/**
 * Expressive toggleable menu item with switch
 * New component type introduced in Material 3 1.5.0-alpha09
 */
@Composable
fun ExpressiveToggleableMenuItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            }
        },
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Expressive selectable menu item with checkbox
 * New component type introduced in Material 3 1.5.0-alpha09
 */
@Composable
fun ExpressiveSelectableMenuItem(
    text: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        onClick = { onSelectedChange(!selected) },
        modifier = modifier,
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Expressive menu group with header
 * Organizes related menu items with visual grouping
 */
@Composable
fun ExpressiveMenuGroup(
    title: String,
    items: List<ExpressiveMenuItemData>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Group header
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Group items
        items.forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                onClick = item.onClick,
                leadingIcon = item.icon?.let {
                    {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                        )
                    }
                },
                enabled = item.enabled,
            )
        }
    }
}

/**
 * Data class for menu items
 */
data class ExpressiveMenuItemData(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

/**
 * Example: Quality settings menu with selectable items
 */
@Composable
fun QualitySelectionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedQuality: String,
    onQualitySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val qualities = listOf("Auto", "4K", "1080p", "720p", "480p")

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Text(
            text = "Playback Quality",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        qualities.forEach { quality ->
            ExpressiveSelectableMenuItem(
                text = quality,
                selected = quality == selectedQuality,
                onSelectedChange = { selected ->
                    if (selected) {
                        onQualitySelected(quality)
                        onDismissRequest()
                    }
                },
            )
        }
    }
}
