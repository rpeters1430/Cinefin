package com.rpeters.jellyfin.utils

import android.content.Context
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Optimizes image loading with Coil to prevent memory leaks and improve performance
 */
@OptIn(ExperimentalCoilApi::class)
object ImageLoadingOptimizer {
    private const val TAG = "ImageLoadingOptimizer"

    fun initializeCoil(context: Context, okHttpClient: OkHttpClient) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageLoader = ImageLoader.Builder(context)
                    .memoryCache {
                        MemoryCache.Builder(context)
                            .maxSizePercent(0.20) // Use 20% of available memory
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(getCacheDirectory(context))
                            .maxSizePercent(0.02) // Use 2% of available disk space
                            .cleanupDispatcher(Dispatchers.IO)
                            .build()
                    }
                    .okHttpClient {
                        okHttpClient.newBuilder()
                            .addNetworkInterceptor { chain ->
                                android.net.TrafficStats.setThreadStatsTag("coil_images".hashCode())
                                try {
                                    chain.proceed(chain.request())
                                } finally {
                                    android.net.TrafficStats.clearThreadStatsTag()
                                }
                            }
                            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                            .build()
                    }
                    .crossfade(200) // Smooth transitions
                    .respectCacheHeaders(false) // Ignore server cache headers for better control
                    .apply {
                        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                            logger(DebugLogger())
                        }
                    }
                    .build()

                // Set as singleton image loader
                Coil.setImageLoader(imageLoader)
                Log.d(TAG, "Coil image loader initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Coil image loader", e)
            }
        }
    }

    private fun getCacheDirectory(context: Context): File {
        return File(context.cacheDir, "image_cache").apply {
            mkdirs()
        }
    }

    fun clearImageCache(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Coil.imageLoader(context).memoryCache?.clear()
                Coil.imageLoader(context).diskCache?.clear()
                Log.d(TAG, "Image cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear image cache", e)
            }
        }
    }
}
