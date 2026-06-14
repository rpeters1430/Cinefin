package com.rpeters.jellyfin.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.data.preferences.FollowedSeriesNotification
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.components.ExpressiveTextButton
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.theme.JellyfinExpressiveTheme
import com.rpeters.jellyfin.ui.viewmodel.EpisodeNotificationSettingsViewModel
import java.text.DateFormat
import java.util.Date

@OptInAppExperimentalApis
@Composable
fun EpisodeNotificationSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: EpisodeNotificationSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "New Episode Alerts",
                navigationIcon = { ExpressiveBackNavigationIcon(onClick = onNavigateBack) },
                actions = {
                    ExpressiveTextButton(onClick = viewModel::checkNow) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Check")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ExpressiveContentCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
                    shape = JellyfinExpressiveTheme.shapes.section,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExpressiveSwitchListItem(
                            title = "Enable new episode alerts",
                            subtitle = "Periodically checks followed shows on your Jellyfin server.",
                            checked = preferences.enabled,
                            onCheckedChange = viewModel::setEnabled,
                            leadingIcon = Icons.Default.Notifications,
                        )
                        if (!hasNotificationPermission) {
                            Text(
                                text = "Android notification permission is required before alerts can appear.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ExpressiveFilledButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            ) {
                                Text("Allow Notifications")
                            }
                        }
                        Text(
                            text = "Checks run in the background about every 6 hours while the device has network access. Add shows from a TV show detail page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (preferences.followedSeries.isEmpty()) {
                item {
                    ExpressiveContentCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
                        shape = JellyfinExpressiveTheme.shapes.section,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "No followed shows",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Open a TV show and choose Alert Me for New Episodes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Followed Shows",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(preferences.followedSeries, key = { it.seriesId }) { series ->
                    FollowedSeriesRow(
                        series = series,
                        onRemove = { viewModel.removeSeries(series.seriesId) },
                    )
                }
            }
        }
    }
}

@OptInAppExperimentalApis
@Composable
private fun FollowedSeriesRow(
    series: FollowedSeriesNotification,
    onRemove: () -> Unit,
) {
    ExpressiveContentCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
        shape = JellyfinExpressiveTheme.shapes.section,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.seriesName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${series.seasonCount} seasons · ${series.episodeCount} episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (series.lastCheckedAt > 0L) {
                    Text(
                        text = "Last checked ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(series.lastCheckedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}
