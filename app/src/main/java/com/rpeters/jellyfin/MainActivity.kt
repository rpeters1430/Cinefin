package com.rpeters.jellyfin

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.rpeters.jellyfin.ui.JellyfinApp
import com.rpeters.jellyfin.ui.tv.TvJellyfinApp
import com.rpeters.jellyfin.utils.DeviceTypeUtils
import com.rpeters.jellyfin.utils.MainThreadMonitor
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.AndroidEntryPoint

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var shortcutDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start main thread monitoring in debug builds
        MainThreadMonitor.startMonitoring()

        val deviceType = MainThreadMonitor.measureMainThreadImpact("getDeviceType") {
            DeviceTypeUtils.getDeviceType(this)
        }

        SecureLogger.d(TAG, "isTvDevice=${deviceType == DeviceTypeUtils.DeviceType.TV}")

        handleShortcutIntent(intent)

        // Workaround for Compose fontWeightAdjustment crash on some API 31+ devices
        // Some OEMs don't implement Configuration.fontWeightAdjustment field properly
        applyComposeFontWeightAdjustmentWorkaround()

        setContent {
            val destination = shortcutDestination
            when (deviceType) {
                DeviceTypeUtils.DeviceType.TV -> TvJellyfinApp()
                else -> JellyfinApp(
                    initialDestination = destination,
                    onShortcutConsumed = {
                        shortcutDestination = null
                        this@MainActivity.intent?.removeExtra(EXTRA_SHORTCUT_DESTINATION)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MainThreadMonitor.stopMonitoring()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        shortcutDestination = extractShortcutDestination(intent)
        if (shortcutDestination != null) {
            SecureLogger.d(TAG, "Shortcut destination: $shortcutDestination")
        } else if (intent?.hasExtra(EXTRA_SHORTCUT_DESTINATION) == true) {
            SecureLogger.w(TAG, "Ignoring invalid shortcut destination payload")
            this.intent?.removeExtra(EXTRA_SHORTCUT_DESTINATION)
        }
    }

    private fun extractShortcutDestination(intent: Intent?): String? {
        val rawDestination = intent?.getStringExtra(EXTRA_SHORTCUT_DESTINATION) ?: return null

        // SECURITY: Validate against whitelist of allowed routes
        // Pattern matching alone is insufficient - must validate actual route existence
        if (!SHORTCUT_DESTINATION_PATTERN.matches(rawDestination)) {
            SecureLogger.w(TAG, "Invalid shortcut destination format")
            return null
        }

        // Extract base route (without parameters) for validation
        val baseRoute = rawDestination.split("/").firstOrNull() ?: return null

        // Validate against whitelist of known routes
        if (!ALLOWED_SHORTCUT_ROUTES.contains(baseRoute)) {
            SecureLogger.w(TAG, "Shortcut destination not in whitelist: $baseRoute")
            return null
        }

        return rawDestination
    }

    /**
     * Workaround for Compose fontWeightAdjustment NoSuchFieldError on some API 31+ devices
     * Some OEMs (Samsung, Xiaomi, etc.) don't implement Configuration.fontWeightAdjustment
     * even though they report API 31+. This proactively checks and logs the issue.
     */
    private fun applyComposeFontWeightAdjustmentWorkaround() {
        try {
            // Try to access the field via reflection to check if it exists
            val configClass = android.content.res.Configuration::class.java
            configClass.getField("fontWeightAdjustment")
            SecureLogger.d(TAG, "fontWeightAdjustment field is available on this device")
        } catch (e: NoSuchFieldException) {
            // Field doesn't exist - this is the problematic device
            SecureLogger.w(TAG, "fontWeightAdjustment field NOT available - Compose may crash. Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, API: ${android.os.Build.VERSION.SDK_INT}")
            SecureLogger.w(TAG, "This is a known issue with some OEM implementations of Android 12+")

            // Unfortunately, we can't prevent the crash at this point since Compose
            // initializes the font system during setContent(). The crash will still happen,
            // but at least we're logging it clearly for debugging.
            // The real fix requires Compose library update or device-specific workarounds.
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val EXTRA_SHORTCUT_DESTINATION = "destination"

        // Pattern to validate basic format (alphanumeric, dash, underscore, slash, braces)
        private val SHORTCUT_DESTINATION_PATTERN = Regex("^[a-zA-Z0-9_\\-/{}]+$")

        // SECURITY: Whitelist of allowed shortcut routes
        // Only these base routes can be navigated to via shortcuts/deep links
        private val ALLOWED_SHORTCUT_ROUTES = setOf(
            // Main screens
            "home",
            "enhanced_home",
            "library",
            "search",
            "favorites",
            "profile",
            "settings",

            // Content categories
            "movies",
            "tv_shows",
            "music",
            "home_videos",
            "books",

            // Detail screens (with parameters)
            "movie_detail",
            "episode_detail",
            "tv_seasons",
            "tv_episodes",
            "album_detail",
            "artist_detail",
            "home_video_detail",
            "item_detail",
            "stuff",

            // Auth screens (limited use)
            "server_connection",
            "quick_connect",
        )
    }
}
