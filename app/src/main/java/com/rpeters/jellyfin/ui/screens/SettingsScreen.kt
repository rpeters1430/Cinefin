package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveMediaListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel

@OptInAppExperimentalApis
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onManagePinsClick: () -> Unit = {},
    onSubtitleSettingsClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onAppearanceSettingsClick: () -> Unit = {},
    onPlaybackSettingsClick: () -> Unit = {},
    onDownloadsSettingsClick: () -> Unit = {},
    onNotificationsSettingsClick: () -> Unit = {},
    onPrivacySettingsClick: () -> Unit = {},
    onAccessibilitySettingsClick: () -> Unit = {},
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LibraryManagementCard(
                    enabled = libraryActionPrefs.enableManagementActions,
                    onToggle = libraryActionsPreferencesViewModel::setManagementActionsEnabled,
                )
            }

            item {
                PinningManagementCard(onManagePinsClick = onManagePinsClick)
            }

            item {
                SettingsHeader(
                    titleStyle = MaterialTheme.typography.headlineSmall,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.settings_appearance_title),
                    subtitle = stringResource(id = R.string.settings_appearance_description),
                    leadingIcon = Icons.Default.Palette,
                    onClick = onAppearanceSettingsClick,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.settings_playback_title),
                    subtitle = stringResource(id = R.string.settings_playback_description),
                    leadingIcon = Icons.Default.PlayCircle,
                    onClick = onPlaybackSettingsClick,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.settings_downloads_title),
                    subtitle = stringResource(id = R.string.settings_downloads_description),
                    leadingIcon = Icons.Default.Download,
                    onClick = onDownloadsSettingsClick,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.settings_subtitles_title),
                    subtitle = stringResource(id = R.string.settings_subtitles_description),
                    leadingIcon = Icons.Default.ClosedCaption,
                    onClick = onSubtitleSettingsClick,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.settings_notifications_title),
                    subtitle = stringResource(id = R.string.settings_notifications_description),
                    leadingIcon = Icons.Default.Notifications,
                    onClick = onNotificationsSettingsClick,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.settings_privacy_title),
                    subtitle = stringResource(id = R.string.settings_privacy_description),
                    leadingIcon = Icons.Default.Security,
                    onClick = onPrivacySettingsClick,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.settings_accessibility_title),
                    subtitle = stringResource(id = R.string.settings_accessibility_description),
                    leadingIcon = Icons.Default.Accessibility,
                    onClick = onAccessibilitySettingsClick,
                )
            }

            item {
                ExpressiveMediaListItem(
                    title = stringResource(id = R.string.privacy_policy_title),
                    subtitle = stringResource(id = R.string.privacy_policy_description),
                    leadingIcon = Icons.Default.Settings,
                    onClick = onPrivacyPolicyClick,
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LibraryManagementCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionCard(modifier = modifier) {
        Text(
            text = stringResource(id = R.string.settings_library_management_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        ExpressiveSwitchListItem(
            title = stringResource(id = R.string.settings_library_management_toggle),
            subtitle = stringResource(id = R.string.settings_library_management_description),
            checked = enabled,
            onCheckedChange = onToggle,
            leadingIcon = Icons.Default.Settings,
        )
    }
}

@Composable
private fun PinningManagementCard(
    onManagePinsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionContainer(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.settings_pinning_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(id = R.string.settings_pinning_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onManagePinsClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(id = R.string.settings_pinning_manage))
        }
    }
}

@Composable
private fun SettingsSectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private const val TAG = "SettingsScreen"
