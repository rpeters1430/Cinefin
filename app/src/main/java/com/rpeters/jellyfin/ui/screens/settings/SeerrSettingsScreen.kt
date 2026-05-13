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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import com.rpeters.jellyfin.ui.viewmodel.SeerrSettingsViewModel

@Composable
fun SeerrSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SeerrSettingsViewModel = hiltViewModel()
) {
    val seerrPreferences by viewModel.seerrPreferences.collectAsStateWithLifecycle()
    val connectionTestState by viewModel.connectionTestState.collectAsStateWithLifecycle()
    val isPluginConfigured by viewModel.isPluginConfigured.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Seerr Integration",
                navigationIcon = {
                    ExpressiveBackNavigationIcon(onClick = onNavigateBack)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ExpressiveSettingsCard(
                title = "Status",
                icon = Icons.Default.Info
            ) {
                ExpressiveSwitchListItem(
                    title = "Enable Seerr Integration",
                    subtitle = "Show Requests tab in bottom navigation",
                    checked = seerrPreferences.isEnabled,
                    onCheckedChange = { viewModel.setEnabled(it) },
                    leadingIcon = Icons.Default.AddCircle
                )
            }

            if (isPluginConfigured) {
                ExpressiveSettingsCard(
                    title = "Cinefin Server Plugin",
                    icon = Icons.Default.Check,
                    description = "Media requests are securely managed by your Jellyfin server."
                ) {
                    Text(
                        text = "The Cinefin Server Plugin is installed and configured on your Jellyfin server. " +
                                "All Overseerr, Sonarr, and Radarr requests are routed automatically. " +
                                "No local configuration is necessary.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ExpressiveSettingsCard(
                    title = "Connection",
                    icon = Icons.Default.Link,
                    description = "Configure your Seerr, Overseerr, or Jellyseerr instance"
                ) {
                    OutlinedTextField(
                        value = seerrPreferences.baseUrl,
                        onValueChange = { viewModel.updateBaseUrl(it) },
                        label = { Text("Seerr URL") },
                        placeholder = { Text("https://seerr.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                OutlinedTextField(
                    value = seerrPreferences.apiKey,
                    onValueChange = { viewModel.updateApiKey(it) },
                    label = { Text("API Key") },
                    placeholder = { Text("Your Seerr API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Text(
                    text = "You can find your API Key in Seerr Settings → General.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveFilledButton(
                        onClick = viewModel::testConnection,
                        enabled = seerrPreferences.isValid && connectionTestState !is ConnectionTestState.Testing,
                    ) {
                        if (connectionTestState is ConnectionTestState.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Testing…")
                        } else {
                            Text("Test Connection")
                        }
                    }

                    when (val state = connectionTestState) {
                        is ConnectionTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is ConnectionTestState.Failure -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> Unit
                    }
                }
            }
            }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(JellyfinExpressiveTheme.shapes.control)
                        .background(JellyfinExpressiveTheme.colors.sectionIconContainer),
                    contentAlignment = Alignment.Center
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
                            fontWeight = FontWeight.Bold
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
