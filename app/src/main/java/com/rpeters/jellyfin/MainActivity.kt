package com.rpeters.jellyfin

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.rpeters.jellyfin.ui.JellyfinApp
import com.rpeters.jellyfin.ui.tv.TvJellyfinApp
import com.rpeters.jellyfin.utils.DeviceTypeUtils
import com.rpeters.jellyfin.utils.MainThreadMonitor
import dagger.hilt.android.AndroidEntryPoint

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start main thread monitoring in debug builds
        MainThreadMonitor.startMonitoring()

        val deviceType = MainThreadMonitor.measureMainThreadImpact("getDeviceType") {
            DeviceTypeUtils.getDeviceType(this)
        }

        Log.i("MainActivity", "isTvDevice=${deviceType == DeviceTypeUtils.DeviceType.TV}")

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
