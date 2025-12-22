package com.rpeters.jellyfin

import android.app.Application
import android.os.StrictMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.ui.surface.ModernSurfaceCoordinator
import com.rpeters.jellyfin.utils.AppResources
import com.rpeters.jellyfin.utils.NetworkOptimizer
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JellyfinApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var offlineDownloadManager: OfflineDownloadManager

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var modernSurfaceCoordinator: ModernSurfaceCoordinator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "JellyfinApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Logger with application context for file logging
        Logger.appContext = this
        AppResources.initialize(this)

        // Configure logging verbosity - disable verbose logging in production to reduce log spam
        // Set to true to enable detailed debug logging (codec detection, playback decisions, etc.)
        SecureLogger.enableVerboseLogging = BuildConfig.DEBUG && false // Disabled by default even in debug

        // Configure performance optimizations first
        initializePerformanceOptimizations()

        modernSurfaceCoordinator.initialize()

        setupUncaughtExceptionHandler()
        SecureLogger.i(TAG, "Application started")
    }

    override fun onTerminate() {
        super.onTerminate()
        cleanupResources()
        SecureLogger.i(TAG, "Application terminated")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            SecureLogger.w(TAG, "Trim memory level $level - clearing image cache")
            clearImageCaches(clearDiskCache = false)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        SecureLogger.w(TAG, "Low memory warning - aggressively cleaning up caches")
        clearImageCaches(clearDiskCache = true)
    }

    private fun clearImageCaches(clearDiskCache: Boolean) {
        try {
            val loader = SingletonImageLoader.get(this)
            loader.memoryCache?.clear()
            if (clearDiskCache) {
                loader.diskCache?.clear()
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to clear image caches", e)
        }
    }

    private fun initializePerformanceOptimizations() {
        applicationScope.launch {
            try {
                // Initialize network optimizations for StrictMode compliance
                NetworkOptimizer.initialize(this@JellyfinApplication)

                // Configure StrictMode with network optimizations
                NetworkOptimizer.configureNetworkStrictMode()

                SecureLogger.i(TAG, "Performance optimizations initialized")
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to initialize performance optimizations", e)

                // Fallback to basic StrictMode if optimizations fail
                if (BuildConfig.DEBUG) {
                    StrictMode.setThreadPolicy(
                        StrictMode.ThreadPolicy.Builder()
                            .detectDiskReads()
                            .detectDiskWrites()
                            .detectNetwork()
                            .penaltyLog()
                            .build(),
                    )
                    StrictMode.setVmPolicy(
                        StrictMode.VmPolicy.Builder()
                            .detectLeakedClosableObjects()
                            .detectUntaggedSockets()
                            .penaltyLog()
                            .build(),
                    )
                }
            }
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            SecureLogger.e(TAG, "Uncaught exception in thread ${thread.name}", exception)
            cleanupResources()
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    private fun cleanupResources() {
        try {
            // Cancel application scope to prevent leaks
            applicationScope.cancel()

            if (::offlineDownloadManager.isInitialized) {
                offlineDownloadManager.cleanup()
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error during resource cleanup", e)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader = imageLoader
}
