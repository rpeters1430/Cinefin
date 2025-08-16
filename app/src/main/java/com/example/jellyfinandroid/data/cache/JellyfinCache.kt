package com.example.jellyfinandroid.data.cache

import android.content.Context
import android.util.Log
import com.example.jellyfinandroid.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent caching system for Jellyfin content to support offline functionality.
 * Part of Phase 2: Enhanced Error Handling & User Experience improvements.
 */
@Singleton
class JellyfinCache @Inject constructor(
    private val context: Context,
) {

    companion object {
        private const val TAG = "JellyfinCache"
        private const val CACHE_DIR = "jellyfin_cache"
        private const val MAX_CACHE_SIZE_MB = 100L // 100 MB cache limit
        private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val RECENTLY_ADDED_KEY = "recently_added"
        private const val LIBRARIES_KEY = "libraries"
        private const val FAVORITES_KEY = "favorites"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // In-memory cache for quick access
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<*>>()

    // Cache statistics
    private val _cacheStats = MutableStateFlow(CacheStats())
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    init {
        // Clean up old cache entries on initialization
        cleanupOldEntries()
        updateCacheStats()
    }

    /**
     * Caches a list of BaseItemDto objects with TTL.
     */
    suspend fun cacheItems(
        key: String,
        items: List<BaseItemDto>,
        ttlMs: Long = MAX_CACHE_AGE_MS,
    ): Boolean {
        return try {
            val cacheData = CacheData(
                items = items,
                timestamp = System.currentTimeMillis(),
                ttlMs = ttlMs,
            )

            val cacheEntry = CacheEntry(
                data = cacheData,
                expiresAt = System.currentTimeMillis() + ttlMs,
            )

            // Store in memory cache
            memoryCache[key] = cacheEntry

            // Store on disk
            val file = File(cacheDir, "$key.json")
            file.writeText(json.encodeToString(CacheData.serializer(), cacheData))

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cached ${items.size} items with key: $key")
            }

            updateCacheStats()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache items for key: $key", e)
            false
        }
    }

    /**
     * Retrieves cached items if they exist and are still valid.
     */
    suspend fun getCachedItems(key: String): List<BaseItemDto>? {
        return try {
            // Check memory cache first
            memoryCache[key]?.let { entry ->
                if (entry.isValid()) {
                    @Suppress("UNCHECKED_CAST")
                    val cacheData = entry.data as? CacheData
                    if (cacheData != null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Memory cache hit for key: $key")
                        }
                        return cacheData.items
                    }
                } else {
                    // Remove expired entry
                    memoryCache.remove(key)
                }
            }

            // Check disk cache
            val file = File(cacheDir, "$key.json")
            if (file.exists()) {
                val cacheData = json.decodeFromString<CacheData>(file.readText())
                val isValid = (System.currentTimeMillis() - cacheData.timestamp) < cacheData.ttlMs

                if (isValid) {
                    // Add back to memory cache
                    val cacheEntry = CacheEntry(
                        data = cacheData,
                        expiresAt = cacheData.timestamp + cacheData.ttlMs,
                    )
                    memoryCache[key] = cacheEntry

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Disk cache hit for key: $key")
                    }
                    return cacheData.items
                } else {
                    // Delete expired file
                    file.delete()
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Deleted expired cache file for key: $key")
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve cached items for key: $key", e)
            null
        }
    }

    /**
     * Checks if cached data exists and is valid for a given key.
     */
    fun isCached(key: String): Boolean {
        // Check memory cache
        memoryCache[key]?.let { entry ->
            if (entry.isValid()) {
                return true
            } else {
                memoryCache.remove(key)
            }
        }

        // Check disk cache
        val file = File(cacheDir, "$key.json")
        if (file.exists()) {
            try {
                val cacheData = json.decodeFromString<CacheData>(file.readText())
                val isValid = (System.currentTimeMillis() - cacheData.timestamp) < cacheData.ttlMs
                return isValid
            } catch (e: Exception) {
                Log.e(TAG, "Error checking cache validity for key: $key", e)
                // Delete corrupted file
                file.delete()
            }
        }

        return false
    }

    /**
     * Invalidates cache for a specific key.
     */
    suspend fun invalidateCache(key: String) {
        memoryCache.remove(key)
        val file = File(cacheDir, "$key.json")
        if (file.exists()) {
            file.delete()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Invalidated cache for key: $key")
            }
        }
        updateCacheStats()
    }

    /**
     * Clears all cached data.
     */
    suspend fun clearAllCache() {
        memoryCache.clear()

        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".json")) {
                file.delete()
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Cleared all cache")
        }
        updateCacheStats()
    }

    /**
     * Gets the size of cached data in bytes.
     */
    fun getCacheSizeBytes(): Long {
        return try {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
            0L
        }
    }

    /**
     * Cleans up expired cache entries.
     */
    private fun cleanupOldEntries() {
        try {
            val currentTime = System.currentTimeMillis()

            // Clean memory cache
            val expiredKeys = memoryCache.entries
                .filter { !it.value.isValid() }
                .map { it.key }

            expiredKeys.forEach { key ->
                memoryCache.remove(key)
            }

            // Clean disk cache
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    try {
                        val cacheData = json.decodeFromString<CacheData>(file.readText())
                        val isExpired = (currentTime - cacheData.timestamp) >= cacheData.ttlMs

                        if (isExpired) {
                            file.delete()
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Deleted expired cache file: ${file.name}")
                            }
                        }
                    } catch (e: Exception) {
                        // Delete corrupted files
                        file.delete()
                        Log.w(TAG, "Deleted corrupted cache file: ${file.name}")
                    }
                }
            }

            // Check if we need to free up space
            val cacheSize = getCacheSizeBytes()
            val maxCacheSizeBytes = MAX_CACHE_SIZE_MB * 1024 * 1024

            if (cacheSize > maxCacheSizeBytes) {
                evictOldestEntries(maxCacheSizeBytes)
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cache cleanup completed. Removed ${expiredKeys.size} expired entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
        }
    }

    /**
     * Evicts oldest cache entries to stay within size limit.
     */
    private fun evictOldestEntries(maxSizeBytes: Long) {
        try {
            val files = cacheDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.sortedBy { it.lastModified() } // Oldest first
                ?: return

            var currentSize = files.sumOf { it.length() }
            var deletedCount = 0

            for (file in files) {
                if (currentSize <= maxSizeBytes) break

                currentSize -= file.length()
                file.delete()
                deletedCount++

                // Remove from memory cache too
                val key = file.nameWithoutExtension
                memoryCache.remove(key)
            }

            if (BuildConfig.DEBUG && deletedCount > 0) {
                Log.d(TAG, "Evicted $deletedCount cache entries to stay within size limit")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache eviction", e)
        }
    }

    /**
     * Updates cache statistics.
     */
    private fun updateCacheStats() {
        try {
            val diskEntries = cacheDir.listFiles()
                ?.count { it.isFile && it.name.endsWith(".json") } ?: 0

            val memoryEntries = memoryCache.size
            val totalSizeBytes = getCacheSizeBytes()
            val totalSizeMB = totalSizeBytes / (1024.0 * 1024.0)

            _cacheStats.value = CacheStats(
                totalEntries = diskEntries,
                memoryEntries = memoryEntries,
                totalSizeBytes = totalSizeBytes,
                totalSizeMB = totalSizeMB,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cache stats", e)
        }
    }

    /**
     * Convenient methods for common cache operations.
     */

    suspend fun cacheRecentlyAdded(items: List<BaseItemDto>) {
        cacheItems(RECENTLY_ADDED_KEY, items, ttlMs = 30 * 60 * 1000L) // 30 minutes
    }

    suspend fun getCachedRecentlyAdded(): List<BaseItemDto>? {
        return getCachedItems(RECENTLY_ADDED_KEY)
    }

    suspend fun cacheLibraries(items: List<BaseItemDto>) {
        cacheItems(LIBRARIES_KEY, items, ttlMs = 60 * 60 * 1000L) // 1 hour
    }

    suspend fun getCachedLibraries(): List<BaseItemDto>? {
        return getCachedItems(LIBRARIES_KEY)
    }

    suspend fun cacheFavorites(items: List<BaseItemDto>) {
        cacheItems(FAVORITES_KEY, items, ttlMs = 10 * 60 * 1000L) // 10 minutes
    }

    suspend fun getCachedFavorites(): List<BaseItemDto>? {
        return getCachedItems(FAVORITES_KEY)
    }
}

/**
 * Data classes for cache implementation.
 */

@Serializable
private data class CacheData(
    val items: List<BaseItemDto>,
    val timestamp: Long,
    val ttlMs: Long,
)

private data class CacheEntry<T>(
    val data: T,
    val expiresAt: Long,
) {
    fun isValid(): Boolean = System.currentTimeMillis() < expiresAt
}

data class CacheStats(
    val totalEntries: Int = 0,
    val memoryEntries: Int = 0,
    val totalSizeBytes: Long = 0L,
    val totalSizeMB: Double = 0.0,
)
