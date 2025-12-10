package com.rpeters.jellyfin.utils

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Optimizes image loading with Coil to prevent memory leaks and improve performance
 */
@OptIn(ExperimentalCoilApi::class)
object ImageLoadingOptimizer {
    private const val TAG = "ImageLoadingOptimizer"

    fun initializeCoil(context: Context, okHttpClient: OkHttpClient) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val performanceProfile = DevicePerformanceProfile.detect(context)
                val imageHttpClient = okHttpClient.newBuilder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Accept", "image/webp,image/avif,image/*,*/*;q=0.8")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val imageLoader = ImageLoader.Builder(context)
                    .memoryCache {
                        MemoryCache.Builder()
                            .maxSizePercent(context, performanceProfile.memoryCachePercent)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                            .maxSizeBytes(performanceProfile.diskCacheSizeMb * 1024 * 1024)
                            .cleanupCoroutineContext(Dispatchers.IO)
                            .build()
                    }
                    .components {
                        add(
                            OkHttpNetworkFetcherFactory(
                                callFactory = { imageHttpClient },
                            ),
                        )
                    }
                    // Coil 3.x: crossfade, respectCacheHeaders, allowRgb565, allowHardware
                    // are now request-level options, not builder options
                    .apply {
                        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
                            logger(DebugLogger())
                        }
                    }
                    .build()

                // Set as singleton image loader
                SingletonImageLoader.setSafe { imageLoader }
                Log.d(TAG, "Coil image loader initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Coil image loader", e)
            }
        }
    }

    // Coil 3.x: Cache directory is now handled by resolve()
    // This function is no longer needed but kept for backward compatibility
    private fun getCacheDirectory(context: Context): File {
        return context.cacheDir.resolve("image_cache").apply {
            mkdirs()
        }
    }

    fun clearImageCache(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SingletonImageLoader.get(context).memoryCache?.clear()
                SingletonImageLoader.get(context).diskCache?.clear()
                Log.d(TAG, "Image cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear image cache", e)
            }
        }
    }
}
