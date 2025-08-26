package com.rpeters.jellyfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rpeters.jellyfin.ui.JellyfinApp
import com.rpeters.jellyfin.ui.tv.TvJellyfinApp
import com.rpeters.jellyfin.utils.DeviceTypeUtils
import com.rpeters.jellyfin.utils.ImageLoaderInitializer
import com.rpeters.jellyfin.utils.MainThreadMonitor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageLoaderInitializerEntryPoint {
    fun imageLoaderInitializer(): ImageLoaderInitializer
}

@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var imageLoaderInitializer: ImageLoaderInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start main thread monitoring in debug builds
        MainThreadMonitor.startMonitoring()

        imageLoaderInitializer = EntryPointAccessors.fromApplication(
            applicationContext,
            ImageLoaderInitializerEntryPoint::class.java,
        ).imageLoaderInitializer()
        imageLoaderInitializer.initialize()

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
