package com.rpeters.jellyfin

import android.app.Application
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JellyfinApplication : Application() {

    @Inject
    lateinit var offlineDownloadManager: OfflineDownloadManager

    companion object {
        private const val TAG = "JellyfinApplication"
    }

    override fun onCreate() {
        super.onCreate()
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
}
