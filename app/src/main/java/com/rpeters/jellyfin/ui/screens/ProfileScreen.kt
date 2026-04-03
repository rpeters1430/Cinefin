package com.rpeters.jellyfin.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.ServerInfo
import com.rpeters.jellyfin.data.model.CurrentUserDetails
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBarAction
import com.rpeters.jellyfin.ui.image.AvatarImage
import java.text.DateFormat
import java.util.Date

@OptInAppExperimentalApis
@Composable
fun ProfileScreen(
    currentServer: JellyfinServer?,
    serverInfo: ServerInfo?,
    currentUser: CurrentUserDetails?,
    userAvatarUrl: String?,
    onLogout: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val activity = context as? Activity
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val adaptiveConfig = windowSizeClass?.let { rememberAdaptiveLayoutConfig(it) }
    val isTabletLayout = adaptiveConfig?.isTablet == true &&
        windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val contentPadding = adaptiveConfig?.contentPadding ?: PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val sectionSpacing = adaptiveConfig?.sectionSpacing ?: 24.dp
    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(id = R.string.profile),
                navigationIcon = {
                    if (showBackButton) {
                        ExpressiveBackNavigationIcon(onClick = onBackClick)
                    }
                },
                actions = {
                    ExpressiveTopAppBarAction(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.settings),
                        onClick = onSettingsClick,
                    )
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        val unknownLabel = stringResource(id = R.string.unknown)
        val displayName = currentUser?.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.default_username)
        val serverVersion = serverInfo?.version ?: currentServer?.version ?: stringResource(id = R.string.unknown)
        val serverName = currentServer?.name ?: stringResource(id = R.string.unknown)
        val appVersion = BuildConfig.VERSION_NAME.ifBlank { unknownLabel }
        val appUpdatedDate = remember {
            runCatching {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(packageInfo.lastUpdateTime))
            }.getOrElse { unknownLabel }
        }
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = if (isTabletLayout) 24.dp else 16.dp,
                    vertical = contentPadding.calculateTopPadding(),
                )
                .widthIn(max = 1440.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            ProfileBrandHeader()

            if (isTabletLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(0.95f),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    ) {
                        ProfileHeaderCard(
                            userAvatarUrl = userAvatarUrl,
                            displayName = displayName,
                            serverVersion = serverVersion,
                            appVersion = appVersion,
                            appUpdatedDate = appUpdatedDate,
                        )
                        ServerStatusCard(
                            isConnected = currentServer?.isConnected == true,
                            version = serverVersion,
                            operatingSystem = serverInfo?.operatingSystem,
                            productName = serverInfo?.productName,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.05f),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing),
                    ) {
                        ServerInformationCard(
                            serverName = serverName,
                            currentServer = currentServer,
                            onOpenServerUrl = currentServer?.takeIf { it.url.startsWith("http") }?.let {
                                { uriHandler.openUri(it.url) }
                            },
                        )
                        ProfileActionsCard(onSettingsClick = onSettingsClick)
                        LogoutButton(onLogout = onLogout)
                    }
                }
            } else {
                ProfileHeaderCard(
                    userAvatarUrl = userAvatarUrl,
                    displayName = displayName,
                    serverVersion = serverVersion,
                    appVersion = appVersion,
                    appUpdatedDate = appUpdatedDate,
                )

                ServerStatusCard(
                    isConnected = currentServer?.isConnected == true,
                    version = serverVersion,
                    operatingSystem = serverInfo?.operatingSystem,
                    productName = serverInfo?.productName,
                )

                ServerInformationCard(
                    serverName = serverName,
                    currentServer = currentServer,
                    onOpenServerUrl = currentServer?.takeIf { it.url.startsWith("http") }?.let {
                        { uriHandler.openUri(it.url) }
                    },
                )

                ProfileActionsCard(onSettingsClick = onSettingsClick)

                Spacer(modifier = Modifier.height(8.dp))

                LogoutButton(onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun ProfileBrandHeader(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = stringResource(id = R.string.app_name),
            modifier = Modifier.size(120.dp),
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    userAvatarUrl: String?,
    displayName: String,
    serverVersion: String,
    appVersion: String,
    appUpdatedDate: String,
    modifier: Modifier = Modifier,
) {
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (userAvatarUrl != null) {
                AvatarImage(
                    imageUrl = userAvatarUrl,
                    userName = displayName,
                    modifier = Modifier.size(64.dp),
                    size = 64.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountBox,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("${stringResource(id = R.string.version)}: $serverVersion") },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("${stringResource(id = R.string.app_version_label)}: $appVersion") },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(id = R.string.app_updated_label, appUpdatedDate)) },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(displayName) },
                )
            }
        }
    }
}

@Composable
private fun ServerInformationCard(
    serverName: String,
    currentServer: JellyfinServer?,
    onOpenServerUrl: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(id = R.string.server),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    serverName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            currentServer?.let { server ->
                ProfileInfoRow(stringResource(id = R.string.server_name_label), serverName)
                ProfileInfoRow(
                    label = stringResource(id = R.string.server_url_label),
                    value = server.url,
                    onValueClick = onOpenServerUrl,
                )
            }
        }
    }
}

@Composable
private fun ProfileActionsCard(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsHeader(
                titleStyle = MaterialTheme.typography.titleMedium,
                horizontalSpacing = 8.dp,
            )

            FilledTonalButton(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.settings_open))
            }
        }
    }
}

@Composable
private fun LogoutButton(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveFilledButton(
        onClick = onLogout,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(id = R.string.sign_out))
    }
}

@Composable
private fun ServerStatusCard(
    isConnected: Boolean,
    version: String?,
    operatingSystem: String?,
    productName: String?,
    modifier: Modifier = Modifier,
) {
    val connectionLabel = if (isConnected) {
        stringResource(id = R.string.server_status_connected)
    } else {
        stringResource(id = R.string.server_status_disconnected)
    }
    val statusContainerColor = if (isConnected) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val statusLabelColor = if (isConnected) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val unknown = stringResource(id = R.string.unknown)

    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.server_status),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(text = connectionLabel) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusContainerColor,
                        labelColor = statusLabelColor,
                    ),
                    border = null,
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(
                                id = R.string.server_status_version,
                                version ?: unknown,
                            ),
                        )
                    },
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(
                                id = R.string.server_status_os,
                                operatingSystem ?: unknown,
                            ),
                        )
                    },
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(
                                id = R.string.server_status_product,
                                productName ?: unknown,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onValueClick != null) {
                            Modifier.clickable(onClick = onValueClick)
                        } else {
                            Modifier
                        },
                    ),
            )
            IconButton(
                onClick = {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, value))
                },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(id = R.string.copy_value),
                )
            }
        }
    }
}
