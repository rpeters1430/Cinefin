package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.theme.MusicGreen

@Composable
internal fun TVShowsTopBar(
    viewMode: TVShowViewMode,
    onViewModeChange: (TVShowViewMode) -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onSortChange: (TVShowSortOrder) -> Unit,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        title = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResource(id = R.string.tv_shows),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
        actions = {
            // View mode toggle
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                IconButton(
                    onClick = {
                        onViewModeChange(
                            when (viewMode) {
                                TVShowViewMode.GRID -> TVShowViewMode.LIST
                                TVShowViewMode.LIST -> TVShowViewMode.CAROUSEL
                                TVShowViewMode.CAROUSEL -> TVShowViewMode.GRID
                            },
                        )
                    },
                ) {
                    Icon(
                        imageVector = when (viewMode) {
                            TVShowViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                            TVShowViewMode.LIST -> Icons.Default.ViewCarousel
                            TVShowViewMode.CAROUSEL -> Icons.Default.GridView
                        },
                        contentDescription = "Toggle view mode",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Sort menu
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Box {
                    IconButton(onClick = { onShowSortMenuChange(true) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { onShowSortMenuChange(false) },
                    ) {
                        TVShowSortOrder.getAllSortOrders().forEach { order ->
                            DropdownMenuItem(
                                text = { Text(stringResource(id = order.displayNameResId)) },
                                onClick = {
                                    onSortChange(order)
                                    onShowSortMenuChange(false)
                                },
                            )
                        }
                    }
                }
            }

            // Refresh button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(end = 8.dp, start = 4.dp),
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MusicGreen,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}
