package com.rpeters.jellyfin

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

        Log.i("MainActivity", "isTvDevice=${deviceType == DeviceTypeUtils.DeviceType.TV}")

        // Handle app shortcuts by extracting the destination extra from the intent
        shortcutDestination = intent?.getStringExtra("destination")
        if (shortcutDestination != null) {
            Log.d("MainActivity", "Shortcut destination: $shortcutDestination")
        }

        setContent {
            val destination = shortcutDestination
            when (deviceType) {
                DeviceTypeUtils.DeviceType.TV -> TvJellyfinApp()
                else -> JellyfinApp(
                    initialDestination = destination,
                    onShortcutConsumed = { shortcutDestination = null },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MainThreadMonitor.stopMonitoring()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutDestination = intent?.getStringExtra("destination")
        if (shortcutDestination != null) {
            Log.d("MainActivity", "New shortcut destination: $shortcutDestination")
        }
    }
}
