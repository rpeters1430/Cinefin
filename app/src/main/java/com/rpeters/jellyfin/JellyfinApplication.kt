package com.rpeters.jellyfin

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.ui.surface.ModernSurfaceCoordinator
import com.rpeters.jellyfin.utils.NetworkOptimizer
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JellyfinApplication : Application(), ImageLoaderFactory {

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

    override fun onLowMemory() {
        super.onLowMemory()
        SecureLogger.w(TAG, "Low memory warning - cleaning up caches")
        // Could trigger cache cleanup here if needed
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
            if (::offlineDownloadManager.isInitialized) {
                offlineDownloadManager.cleanup()
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error during resource cleanup", e)
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
