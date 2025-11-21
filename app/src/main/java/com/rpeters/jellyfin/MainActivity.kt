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
        return if (SHORTCUT_DESTINATION_PATTERN.matches(rawDestination)) {
            rawDestination
        } else {
            null
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val EXTRA_SHORTCUT_DESTINATION = "destination"
        private val SHORTCUT_DESTINATION_PATTERN = Regex("^[a-zA-Z0-9_\\-/{}]+$")
    }
}
