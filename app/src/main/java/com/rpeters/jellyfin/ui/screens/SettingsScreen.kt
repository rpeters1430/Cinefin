package com.rpeters.jellyfin.ui.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveMediaListItem
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.components.ExpressiveTextButton
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.image.AvatarImage
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.theme.JellyfinExpressiveTheme
import com.rpeters.jellyfin.ui.theme.ShapeTokens
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.RemoteConfigViewModel
import com.rpeters.jellyfin.ui.viewmodel.ServerManagementAction
import com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementViewModel

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
    serverManagementViewModel: SettingsServerManagementViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val showTranscodingDiagnostics = remoteConfigViewModel.getBoolean(FeatureFlags.Experimental.SHOW_TRANSCODING_DIAGNOSTICS)
    val serverManagementState by serverManagementViewModel.state.collectAsStateWithLifecycle()
    val canManageServer = currentUser?.isAdministrator == true

    SettingsScreenContent(
        enableManagementActions = libraryActionPrefs.enableManagementActions,
        onToggleManagementActions = libraryActionsPreferencesViewModel::setManagementActionsEnabled,
        canManageServer = canManageServer,
        serverManagementState = serverManagementState,
        onRescanLibraries = serverManagementViewModel::rescanLibraries,
        onRestartServer = serverManagementViewModel::restartServer,
        onShutdownServer = serverManagementViewModel::shutdownServer,
        onServerManagementSuccessConsumed = serverManagementViewModel::clearSuccessMessage,
        onServerManagementErrorConsumed = serverManagementViewModel::clearErrorMessage,
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
    canManageServer: Boolean,
    serverManagementState: com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementState,
    onRescanLibraries: () -> Unit,
    onRestartServer: () -> Unit,
    onShutdownServer: () -> Unit,
    onServerManagementSuccessConsumed: () -> Unit,
    onServerManagementErrorConsumed: () -> Unit,
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
    val context = LocalContext.current
    val activity = context as? Activity
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val adaptiveConfig = windowSizeClass?.let { rememberAdaptiveLayoutConfig(it) }
    val isTabletLayout = adaptiveConfig?.isTablet == true &&
        windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val contentPadding = adaptiveConfig?.contentPadding ?: PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val sectionSpacing = adaptiveConfig?.sectionSpacing ?: 16.dp

    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(serverManagementState.successMessage) {
        val message = serverManagementState.successMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onServerManagementSuccessConsumed()
    }

    LaunchedEffect(serverManagementState.errorMessage) {
        val message = serverManagementState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onServerManagementErrorConsumed()
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(id = R.string.settings),
                navigationIcon = {
                    ExpressiveBackNavigationIcon(onClick = onBackClick)
                },
                actions = {
                    ExpressiveTextButton(onClick = {
                        haptics.lightClick()
                        onAppearanceSettingsClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.settings_appearance_title))
                    }
                },
                translucent = true,
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier,
    ) { paddingValues ->
        if (isTabletLayout) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = contentPadding.calculateTopPadding(),
                    end = 24.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 1440.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(0.92f),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                        ) {
                            SettingsBrandHeader()
                            if (currentUser != null || currentServer != null) {
                                AccountCard(
                                    currentUser = currentUser,
                                    userAvatarUrl = userAvatarUrl,
                                    currentServer = currentServer,
                                    onLogout = onLogout,
                                )
                            }
                            LibraryManagementCard(
                                enabled = enableManagementActions,
                                onToggle = onToggleManagementActions,
                            )
                            if (canManageServer) {
                                ServerManagementCard(
                                    state = serverManagementState,
                                    onRescanLibraries = onRescanLibraries,
                                    onRestartServer = onRestartServer,
                                    onShutdownServer = onShutdownServer,
                                )
                            }
                            PinningManagementCard(onManagePinsClick = onManagePinsClick)
                        }

                        SettingsDestinationsSection(
                            modifier = Modifier.weight(1.18f),
                            onAppearanceSettingsClick = onAppearanceSettingsClick,
                            onPlaybackSettingsClick = onPlaybackSettingsClick,
                            onDownloadsSettingsClick = onDownloadsSettingsClick,
                            onSubtitleSettingsClick = onSubtitleSettingsClick,
                            onNotificationsSettingsClick = onNotificationsSettingsClick,
                            onPrivacySettingsClick = onPrivacySettingsClick,
                            onAccessibilitySettingsClick = onAccessibilitySettingsClick,
                            onPrivacyPolicyClick = onPrivacyPolicyClick,
                            onTranscodingDiagnosticsClick = onTranscodingDiagnosticsClick,
                            onAiDiagnosticsClick = onAiDiagnosticsClick,
                            showTranscodingDiagnostics = showTranscodingDiagnostics,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SettingsBrandHeader() }

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

                if (canManageServer) {
                    item {
                        ServerManagementCard(
                            state = serverManagementState,
                            onRescanLibraries = onRescanLibraries,
                            onRestartServer = onRestartServer,
                            onShutdownServer = onShutdownServer,
                        )
                    }
                }

                item {
                    PinningManagementCard(onManagePinsClick = onManagePinsClick)
                }

                item {
                    SettingsDestinationsSection(
                        onAppearanceSettingsClick = onAppearanceSettingsClick,
                        onPlaybackSettingsClick = onPlaybackSettingsClick,
                        onDownloadsSettingsClick = onDownloadsSettingsClick,
                        onSubtitleSettingsClick = onSubtitleSettingsClick,
                        onNotificationsSettingsClick = onNotificationsSettingsClick,
                        onPrivacySettingsClick = onPrivacySettingsClick,
                        onAccessibilitySettingsClick = onAccessibilitySettingsClick,
                        onPrivacyPolicyClick = onPrivacyPolicyClick,
                        onTranscodingDiagnosticsClick = onTranscodingDiagnosticsClick,
                        onAiDiagnosticsClick = onAiDiagnosticsClick,
                        showTranscodingDiagnostics = showTranscodingDiagnostics,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsBrandHeader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = stringResource(id = R.string.app_name),
                modifier = Modifier.size(120.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsDestinationsSection(
    onAppearanceSettingsClick: () -> Unit,
    onPlaybackSettingsClick: () -> Unit,
    onDownloadsSettingsClick: () -> Unit,
    onSubtitleSettingsClick: () -> Unit,
    onNotificationsSettingsClick: () -> Unit,
    onPrivacySettingsClick: () -> Unit,
    onAccessibilitySettingsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTranscodingDiagnosticsClick: () -> Unit,
    onAiDiagnosticsClick: () -> Unit,
    showTranscodingDiagnostics: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsHeader(
            titleStyle = MaterialTheme.typography.headlineSmall,
        )

        ExpressiveMediaListItem(
            title = stringResource(id = R.string.settings_appearance_title),
            subtitle = stringResource(id = R.string.settings_appearance_description),
            leadingIcon = Icons.Default.Palette,
            onClick = onAppearanceSettingsClick,
        )
        ExpressiveMediaListItem(
            title = stringResource(id = R.string.settings_playback_title),
            subtitle = stringResource(id = R.string.settings_playback_description),
            leadingIcon = Icons.Default.PlayCircle,
            onClick = onPlaybackSettingsClick,
        )
        if (showTranscodingDiagnostics) {
            ExpressiveMediaListItem(
                title = "Transcoding Diagnostics",
                subtitle = "Analyze which videos need transcoding and why",
                leadingIcon = Icons.Default.BugReport,
                onClick = onTranscodingDiagnosticsClick,
            )
        }
        ExpressiveMediaListItem(
            title = "AI Backend Diagnostics",
            subtitle = "Check cloud API status and troubleshoot AI features",
            leadingIcon = Icons.Default.AutoAwesome,
            onClick = onAiDiagnosticsClick,
        )
        ExpressiveMediaListItem(
            title = stringResource(id = R.string.settings_downloads_title),
            subtitle = stringResource(id = R.string.settings_downloads_description),
            leadingIcon = Icons.Default.Download,
            onClick = onDownloadsSettingsClick,
        )
        ExpressiveMediaListItem(
            title = stringResource(id = R.string.settings_subtitles_title),
            subtitle = stringResource(id = R.string.settings_subtitles_description),
            leadingIcon = Icons.Default.ClosedCaption,
            onClick = onSubtitleSettingsClick,
        )
        ExpressiveMediaListItem(
            title = stringResource(id = R.string.settings_notifications_title),
            subtitle = stringResource(id = R.string.settings_notifications_description),
            leadingIcon = Icons.Default.Notifications,
            onClick = onNotificationsSettingsClick,
        )
        ExpressiveMediaListItem(
            title = stringResource(id = R.string.settings_privacy_title),
            subtitle = stringResource(id = R.string.settings_privacy_description),
            leadingIcon = Icons.Default.Security,
            onClick = onPrivacySettingsClick,
        )
        ExpressiveMediaListItem(
            title = stringResource(id = R.string.settings_accessibility_title),
            subtitle = stringResource(id = R.string.settings_accessibility_description),
            leadingIcon = Icons.Default.Accessibility,
            onClick = onAccessibilitySettingsClick,
        )
        ExpressiveMediaListItem(
            title = stringResource(id = R.string.privacy_policy_title),
            subtitle = stringResource(id = R.string.privacy_policy_description),
            leadingIcon = Icons.Default.Settings,
            onClick = onPrivacyPolicyClick,
        )

        Spacer(modifier = Modifier.height(8.dp))
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
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
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
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
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

@Composable
private fun ServerManagementCard(
    state: com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementState,
    onRescanLibraries: () -> Unit,
    onRestartServer: () -> Unit,
    onShutdownServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingAction by remember { mutableStateOf<ServerManagementAction?>(null) }
    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()

    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
        shape = ShapeTokens.Large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.settings_server_management_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(id = R.string.settings_server_management_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ExpressiveFilledButton(
                onClick = {
                    haptics.lightClick()
                    onRescanLibraries()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isRunning,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.settings_server_management_rescan))
            }

            ExpressiveTextButton(
                onClick = {
                    haptics.lightClick()
                    pendingAction = ServerManagementAction.RESTART_SERVER
                },
                enabled = !state.isRunning,
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.settings_server_management_restart))
            }

            ExpressiveTextButton(
                onClick = {
                    haptics.lightClick()
                    pendingAction = ServerManagementAction.SHUTDOWN_SERVER
                },
                enabled = !state.isRunning,
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.settings_server_management_shutdown))
            }
        }
    }

    if (pendingAction != null) {
        val action = pendingAction
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    text = stringResource(
                        id = if (action == ServerManagementAction.RESTART_SERVER) {
                            R.string.settings_server_management_restart_confirm_title
                        } else {
                            R.string.settings_server_management_shutdown_confirm_title
                        },
                    ),
                )
            },
            text = {
                Text(
                    text = stringResource(
                        id = if (action == ServerManagementAction.RESTART_SERVER) {
                            R.string.settings_server_management_restart_confirm_message
                        } else {
                            R.string.settings_server_management_shutdown_confirm_message
                        },
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        when (action) {
                            ServerManagementAction.RESTART_SERVER -> onRestartServer()
                            ServerManagementAction.SHUTDOWN_SERVER -> onShutdownServer()
                            ServerManagementAction.RESCAN_LIBRARIES,
                            null,
                            -> Unit
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = stringResource(id = R.string.continue_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
        )
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
            canManageServer = true,
            serverManagementState = com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementState(),
            onRescanLibraries = {},
            onRestartServer = {},
            onShutdownServer = {},
            onServerManagementSuccessConsumed = {},
            onServerManagementErrorConsumed = {},
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
        containerColor = JellyfinExpressiveTheme.colors.sectionContainerHigh,
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
            val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()
            ExpressiveFilledButton(
                onClick = {
                    haptics.heavyClick()
                    onLogout()
                },
                modifier = Modifier,
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
