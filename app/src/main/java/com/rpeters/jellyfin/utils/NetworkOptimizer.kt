package com.rpeters.jellyfin.utils

import android.app.Application
import android.content.Context
import android.net.TrafficStats
import android.os.StrictMode
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Optimizes network operations for StrictMode compliance and performance
 */
object NetworkOptimizer {
    private const val TAG = "NetworkOptimizer"
    private const val NETWORK_TAG = "jellyfin_network"

    /**
     * Initialize network optimizations for the entire application
     */
    fun initialize(application: Application) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Configure Coil image loading with proper network tagging
                setupCoilImageLoader(application)

                // Set global traffic stats tagging for untagged operations
                setupGlobalNetworkTagging()

                Log.d(TAG, "Network optimizations initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize network optimizations", e)
            }
        }
    }

    private fun setupCoilImageLoader(context: Context) {
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20) // Reduce to 20% to avoid memory pressure
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.015) // Reduce to 1.5% to prevent disk space issues
                    .cleanupDispatcher(Dispatchers.IO) // Ensure cleanup happens on background thread
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addNetworkInterceptor { chain ->
                        // Use more specific tag for image loading
                        TrafficStats.setThreadStatsTag("coil_images".hashCode())
                        try {
                            chain.proceed(chain.request())
                        } finally {
                            TrafficStats.clearThreadStatsTag()
                        }
                    }
                    .addInterceptor { chain ->
                        // Add connection keep-alive and user-agent for better caching
                        val request = chain.request().newBuilder()
                            .addHeader("Connection", "keep-alive")
                            .addHeader("User-Agent", "JellyfinAndroid-Coil/1.0.0")
                            .build()
                        chain.proceed(request)
                    }
                    .connectionPool(okhttp3.ConnectionPool(3, 3, TimeUnit.MINUTES)) // Smaller pool for images
                    .connectTimeout(10, TimeUnit.SECONDS) // Faster timeout for images
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false) // Don't retry image loads to avoid blocking
                    .build()
            }
            .crossfade(150) // Slightly faster crossfade
            .respectCacheHeaders(false) // Ignore server cache headers
            .allowRgb565(true) // Use less memory for images
            .apply {
                if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()

        // Set as default image loader
        Coil.setImageLoader(imageLoader)
    }

    private fun setupGlobalNetworkTagging() {
        // Create a thread-local tag for network operations
        val networkTag = NETWORK_TAG.hashCode()

        // This helps with ExoPlayer and other libraries that might not tag their sockets
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.w(TAG, "Uncaught exception in thread ${thread.name}", throwable)

            // Clear any lingering traffic stats tags
            try {
                TrafficStats.clearThreadStatsTag()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }

            // Call the original handler if any
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Execute network operation with proper tagging
     */
    suspend fun <T> executeTaggedNetworkOperation(
        tag: String = NETWORK_TAG,
        operation: suspend () -> T,
    ): T {
        return PerformanceOptimizer.executeNetworkOperation {
            TrafficStats.setThreadStatsTag(tag.hashCode())
            try {
                operation()
            } finally {
                TrafficStats.clearThreadStatsTag()
            }
        }
    }

    /**
     * Create a properly configured OkHttpClient for ExoPlayer data sources
     */
    fun createExoPlayerOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                TrafficStats.setThreadStatsTag("exoplayer_media".hashCode())
                try {
                    chain.proceed(chain.request())
                } finally {
                    TrafficStats.clearThreadStatsTag()
                }
            }
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Properly close network resources to prevent leaks
     */
    fun closeNetworkResources(vararg closeables: AutoCloseable?) {
        closeables.forEach { closeable ->
            try {
                closeable?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close network resource", e)
            }
        }
    }

    /**
     * Configure StrictMode for network optimizations
     * Note: Untagged socket detection disabled due to Jellyfin SDK using internal Ktor client
     */
    fun configureNetworkStrictMode() {
        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectResourceMismatches()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectActivityLeaks()
                    // Disabled: .detectUntaggedSockets() - Jellyfin SDK uses internal Ktor client
                    .penaltyLog()
                    .build(),
            )
        }
    }
}
