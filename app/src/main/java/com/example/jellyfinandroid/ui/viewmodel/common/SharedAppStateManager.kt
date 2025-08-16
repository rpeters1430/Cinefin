package com.example.jellyfinandroid.ui.viewmodel.common

import android.util.Log
import com.example.jellyfinandroid.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4: Shared State Management System
 *
 * Centralized state manager that reduces memory usage by sharing common data
 * across ViewModels and provides intelligent caching and state synchronization.
 */
@Singleton
class SharedAppStateManager @Inject constructor() {

    companion object {
        private const val TAG = "SharedAppStateManager"
        private const val MAX_CACHED_ITEMS = 5000
        private const val MAX_MEMORY_CACHE_SIZE = 50 * 1024 * 1024 // 50MB
    }

    // Concurrent maps for thread-safe access
    private val itemCache = ConcurrentHashMap<String, BaseItemDto>()
    private val libraryCache = ConcurrentHashMap<String, List<BaseItemDto>>()
    private val imageUrlCache = ConcurrentHashMap<String, String>()

    // State flows for reactive updates
    private val _sharedItems = MutableStateFlow<Map<String, BaseItemDto>>(emptyMap())
    val sharedItems: StateFlow<Map<String, BaseItemDto>> = _sharedItems.asStateFlow()

    private val _libraries = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val libraries: StateFlow<List<BaseItemDto>> = _libraries.asStateFlow()

    private val _recentlyViewed = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val recentlyViewed: StateFlow<List<BaseItemDto>> = _recentlyViewed.asStateFlow()

    private val _favorites = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val favorites: StateFlow<List<BaseItemDto>> = _favorites.asStateFlow()

    // Performance metrics
    private val _memoryUsage = MutableStateFlow(0L)
    val memoryUsage: StateFlow<Long> = _memoryUsage.asStateFlow()

    private val _cacheHitRate = MutableStateFlow(0f)
    val cacheHitRate: StateFlow<Float> = _cacheHitRate.asStateFlow()

    // Synchronization
    private val stateMutex = Mutex()
    private var cacheHits = 0L
    private var cacheMisses = 0L

    /**
     * Add or update an item in the shared cache
     */
    suspend fun addOrUpdateItem(item: BaseItemDto) {
        stateMutex.withLock {
            val itemId = item.id?.toString() ?: return

            itemCache[itemId] = item

            // Update state flow
            _sharedItems.value = itemCache.toMap()

            // Manage memory
            manageMemory()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Added/Updated item: ${item.name} (ID: $itemId)")
            }
        }
    }

    /**
     * Add multiple items efficiently
     */
    suspend fun addOrUpdateItems(items: List<BaseItemDto>) {
        stateMutex.withLock {
            items.forEach { item ->
                val itemId = item.id?.toString()
                if (itemId != null) {
                    itemCache[itemId] = item
                }
            }

            // Single state update for better performance
            _sharedItems.value = itemCache.toMap()
            manageMemory()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Added/Updated ${items.size} items")
            }
        }
    }

    /**
     * Get an item by ID with cache tracking
     */
    fun getItem(itemId: String): BaseItemDto? {
        val item = itemCache[itemId]

        if (item != null) {
            cacheHits++
        } else {
            cacheMisses++
        }

        updateCacheHitRate()
        return item
    }

    /**
     * Get items by type with filtering
     */
    fun getItemsByType(type: org.jellyfin.sdk.model.api.BaseItemKind): List<BaseItemDto> {
        return itemCache.values.filter { it.type == type }
    }

    /**
     * Update libraries cache
     */
    suspend fun updateLibraries(libraries: List<BaseItemDto>) {
        stateMutex.withLock {
            _libraries.value = libraries

            // Cache libraries by ID for quick access
            libraries.forEach { library ->
                val libraryId = library.id?.toString()
                if (libraryId != null) {
                    libraryCache[libraryId] = listOf(library)
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Updated ${libraries.size} libraries")
            }
        }
    }

    /**
     * Update recently viewed items
     */
    suspend fun updateRecentlyViewed(items: List<BaseItemDto>) {
        stateMutex.withLock {
            _recentlyViewed.value = items.take(20) // Limit to 20 recent items

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Updated ${items.size} recently viewed items")
            }
        }
    }

    /**
     * Update favorites
     */
    suspend fun updateFavorites(items: List<BaseItemDto>) {
        stateMutex.withLock {
            _favorites.value = items

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Updated ${items.size} favorite items")
            }
        }
    }

    /**
     * Cache image URL for an item
     */
    fun cacheImageUrl(itemId: String, imageUrl: String) {
        imageUrlCache[itemId] = imageUrl
    }

    /**
     * Get cached image URL
     */
    fun getCachedImageUrl(itemId: String): String? {
        return imageUrlCache[itemId]
    }

    /**
     * Search items in cache
     */
    fun searchItems(query: String): List<BaseItemDto> {
        if (query.isBlank()) return emptyList()

        val lowercaseQuery = query.lowercase()
        return itemCache.values.filter { item ->
            item.name?.lowercase()?.contains(lowercaseQuery) == true ||
                item.overview?.lowercase()?.contains(lowercaseQuery) == true ||
                item.genres?.any { it.lowercase().contains(lowercaseQuery) } == true
        }.take(50) // Limit search results
    }

    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): Map<String, Any> {
        return mapOf(
            "totalItems" to itemCache.size,
            "memoryUsageBytes" to _memoryUsage.value,
            "cacheHitRate" to _cacheHitRate.value,
            "totalLibraries" to _libraries.value.size,
            "recentlyViewedCount" to _recentlyViewed.value.size,
            "favoritesCount" to _favorites.value.size,
        )
    }

    /**
     * Clear all cached data
     */
    suspend fun clearCache() {
        stateMutex.withLock {
            itemCache.clear()
            libraryCache.clear()
            imageUrlCache.clear()

            _sharedItems.value = emptyMap()
            _libraries.value = emptyList()
            _recentlyViewed.value = emptyList()
            _favorites.value = emptyList()
            _memoryUsage.value = 0L

            cacheHits = 0L
            cacheMisses = 0L
            _cacheHitRate.value = 0f

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cache cleared")
            }
        }
    }

    /**
     * Remove items older than specified time
     */
    suspend fun cleanupOldItems(maxAgeMs: Long = 24 * 60 * 60 * 1000) { // 24 hours
        stateMutex.withLock {
            val currentTime = System.currentTimeMillis()
            val initialSize = itemCache.size

            // Remove old items (this is simplified - in real implementation you'd track timestamp)
            // For now, just manage by size
            manageMemory()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cleanup completed. Removed ${initialSize - itemCache.size} items")
            }
        }
    }

    /**
     * Manage memory usage by removing least recently used items
     */
    private fun manageMemory() {
        if (itemCache.size > MAX_CACHED_ITEMS) {
            // Simple LRU - remove oldest entries
            val itemsToRemove = itemCache.size - (MAX_CACHED_ITEMS * 0.8).toInt()
            val keysToRemove = itemCache.keys.take(itemsToRemove)

            keysToRemove.forEach { key ->
                itemCache.remove(key)
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Memory management: removed $itemsToRemove items")
            }
        }

        // Estimate memory usage (simplified calculation)
        val estimatedMemory = itemCache.size * 2048L // Rough estimate per item
        _memoryUsage.value = estimatedMemory
    }

    /**
     * Update cache hit rate
     */
    private fun updateCacheHitRate() {
        val totalRequests = cacheHits + cacheMisses
        if (totalRequests > 0) {
            _cacheHitRate.value = cacheHits.toFloat() / totalRequests.toFloat()
        }
    }

    /**
     * Preload items for better performance
     */
    suspend fun preloadItems(items: List<BaseItemDto>) {
        addOrUpdateItems(items)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Preloaded ${items.size} items for better performance")
        }
    }
}
