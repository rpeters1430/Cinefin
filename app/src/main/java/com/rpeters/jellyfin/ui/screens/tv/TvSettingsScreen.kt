package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.viewmodel.PlaybackPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import com.rpeters.jellyfin.ui.viewmodel.SubtitleAppearancePreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
fun TvSettingsScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    connectionViewModel: ServerConnectionViewModel = hiltViewModel(),
    playbackPreferencesViewModel: PlaybackPreferencesViewModel = hiltViewModel(),
    subtitlePreferencesViewModel: SubtitleAppearancePreferencesViewModel = hiltViewModel(),
    themePreferencesViewModel: ThemePreferencesViewModel = hiltViewModel(),
) {
    val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle()
    val playbackPreferences by playbackPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val subtitlePreferences by subtitlePreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val themePreferences by themePreferencesViewModel.themePreferences.collectAsStateWithLifecycle()
    val tvLayout = CinefinTvTheme.layout

    val sections = listOf(
        TvSettingsSection(
            title = "Account",
            subtitle = "Who you are signed in as and where this TV is connected.",
            items = listOf(
                TvSettingsItem("Server", connectionState.savedServerUrl.ifBlank { "Not connected" }),
                TvSettingsItem("Username", connectionState.savedUsername.ifBlank { "Unknown account" }),
                TvSettingsItem(
                    "Session",
                    if (connectionState.hasSavedPassword) "Saved sign-in available" else "No saved credentials",
                ),
            ),
        ),
        TvSettingsSection(
            title = "Playback",
            subtitle = "How playback resumes, streams, and moves between episodes.",
            items = listOf(
                TvSettingsItem("Resume Playback", playbackPreferences.resumePlaybackMode.name.prettySettingLabel()),
                TvSettingsItem("Autoplay Next Episode", playbackPreferences.autoPlayNextEpisode.onOffLabel()),
                TvSettingsItem("Transcoding Quality", playbackPreferences.transcodingQuality.name.prettySettingLabel()),
                TvSettingsItem("Audio Channels", playbackPreferences.audioChannels.name.prettySettingLabel()),
            ),
        ),
        TvSettingsSection(
            title = "Audio & Subtitles",
            subtitle = "Subtitle presentation and language defaults used during playback.",
            items = listOf(
                TvSettingsItem("Preferred Audio Language", playbackPreferences.preferredAudioLanguage ?: "System default"),
                TvSettingsItem("Subtitle Size", subtitlePreferences.textSize.name.prettySettingLabel()),
                TvSettingsItem("Subtitle Font", subtitlePreferences.font.name.prettySettingLabel()),
                TvSettingsItem("Subtitle Background", subtitlePreferences.background.name.prettySettingLabel()),
            ),
        ),
        TvSettingsSection(
            title = "Appearance",
            subtitle = "Theme choices that affect contrast, motion, and overall presentation.",
            items = listOf(
                TvSettingsItem("Theme Mode", themePreferences.themeMode.name.prettySettingLabel()),
                TvSettingsItem("Accent Color", themePreferences.accentColor.name.prettySettingLabel()),
                TvSettingsItem("Contrast", themePreferences.contrastLevel.name.prettySettingLabel()),
                TvSettingsItem("Dynamic Color", themePreferences.useDynamicColors.onOffLabel()),
                TvSettingsItem("Reduce Motion", themePreferences.respectReduceMotion.onOffLabel()),
            ),
        ),
        TvSettingsSection(
            title = "Diagnostics",
            subtitle = "Useful environment details when checking this TV setup.",
            items = listOf(
                TvSettingsItem("App Version", BuildConfig.VERSION_NAME),
                TvSettingsItem("Build Type", BuildConfig.BUILD_TYPE.prettySettingLabel()),
                TvSettingsItem("Remember Login", connectionState.rememberLogin.onOffLabel()),
                TvSettingsItem("Biometric Sign-In", connectionState.isBiometricAuthEnabled.onOffLabel()),
            ),
        ),
    )

    Box(modifier = modifier.fillMaxSize()) {
        TvImmersiveBackground(backdropUrl = null)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tvLayout.screenHorizontalPadding)
                .padding(top = tvLayout.screenTopPadding, bottom = tvLayout.contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvText(
                    text = "Settings",
                    style = TvMaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
                TvText(
                    text = "A TV-first overview of account, playback, subtitle, appearance, and device details.",
                    style = TvMaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            sections.forEach { section ->
                TvSettingsSectionCard(section = section)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton(
                    onClick = { themePreferencesViewModel.resetToDefaults() },
                    colors = TvButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    ),
                ) {
                    TvText("Reset Theme")
                }

                TvButton(
                    onClick = {
                        connectionViewModel.logout()
                        onSignOut()
                    },
                    colors = TvButtonDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.error,
                        contentColor = TvMaterialTheme.colorScheme.onError,
                    ),
                ) {
                    TvText("Sign Out")
                }
            }
        }
    }
}

private data class TvSettingsSection(
    val title: String,
    val subtitle: String,
    val items: List<TvSettingsItem>,
)

private data class TvSettingsItem(
    val label: String,
    val value: String,
)

@Composable
private fun TvSettingsSectionCard(
    section: TvSettingsSection,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TvText(
                text = section.title,
                style = TvMaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            TvText(
                text = section.subtitle,
                style = TvMaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.68f),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(end = 24.dp),
        ) {
            items(section.items, key = { item -> "${section.title}_${item.label}" }) { item ->
                TvCard(
                    onClick = {},
                    scale = TvCardDefaults.scale(focusedScale = 1.03f),
                    colors = TvCardDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f)),
                ) {
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TvText(
                            text = item.label.uppercase(),
                            style = TvMaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.62f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        TvText(
                            text = item.value,
                            style = TvMaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun Boolean.onOffLabel(): String = if (this) "On" else "Off"

private fun String.prettySettingLabel(): String {
    return lowercase()
        .split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase() } }
}
