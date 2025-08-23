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
                            .maxSizePercent(0.15) // Reduce to 15% to prevent memory issues
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(getCacheDirectory(context))
                            .maxSizePercent(0.015) // Reduce to 1.5% to prevent disk issues
                            .cleanupDispatcher(Dispatchers.IO)
                            .build()
                    }
                    .okHttpClient {
                        // Create a separate client for Coil to avoid conflicts
                        okHttpClient.newBuilder()
                            .addNetworkInterceptor { chain ->
                                // Use unique thread ID to avoid conflicts with other network operations
                                val uniqueTag = "coil_${Thread.currentThread().hashCode()}".hashCode()
                                android.net.TrafficStats.setThreadStatsTag(uniqueTag)
                                try {
                                    chain.proceed(chain.request())
                                } finally {
                                    android.net.TrafficStats.clearThreadStatsTag()
                                }
                            }
                            .addInterceptor { chain ->
                                // Optimize for image loading
                                val request = chain.request().newBuilder()
                                    .addHeader("Connection", "keep-alive")
                                    .addHeader("User-Agent", "JellyfinAndroid-Images/1.0.0")
                                    .addHeader("Accept", "image/webp,image/avif,image/*,*/*;q=0.8")
                                    .build()
                                chain.proceed(request)
                            }
                            .connectionPool(okhttp3.ConnectionPool(3, 3, TimeUnit.MINUTES))
                            .connectTimeout(8, TimeUnit.SECONDS) // Faster timeout for images
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(8, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(false) // Don't retry to avoid blocking
                            .build()
                    }
                    .crossfade(100) // Fast crossfade
                    .respectCacheHeaders(false) // Ignore server cache headers for better control
                    .allowRgb565(true) // Use less memory per image
                    .allowHardware(true) // Use hardware bitmaps when possible
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
