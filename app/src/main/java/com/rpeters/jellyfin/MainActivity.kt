package com.rpeters.jellyfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rpeters.jellyfin.ui.JellyfinApp
import com.rpeters.jellyfin.ui.tv.TvJellyfinApp
import com.rpeters.jellyfin.utils.DeviceTypeUtils
import com.rpeters.jellyfin.utils.MainThreadMonitor
import dagger.hilt.android.AndroidEntryPoint

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start main thread monitoring in debug builds
        MainThreadMonitor.startMonitoring()

        val deviceType = MainThreadMonitor.measureMainThreadImpact("getDeviceType") {
            DeviceTypeUtils.getDeviceType(this)
        }

        setContent {
            when (deviceType) {
                DeviceTypeUtils.DeviceType.TV -> TvJellyfinApp()
                else -> JellyfinApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MainThreadMonitor.stopMonitoring()
    }
}
