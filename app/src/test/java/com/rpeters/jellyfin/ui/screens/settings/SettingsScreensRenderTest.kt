package com.rpeters.jellyfin.ui.screens.settings

import com.rpeters.jellyfin.data.model.CurrentUserDetails
import com.rpeters.jellyfin.data.preferences.AiPreferences
import com.rpeters.jellyfin.data.preferences.EpisodeNotificationPreferences
import com.rpeters.jellyfin.data.preferences.FollowedSeriesNotification
import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferences
import com.rpeters.jellyfin.data.preferences.PlaybackPreferences
import com.rpeters.jellyfin.data.preferences.RadarrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SonarrPreferences
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.testing.compose.ComposeScreenTest
import com.rpeters.jellyfin.testing.compose.SharedViewModelMocks
import com.rpeters.jellyfin.ui.screens.SettingsScreen
import com.rpeters.jellyfin.ui.viewmodel.AiPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.ConnectionTestState
import com.rpeters.jellyfin.ui.viewmodel.CredentialImportState
import com.rpeters.jellyfin.ui.viewmodel.EpisodeNotificationSettingsViewModel
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MediaRequestSettingsViewModel
import com.rpeters.jellyfin.ui.viewmodel.PinnedHostState
import com.rpeters.jellyfin.ui.viewmodel.PinningSettingsViewModel
import com.rpeters.jellyfin.ui.viewmodel.PlaybackPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.PrivacySecurityState
import com.rpeters.jellyfin.ui.viewmodel.PrivacySettingsViewModel
import com.rpeters.jellyfin.ui.viewmodel.RemoteConfigViewModel
import com.rpeters.jellyfin.ui.viewmodel.SeerrSettingsViewModel
import com.rpeters.jellyfin.ui.viewmodel.ServerManagementAction
import com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementState
import com.rpeters.jellyfin.ui.viewmodel.SettingsServerManagementViewModel
import com.rpeters.jellyfin.ui.viewmodel.SubtitleAppearancePreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.ThemePreferencesViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class SettingsScreensRenderTest : ComposeScreenTest() {

    // region Main settings

    private fun renderSettings(admin: Boolean, running: Boolean, management: Boolean) {
        val libraryActions: LibraryActionsPreferencesViewModel = mockk(relaxed = true) {
            coEvery { preferences } returns MutableStateFlow(LibraryActionsPreferences(enableManagementActions = management))
        }
        val remoteConfig: RemoteConfigViewModel = mockk(relaxed = true) {
            every { getBoolean(any()) } returns true
            every { getString(any()) } returns ""
        }
        val serverManagement: SettingsServerManagementViewModel = mockk(relaxed = true) {
            coEvery { state } returns MutableStateFlow(
                SettingsServerManagementState(
                    activeAction = if (running) ServerManagementAction.values().first() else null,
                    successMessage = if (running) null else "Library scan started",
                    errorMessage = if (running) "Something failed" else null,
                ),
            )
        }
        val ai: AiPreferencesViewModel = mockk(relaxed = true) {
            coEvery { preferences } returns MutableStateFlow(AiPreferences.DEFAULT)
        }
        render {
            SettingsScreen(
                onBackClick = {},
                currentServer = SharedViewModelMocks.testServer.copy(isAdministrator = admin),
                currentUser = CurrentUserDetails(name = "tester", primaryImageTag = "tag", lastLoginDate = "2026-11-01", isAdministrator = admin),
                userAvatarUrl = null,
                libraryActionsPreferencesViewModel = libraryActions,
                remoteConfigViewModel = remoteConfig,
                serverManagementViewModel = serverManagement,
                aiPreferencesViewModel = ai,
            )
        }
    }

    @Test fun settings_admin() = renderSettings(admin = true, running = false, management = true).also { assertRendered() }

    @Test fun settings_adminRunningAction() = renderSettings(admin = true, running = true, management = false).also { assertRendered() }

    @Test fun settings_regularUser() = renderSettings(admin = false, running = false, management = false).also { assertRendered() }

    // endregion

    @Test
    fun appearance_defaultAndCustom() {
        val vm: ThemePreferencesViewModel = mockk(relaxed = true) {
            coEvery { themePreferences } returns MutableStateFlow(ThemePreferences.DEFAULT)
        }
        render { AppearanceSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun appearance_allOptionsOn() {
        val custom = ThemePreferences.DEFAULT.copy(useDynamicColors = false, respectReduceMotion = true)
        val vm: ThemePreferencesViewModel = mockk(relaxed = true) {
            coEvery { themePreferences } returns MutableStateFlow(custom)
        }
        render(theme = custom) { AppearanceSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun playback() {
        val vm: PlaybackPreferencesViewModel = mockk(relaxed = true) {
            coEvery { preferences } returns MutableStateFlow(PlaybackPreferences.DEFAULT)
        }
        render { PlaybackSettingsScreen(onBackClick = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun playback_customised() {
        val prefs = PlaybackPreferences.DEFAULT.copy(autoPlayNextEpisode = false, useExternalPlayer = true, preferredAudioLanguage = "jpn")
        val vm: PlaybackPreferencesViewModel = mockk(relaxed = true) { coEvery { preferences } returns MutableStateFlow(prefs) }
        render { PlaybackSettingsScreen(onBackClick = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun subtitles() {
        val vm: SubtitleAppearancePreferencesViewModel = mockk(relaxed = true) {
            coEvery { preferences } returns MutableStateFlow(SubtitleAppearancePreferences.DEFAULT)
        }
        render { SubtitleSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        assertRendered()
    }

    private fun mediaRequestVm(admin: Boolean, configured: Boolean, test: ConnectionTestState, import: CredentialImportState) =
        mockk<MediaRequestSettingsViewModel>(relaxed = true) {
            coEvery { supportsCinefinPlugin } returns MutableStateFlow(configured)
            coEvery { isCurrentUserAdmin } returns MutableStateFlow(admin)
            coEvery { credentialImportState } returns MutableStateFlow(import)
            coEvery { allowNonAdminImports } returns MutableStateFlow(!admin)
            coEvery { isPluginConfigured } returns MutableStateFlow(configured)
            coEvery { isPluginConfigurationSupported } returns MutableStateFlow(configured)
            coEvery { seerrPreferences } returns MutableStateFlow(
                if (configured) SeerrPreferences(baseUrl = "https://seerr.test", apiKey = "k", isEnabled = true) else SeerrPreferences.DEFAULT,
            )
            coEvery { seerrTestState } returns MutableStateFlow(test)
            coEvery { sonarrPreferences } returns MutableStateFlow(
                if (configured) SonarrPreferences(baseUrl = "https://sonarr.test", apiKey = "k", isEnabled = true) else SonarrPreferences.DEFAULT,
            )
            coEvery { sonarrTestState } returns MutableStateFlow(test)
            coEvery { radarrPreferences } returns MutableStateFlow(RadarrPreferences.DEFAULT)
            coEvery { radarrTestState } returns MutableStateFlow(ConnectionTestState.Idle)
        }

    @Test
    fun mediaRequests_configuredAdmin() {
        render {
            MediaRequestSettingsScreen(
                onNavigateBack = {},
                viewModel = mediaRequestVm(true, true, ConnectionTestState.Success, CredentialImportState.Success("Imported")),
            )
        }
        assertRendered()
    }

    @Test
    fun mediaRequests_unconfiguredTesting() {
        render {
            MediaRequestSettingsScreen(onNavigateBack = {}, viewModel = mediaRequestVm(false, false, ConnectionTestState.Testing, CredentialImportState.Importing))
        }
        assertRendered()
    }

    @Test
    fun mediaRequests_failure() {
        render {
            MediaRequestSettingsScreen(
                onNavigateBack = {},
                viewModel = mediaRequestVm(true, false, ConnectionTestState.Failure("401"), CredentialImportState.Failure("Denied")),
            )
        }
        assertRendered()
    }

    @Test
    fun privacy_states() {
        val vm: PrivacySettingsViewModel = mockk(relaxed = true) {
            coEvery { state } returns MutableStateFlow(
                PrivacySecurityState(biometricEnabled = true, biometricAvailable = true, biometricWeakOnly = true, requireStrongBiometric = true, errorMessage = "Locked"),
            )
        }
        render { PrivacySettingsScreen(onNavigateBack = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun privacy_unavailable() {
        val vm: PrivacySettingsViewModel = mockk(relaxed = true) {
            coEvery { state } returns MutableStateFlow(PrivacySecurityState(isUpdatingCredentialSecurity = true))
        }
        render { PrivacySettingsScreen(onNavigateBack = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun pinning_withPins() {
        val now = 1_763_500_000_000L
        val vm: PinningSettingsViewModel = mockk(relaxed = true) {
            coEvery { pins } returns MutableStateFlow(
                listOf(
                    PinnedHostState("jellyfin.example.test", "sha256/AAAA", 1, now - 86_400_000, now + 86_400_000 * 30, false),
                    PinnedHostState("old.example.test", "sha256/BBBB", 0, now - 86_400_000 * 400, now - 86_400_000, true),
                ),
            )
            coEvery { isLoading } returns MutableStateFlow(false)
            coEvery { errorMessage } returns MutableStateFlow("Could not refresh")
        }
        render { PinningSettingsScreen(onBackClick = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun pinning_loadingEmpty() {
        val vm: PinningSettingsViewModel = mockk(relaxed = true) {
            coEvery { pins } returns MutableStateFlow(emptyList())
            coEvery { isLoading } returns MutableStateFlow(true)
            coEvery { errorMessage } returns MutableStateFlow(null)
        }
        render { PinningSettingsScreen(onBackClick = {}, viewModel = vm) }
        assertRendered()
    }

    private fun seerrVm(test: ConnectionTestState, enabled: Boolean) = mockk<SeerrSettingsViewModel>(relaxed = true) {
        coEvery { supportsCinefinPlugin } returns MutableStateFlow(enabled)
        coEvery { seerrPreferences } returns MutableStateFlow(SeerrPreferences(baseUrl = "https://seerr.test", apiKey = "abc", isEnabled = enabled))
        coEvery { isPluginConfigured } returns MutableStateFlow(enabled)
        coEvery { connectionTestState } returns MutableStateFlow(test)
    }

    @Test fun seerr_success() = render { SeerrSettingsScreen(onNavigateBack = {}, viewModel = seerrVm(ConnectionTestState.Success, true)) }.also { assertRendered() }

    @Test fun seerr_failure() = render { SeerrSettingsScreen(onNavigateBack = {}, viewModel = seerrVm(ConnectionTestState.Failure("Bad key"), false)) }.also { assertRendered() }

    @Test fun seerr_testing() = render { SeerrSettingsScreen(onNavigateBack = {}, viewModel = seerrVm(ConnectionTestState.Testing, true)) }.also { assertRendered() }

    @Test
    fun episodeNotifications_followed() {
        val vm: EpisodeNotificationSettingsViewModel = mockk(relaxed = true) {
            coEvery { preferences } returns MutableStateFlow(
                EpisodeNotificationPreferences(
                    enabled = true,
                    followedSeries = listOf(
                        FollowedSeriesNotification("s1", "The Test Series", seasonCount = 3, episodeCount = 30, lastCheckedAt = 1_763_000_000_000L),
                        FollowedSeriesNotification("s2", "Another Show"),
                    ),
                ),
            )
        }
        render { EpisodeNotificationSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        assertRendered()
    }

    @Test
    fun episodeNotifications_disabled() {
        val vm: EpisodeNotificationSettingsViewModel = mockk(relaxed = true) {
            coEvery { preferences } returns MutableStateFlow(EpisodeNotificationPreferences(enabled = false))
        }
        render { EpisodeNotificationSettingsScreen(onNavigateBack = {}, viewModel = vm) }
        assertRendered()
    }
}
