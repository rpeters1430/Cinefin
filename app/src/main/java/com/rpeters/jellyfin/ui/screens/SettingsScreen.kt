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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R

private data class SettingRecommendation(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val options: List<Int>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recommendations = remember {
        listOf(
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
    }

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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(id = R.string.settings),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(id = R.string.settings_recommendations_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(recommendations) { recommendation ->
                SettingsRecommendationCard(recommendation = recommendation)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettingsRecommendationCard(
    recommendation: SettingRecommendation,
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
                text = stringResource(id = recommendation.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(id = recommendation.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recommendation.options.forEach { optionRes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "\u2022",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(id = optionRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
