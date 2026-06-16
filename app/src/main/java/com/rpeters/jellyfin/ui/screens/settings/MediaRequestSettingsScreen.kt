package com.rpeters.jellyfin.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.theme.JellyfinExpressiveTheme
import com.rpeters.jellyfin.ui.viewmodel.ConnectionTestState
import com.rpeters.jellyfin.ui.viewmodel.MediaRequestSettingsViewModel
import com.rpeters.jellyfin.ui.viewmodel.CredentialImportState

@OptInAppExperimentalApis
@Composable
fun MediaRequestSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MediaRequestSettingsViewModel = hiltViewModel(),
) {
    val seerrPrefs by viewModel.seerrPreferences.collectAsStateWithLifecycle()
    val seerrTestState by viewModel.seerrTestState.collectAsStateWithLifecycle()
    val sonarrPrefs by viewModel.sonarrPreferences.collectAsStateWithLifecycle()
    val sonarrTestState by viewModel.sonarrTestState.collectAsStateWithLifecycle()
    val radarrPrefs by viewModel.radarrPreferences.collectAsStateWithLifecycle()
    val radarrTestState by viewModel.radarrTestState.collectAsStateWithLifecycle()
    val credentialImportState by viewModel.credentialImportState.collectAsStateWithLifecycle()
    val isCurrentUserAdmin by viewModel.isCurrentUserAdmin.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Media Requests",
                navigationIcon = { ExpressiveBackNavigationIcon(onClick = onNavigateBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Server Sync ──────────────────────────────────────────────────
            ExpressiveSettingsCard(
                title = "Cinefin Server Plugin",
                icon = Icons.Default.CloudDownload,
                description = "Import Sonarr, Radarr, and Overseerr credentials from Jellyfin"
            ) {
                ExpressiveFilledButton(
                    onClick = viewModel::importCredentialsFromPlugin,
                    enabled = isCurrentUserAdmin && credentialImportState !is CredentialImportState.Importing,
                ) {
                    if (credentialImportState is CredentialImportState.Importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importing…")
                    } else {
                        Text("Import from Jellyfin")
                    }
                }

                if (!isCurrentUserAdmin) {
                    Text(
                        text = "Requires a Jellyfin administrator account",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                when (val state = credentialImportState) {
                    is CredentialImportState.Success -> Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    is CredentialImportState.Failure -> Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    else -> Unit
                }
            }

            // ── Seerr / Overseerr ─────────────────────────────────────────────
            ExpressiveSettingsCard(
                title = "Seerr / Overseerr / Jellyseerr",
                icon = Icons.Default.Search,
                description = "Search trending media and submit requests",
            ) {
                ExpressiveSwitchListItem(
                    title = "Enable Seerr",
                    subtitle = "Required for search and trending — routes requests through your *arr apps",
                    checked = seerrPrefs.isEnabled,
                    onCheckedChange = viewModel::setSeerrEnabled,
                    leadingIcon = Icons.Default.Search,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = seerrPrefs.baseUrl,
                    onValueChange = viewModel::updateSeerrUrl,
                    label = { Text("URL") },
                    placeholder = { Text("https://yourhost.com/overseerr") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = seerrPrefs.apiKey,
                    onValueChange = viewModel::updateSeerrApiKey,
                    label = { Text("API Key") },
                    placeholder = { Text("Found in Overseerr → Settings → General") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                ServiceTestRow(
                    enabled = seerrPrefs.isValid,
                    testState = seerrTestState,
                    onTest = viewModel::testSeerr,
                )
            }

            // ── Sonarr ────────────────────────────────────────────────────────
            ExpressiveSettingsCard(
                title = "Sonarr",
                icon = Icons.Default.Tv,
                description = "Direct TV show download management",
            ) {
                ExpressiveSwitchListItem(
                    title = "Enable Sonarr",
                    subtitle = "Request TV shows and individual episodes directly",
                    checked = sonarrPrefs.isEnabled,
                    onCheckedChange = viewModel::setSonarrEnabled,
                    leadingIcon = Icons.Default.Tv,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = sonarrPrefs.baseUrl,
                    onValueChange = viewModel::updateSonarrUrl,
                    label = { Text("URL") },
                    placeholder = { Text("https://yourhost.com/sonarr") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = sonarrPrefs.apiKey,
                    onValueChange = viewModel::updateSonarrApiKey,
                    label = { Text("API Key") },
                    placeholder = { Text("Found in Sonarr → Settings → General") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                ServiceTestRow(
                    enabled = sonarrPrefs.isValid,
                    testState = sonarrTestState,
                    onTest = viewModel::testSonarr,
                )
            }

            // ── Radarr ────────────────────────────────────────────────────────
            ExpressiveSettingsCard(
                title = "Radarr",
                icon = Icons.Default.Movie,
                description = "Direct movie download management",
            ) {
                ExpressiveSwitchListItem(
                    title = "Enable Radarr",
                    subtitle = "Request movies directly without Seerr",
                    checked = radarrPrefs.isEnabled,
                    onCheckedChange = viewModel::setRadarrEnabled,
                    leadingIcon = Icons.Default.Movie,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = radarrPrefs.baseUrl,
                    onValueChange = viewModel::updateRadarrUrl,
                    label = { Text("URL") },
                    placeholder = { Text("https://yourhost.com/radarr") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = radarrPrefs.apiKey,
                    onValueChange = viewModel::updateRadarrApiKey,
                    label = { Text("API Key") },
                    placeholder = { Text("Found in Radarr → Settings → General") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                ServiceTestRow(
                    enabled = radarrPrefs.isValid,
                    testState = radarrTestState,
                    onTest = viewModel::testRadarr,
                )
            }

            // ── How it works hint ─────────────────────────────────────────────
            ExpressiveSettingsCard(
                title = "How requests are routed",
                icon = Icons.Default.CloudDownload,
            ) {
                Text(
                    text = "• Seerr enabled → all requests go through Seerr (recommended)\n" +
                           "• Seerr disabled, Radarr enabled → movies go directly to Radarr\n" +
                           "• Seerr disabled, Sonarr enabled → TV shows go directly to Sonarr\n\n" +
                           "Seerr is also required for search and trending. " +
                           "Sonarr/Radarr alone will only let you request items you find via search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ServiceTestRow(
    enabled: Boolean,
    testState: ConnectionTestState,
    onTest: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExpressiveFilledButton(
            onClick = onTest,
            enabled = enabled && testState !is ConnectionTestState.Testing,
        ) {
            if (testState is ConnectionTestState.Testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Testing…")
            } else {
                Text("Test Connection")
            }
        }

        when (val state = testState) {
            is ConnectionTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            is ConnectionTestState.Failure -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            else -> Unit
        }
    }
}

@Composable
private fun ExpressiveSettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
        shape = JellyfinExpressiveTheme.shapes.section,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(JellyfinExpressiveTheme.shapes.control)
                        .background(JellyfinExpressiveTheme.colors.sectionIconContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = JellyfinExpressiveTheme.colors.sectionIconContent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}
