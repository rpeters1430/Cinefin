package com.rpeters.jellyfin.ui.screens

import com.rpeters.jellyfin.R

object SettingsRecommendationOptions {
    val appearance = listOf(
        R.string.settings_appearance_theme,
        R.string.settings_appearance_dynamic_color,
        R.string.settings_appearance_language,
        R.string.settings_appearance_layout,
    )
    val playback = listOf(
        R.string.settings_playback_quality,
        R.string.settings_playback_subtitles,
        R.string.settings_playback_autoplay,
        R.string.settings_playback_skip_intro,
    )
    val downloads = listOf(
        R.string.settings_downloads_quality,
        R.string.settings_downloads_location,
        R.string.settings_downloads_wifi_only,
        R.string.settings_downloads_cleanup,
    )
    val notifications = listOf(
        R.string.settings_notifications_library,
        R.string.settings_notifications_downloads,
        R.string.settings_notifications_playback,
    )
    val privacy = listOf(
        R.string.settings_privacy_biometric,
        R.string.settings_privacy_cache,
        R.string.settings_privacy_diagnostics,
        R.string.settings_privacy_sensitive,
    )
    val accessibility = listOf(
        R.string.settings_accessibility_text,
        R.string.settings_accessibility_motion,
        R.string.settings_accessibility_haptics,
    )
}
