package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.data.models.MovieSortOrder
import com.rpeters.jellyfin.data.models.MovieViewMode

@OptInAppExperimentalApis
@Composable
internal fun MoviesTopBar(
    viewMode: MovieViewMode,
    onViewModeChange: (MovieViewMode) -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onSortChange: (MovieSortOrder) -> Unit,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        title = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "Movies",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
        actions = {
            // View mode segmented button
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                SegmentedButton(
                    selected = viewMode == MovieViewMode.GRID,
                    onClick = { onViewModeChange(MovieViewMode.GRID) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = viewMode == MovieViewMode.GRID) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Grid view",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                ) {
                    Text("Grid", style = MaterialTheme.typography.labelMedium)
                }
                SegmentedButton(
                    selected = viewMode == MovieViewMode.LIST,
                    onClick = { onViewModeChange(MovieViewMode.LIST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = viewMode == MovieViewMode.LIST) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "List view",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                ) {
                    Text("List", style = MaterialTheme.typography.labelMedium)
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
                        MovieSortOrder.entries.forEach { sortOrder ->
                            DropdownMenuItem(
                                text = { Text(stringResource(id = sortOrder.displayNameResId)) },
                                onClick = {
                                    onSortChange(sortOrder)
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
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}
