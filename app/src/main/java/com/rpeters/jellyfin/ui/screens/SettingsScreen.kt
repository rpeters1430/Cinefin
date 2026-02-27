package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.FeatureFlags
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.CurrentUserDetails
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveMediaListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.image.AvatarImage
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.theme.ShapeTokens
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.RemoteConfigViewModel

@OptInAppExperimentalApis
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentServer: JellyfinServer? = null,
    currentUser: CurrentUserDetails? = null,
    userAvatarUrl: String? = null,
    onLogout: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    onManagePinsClick: () -> Unit = {},
    onSubtitleSettingsClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onAppearanceSettingsClick: () -> Unit = {},
    onPlaybackSettingsClick: () -> Unit = {},
    onDownloadsSettingsClick: () -> Unit = {},
    onNotificationsSettingsClick: () -> Unit = {},
    onPrivacySettingsClick: () -> Unit = {},
    onAccessibilitySettingsClick: () -> Unit = {},
    onTranscodingDiagnosticsClick: () -> Unit = {},
    onAiDiagnosticsClick: () -> Unit = {},
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
    remoteConfigViewModel: RemoteConfigViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val showTranscodingDiagnostics = remoteConfigViewModel.getBoolean(FeatureFlags.Experimental.SHOW_TRANSCODING_DIAGNOSTICS)

    SettingsScreenContent(
        enableManagementActions = libraryActionPrefs.enableManagementActions,
        onToggleManagementActions = libraryActionsPreferencesViewModel::setManagementActionsEnabled,
        onBackClick = onBackClick,
        modifier = modifier,
        currentServer = currentServer,
        currentUser = currentUser,
        userAvatarUrl = userAvatarUrl,
        onLogout = onLogout,
        onNowPlayingClick = onNowPlayingClick,
        onManagePinsClick = onManagePinsClick,
        onSubtitleSettingsClick = onSubtitleSettingsClick,
        onPrivacyPolicyClick = onPrivacyPolicyClick,
        onAppearanceSettingsClick = onAppearanceSettingsClick,
        onPlaybackSettingsClick = onPlaybackSettingsClick,
        onDownloadsSettingsClick = onDownloadsSettingsClick,
        onNotificationsSettingsClick = onNotificationsSettingsClick,
        onPrivacySettingsClick = onPrivacySettingsClick,
        onAccessibilitySettingsClick = onAccessibilitySettingsClick,
        onTranscodingDiagnosticsClick = onTranscodingDiagnosticsClick,
        onAiDiagnosticsClick = onAiDiagnosticsClick,
        showTranscodingDiagnostics = showTranscodingDiagnostics,
    )
}

@OptInAppExperimentalApis
@Composable
private fun SettingsScreenContent(
    enableManagementActions: Boolean,
    onToggleManagementActions: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentServer: JellyfinServer? = null,
    currentUser: CurrentUserDetails? = null,
    userAvatarUrl: String? = null,
    onLogout: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    onManagePinsClick: () -> Unit = {},
    onSubtitleSettingsClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onAppearanceSettingsClick: () -> Unit = {},
    onPlaybackSettingsClick: () -> Unit = {},
    onDownloadsSettingsClick: () -> Unit = {},
    onNotificationsSettingsClick: () -> Unit = {},
    onPrivacySettingsClick: () -> Unit = {},
    onAccessibilitySettingsClick: () -> Unit = {},
    onTranscodingDiagnosticsClick: () -> Unit = {},
    onAiDiagnosticsClick: () -> Unit = {},
    showTranscodingDiagnostics: Boolean = true,
) {
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
        bottomBar = {
            MiniPlayer(onExpandClick = onNowPlayingClick)
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (currentUser != null || currentServer != null) {
                item {
                    AccountCard(
                        currentUser = currentUser,
                        userAvatarUrl = userAvatarUrl,
                        currentServer = currentServer,
                        onLogout = onLogout,
                    )
                }
            }

            item {
                LibraryManagementCard(
                    enabled = enableManagementActions,
                    onToggle = onToggleManagementActions,
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

            if (showTranscodingDiagnostics) {
                item {
                    ExpressiveMediaListItem(
                        title = "Transcoding Diagnostics",
                        subtitle = "Analyze which videos need transcoding and why",
                        leadingIcon = Icons.Default.BugReport,
                        onClick = onTranscodingDiagnosticsClick,
                    )
                }
            }

            item {
                ExpressiveMediaListItem(
                    title = "AI Backend Diagnostics",
                    subtitle = "Check cloud API status and troubleshoot AI features",
                    leadingIcon = Icons.Default.AutoAwesome,
                    onClick = onAiDiagnosticsClick,
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
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeTokens.Large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.settings_library_management_title),
                style = MaterialTheme.typography.titleLarge,
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
}

@Composable
private fun PinningManagementCard(
    onManagePinsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeTokens.Large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.settings_pinning_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(id = R.string.settings_pinning_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpressiveFilledButton(
                onClick = onManagePinsClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.settings_pinning_manage))
            }
        }
    }
}

@OptInAppExperimentalApis
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    JellyfinAndroidTheme {
        SettingsScreenContent(
            enableManagementActions = true,
            onToggleManagementActions = {},
            onBackClick = {},
        )
    }
}

@Composable
private fun AccountCard(
    currentUser: CurrentUserDetails?,
    userAvatarUrl: String?,
    currentServer: JellyfinServer?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = currentUser?.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.default_username)
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeTokens.Large,
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (userAvatarUrl != null) {
                AvatarImage(
                    imageUrl = userAvatarUrl,
                    userName = displayName,
                    modifier = Modifier.size(48.dp),
                    size = 48.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountBox,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (currentServer != null) {
                    Text(
                        text = currentServer.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(id = R.string.sign_out))
            }
        }
    }
}

private const val TAG = "SettingsScreen"
