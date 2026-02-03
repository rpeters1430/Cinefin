package com.rpeters.jellyfin

import android.app.Application
import android.os.SystemClock
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.utils.RepositoryUtils
import com.rpeters.jellyfin.ui.surface.ModernSurfaceCoordinator
import com.rpeters.jellyfin.utils.AppResources
import com.rpeters.jellyfin.utils.NetworkOptimizer
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class JellyfinApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var offlineDownloadManager: OfflineDownloadManager

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var modernSurfaceCoordinator: ModernSurfaceCoordinator

    @Inject
    lateinit var authRepository: JellyfinAuthRepository

    @Inject
    lateinit var generativeAiRepository: com.rpeters.jellyfin.data.repository.GenerativeAiRepository

    private val applicationJob = SupervisorJob()
    private val applicationScope = CoroutineScope(applicationJob + Dispatchers.Default)
    private val authRecoveryLock = Any()
    private var lastAuthRecoveryAttemptMs = 0L

    companion object {
        private const val TAG = "JellyfinApplication"
        private const val AUTH_RECOVERY_COOLDOWN_MS = 30_000L
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Logger with application context for file logging
        Logger.appContext = this
        AppResources.initialize(this)

        // Configure logging verbosity - disable verbose logging in production to reduce log spam
        // Set to true to enable detailed debug logging (codec detection, playback decisions, etc.)
        SecureLogger.enableVerboseLogging = BuildConfig.DEBUG && true // Enabled for debugging transcoding issues

        // Initialize Firebase App Check (debug mode for testing without Play Store)
        initializeAppCheck()

        // Configure performance optimizations first
        initializePerformanceOptimizations()

        modernSurfaceCoordinator.initialize()

        // Initialize AI in background to check Nano availability early
        initializeAiBackend()

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
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Initializes Firebase App Check
     * - Debug builds: Tries to use DebugAppCheckProviderFactory, falls back to Play Integrity
     * - Release builds: Uses Play Integrity API
     */
    private fun initializeAppCheck() {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            // Debug mode: Try to use debug provider (requires firebase-appcheck-debug dependency)
            try {
                val debugProviderClass = Class.forName(
                    "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory",
                )
                val getInstance = debugProviderClass.getMethod("getInstance")
                val debugProvider = getInstance.invoke(null)

                firebaseAppCheck.installAppCheckProviderFactory(
                    debugProvider as com.google.firebase.appcheck.AppCheckProviderFactory,
                )
                SecureLogger.i(TAG, "Firebase App Check initialized with DEBUG provider")
                SecureLogger.i(TAG, "Check logcat for 'DebugAppCheckProvider' to find your debug token")
            } catch (e: ClassNotFoundException) {
                // Debug provider not available, use Play Integrity
                firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance(),
                )
                SecureLogger.i(TAG, "Firebase App Check initialized with Play Integrity (debug build, debug provider not available)")
            }
        } else {
            // Release mode: Use Play Integrity
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
            SecureLogger.i(TAG, "Firebase App Check initialized with Play Integrity")
        }
    }

    private fun initializePerformanceOptimizations() {
        applicationScope.launch {
            try {
                // Initialize network optimizations off the main thread
                NetworkOptimizer.initialize(this@JellyfinApplication)

                // Configure StrictMode on the main thread to ensure it applies correctly
                withContext(Dispatchers.Main) {
                    NetworkOptimizer.configureNetworkStrictMode()
                }

                SecureLogger.i(TAG, "Performance optimizations initialized")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun initializeAiBackend() {
        applicationScope.launch {
            try {
                // Trigger AI initialization by checking backend status
                // This will start Nano download if available or fallback to cloud
                SecureLogger.i(TAG, "Initializing AI backend in background")
                val isOnDevice = generativeAiRepository.isUsingOnDeviceAI()
                SecureLogger.i(TAG, "AI backend initialized: ${if (isOnDevice) "On-Device (Nano)" else "Cloud (API)"}")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            if (isAuth401Exception(exception)) {
                SecureLogger.w(TAG, "Suppressed fatal 401 auth exception on thread ${thread.name}", exception)
                scheduleAuthRecovery()
                return@setDefaultUncaughtExceptionHandler
            }
            // Handle NoSuchFieldError for fontWeightAdjustment field (Compose compatibility issue)
            // This occurs on certain devices where Configuration.fontWeightAdjustment doesn't exist
            if (isFontWeightAdjustmentError(exception)) {
                SecureLogger.e(TAG, "Compose fontWeightAdjustment compatibility error detected", exception)
                SecureLogger.e(TAG, "This is a known Compose issue on certain devices. Please update the app.")
                // Still crash, but with better logging for Firebase
            }
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
            applicationJob.cancel()
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun isAuth401Exception(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is CancellationException) return false
            if (current is Exception && RepositoryUtils.is401Error(current)) return true
            current = current.cause
        }
        return false
    }

    /**
     * Checks if the exception is the known Compose fontWeightAdjustment NoSuchFieldError
     * This occurs on devices where Configuration.fontWeightAdjustment doesn't exist
     */
    private fun isFontWeightAdjustmentError(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is NoSuchFieldError &&
                current.message?.contains("fontWeightAdjustment") == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun scheduleAuthRecovery() {
        if (!::authRepository.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        synchronized(authRecoveryLock) {
            if (now - lastAuthRecoveryAttemptMs < AUTH_RECOVERY_COOLDOWN_MS) return
            lastAuthRecoveryAttemptMs = now
        }

        applicationScope.launch(Dispatchers.IO) {
            try {
                SecureLogger.w(TAG, "Global 401 handler: forcing re-authentication")
                val success = authRepository.forceReAuthenticate()
                if (success) {
                    SecureLogger.i(TAG, "Global 401 handler: re-authentication successful")
                } else {
                    SecureLogger.w(TAG, "Global 401 handler: re-authentication failed")
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader = imageLoader
}
