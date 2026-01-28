package com.rpeters.jellyfin.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.rpeters.jellyfin.ui.viewmodel.CredentialSecurityPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel

private data class SettingRecommendation(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val options: List<Int>,
)

private val settingRecommendations = listOf(
    SettingRecommendation(
        titleRes = R.string.settings_appearance_title,
        descriptionRes = R.string.settings_appearance_description,
        options = listOf(
            R.string.settings_appearance_theme,
            R.string.settings_appearance_dynamic_color,
            R.string.settings_appearance_language,
            R.string.settings_appearance_layout,
        ),
    ),
    SettingRecommendation(
        titleRes = R.string.settings_playback_title,
        descriptionRes = R.string.settings_playback_description,
        options = listOf(
            R.string.settings_playback_quality,
            R.string.settings_playback_subtitles,
            R.string.settings_playback_autoplay,
            R.string.settings_playback_skip_intro,
        ),
    ),
    SettingRecommendation(
        titleRes = R.string.settings_downloads_title,
        descriptionRes = R.string.settings_downloads_description,
        options = listOf(
            R.string.settings_downloads_quality,
            R.string.settings_downloads_location,
            R.string.settings_downloads_wifi_only,
            R.string.settings_downloads_cleanup,
        ),
    ),
    SettingRecommendation(
        titleRes = R.string.settings_notifications_title,
        descriptionRes = R.string.settings_notifications_description,
        options = listOf(
            R.string.settings_notifications_library,
            R.string.settings_notifications_downloads,
            R.string.settings_notifications_playback,
        ),
    ),
    SettingRecommendation(
        titleRes = R.string.settings_privacy_title,
        descriptionRes = R.string.settings_privacy_description,
        options = listOf(
            R.string.settings_privacy_biometric,
            R.string.settings_privacy_cache,
            R.string.settings_privacy_diagnostics,
            R.string.settings_privacy_sensitive,
        ),
    ),
    SettingRecommendation(
        titleRes = R.string.settings_accessibility_title,
        descriptionRes = R.string.settings_accessibility_description,
        options = listOf(
            R.string.settings_accessibility_text,
            R.string.settings_accessibility_motion,
            R.string.settings_accessibility_haptics,
        ),
    ),
)

@OptInAppExperimentalApis
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onManagePinsClick: () -> Unit = {},
    onSubtitleSettingsClick: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
    credentialSecurityPreferencesViewModel: CredentialSecurityPreferencesViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val credentialSecurityPrefs by credentialSecurityPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val isCredentialSecurityUpdating by credentialSecurityPreferencesViewModel.isUpdating.collectAsStateWithLifecycle()

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
                CredentialSecurityCard(
                    enabled = credentialSecurityPrefs.requireStrongAuthForCredentials,
                    onToggle = credentialSecurityPreferencesViewModel::setStrongAuthRequired,
                    updating = isCredentialSecurityUpdating,
                )
            }

            item {
                SettingsHeader(
                    titleStyle = MaterialTheme.typography.headlineSmall,
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
                    title = stringResource(id = R.string.privacy_policy_title),
                    subtitle = stringResource(id = R.string.privacy_policy_description),
                    leadingIcon = Icons.Default.Settings,
                    onClick = onPrivacyPolicyClick,
                )
            }

            items(
                items = settingRecommendations,
                key = { it.titleRes },
                contentType = { "settings_recommendation" },
            ) { recommendation ->
                SettingsRecommendationCard(recommendation = recommendation)
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
}

@Composable
private fun CredentialSecurityCard(
    enabled: Boolean,
    updating: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.settings_credential_security_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(id = R.string.settings_credential_security_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(id = R.string.settings_credential_security_tradeoff),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(id = R.string.settings_credential_security_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = !updating,
                )
            }
        }
    }
}

@Composable
private fun PinningManagementCard(
    onManagePinsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
}

@Composable
private fun SettingsRecommendationCard(
    recommendation: SettingRecommendation,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = recommendation.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(id = recommendation.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recommendation.options.forEach { optionRes ->
                    ExpressiveMediaListItem(
                        title = stringResource(id = optionRes),
                        leadingIcon = Icons.Default.Settings,
                    )
                }
            }
        }
    }
}
