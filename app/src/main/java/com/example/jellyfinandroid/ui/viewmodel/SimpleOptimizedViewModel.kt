package com.example.jellyfinandroid.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.jellyfinandroid.data.repository.JellyfinMediaRepository
import com.example.jellyfinandroid.data.repository.common.ApiResult
import com.example.jellyfinandroid.ui.viewmodel.common.BaseJellyfinViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * Phase 4: Simple Optimized ViewModel Demonstration
 *
 * A simplified demonstration of Phase 4 concepts that actually builds:
 * - Uses existing BaseJellyfinViewModel for error handling
 * - Demonstrates performance-aware loading
 * - Shows memory optimization patterns
 */
@HiltViewModel
class SimpleOptimizedViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
) : BaseJellyfinViewModel() {

    // Optimized state management
    private val _optimizedData = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val optimizedData: StateFlow<List<BaseItemDto>> = _optimizedData.asStateFlow()

    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()

    // In-memory cache for demonstration
    private val itemCache = mutableMapOf<String, BaseItemDto>()

    data class PerformanceMetrics(
        val cacheHits: Int = 0,
        val apiCalls: Int = 0,
        val memoryUsage: Long = 0L,
        val averageLoadTime: Long = 0L,
    )

    init {
        loadOptimizedData()
    }

    /**
     * Load data with performance optimizations
     */
    fun loadOptimizedData() {
        executeOperation(
            operationName = "loadOptimizedData",
            operation = {
                val startTime = System.currentTimeMillis()

                // Increment API call counter
                updateMetrics { it.copy(apiCalls = it.apiCalls + 1) }

                // Get libraries first (this uses existing, working code)
                when (val result = mediaRepository.getUserLibraries()) {
                    is ApiResult.Success -> {
                        val loadTime = System.currentTimeMillis() - startTime

                        // Cache items for future use
                        result.data.forEach { item ->
                            val itemId = item.id?.toString()
                            if (itemId != null) {
                                itemCache[itemId] = item
                            }
                        }

                        // Update performance metrics
                        updateMetrics { metrics ->
                            metrics.copy(
                                averageLoadTime = (metrics.averageLoadTime + loadTime) / 2,
                                memoryUsage = estimateMemoryUsage(),
                            )
                        }

                        ApiResult.Success(result.data)
                    }
                    is ApiResult.Error -> result
                    is ApiResult.Loading -> ApiResult.Loading()
                }
            },
            onSuccess = { libraries ->
                _optimizedData.value = libraries
            },
        )
    }

    /**
     * Get item from cache (demonstrates performance optimization)
     */
    fun getItemFromCache(itemId: String): BaseItemDto? {
        val item = itemCache[itemId]
        if (item != null) {
            // Cache hit - update metrics
            updateMetrics { it.copy(cacheHits = it.cacheHits + 1) }
        }
        return item
    }

    /**
     * Smart refresh based on conditions
     */
    fun smartRefresh() {
        viewModelScope.launch {
            val metrics = _performanceMetrics.value

            // Only refresh if performance is good and cache hit rate is low
            if (metrics.averageLoadTime < 2000 && metrics.cacheHits < metrics.apiCalls) {
                loadOptimizedData()
            }
        }
    }

    /**
     * Demonstrate adaptive behavior based on performance
     */
    fun getAdaptivePageSize(): Int {
        val avgLoadTime = _performanceMetrics.value.averageLoadTime
        return when {
            avgLoadTime > 3000 -> 10 // Slow connection - smaller pages
            avgLoadTime > 1000 -> 20 // Medium connection
            else -> 50 // Fast connection - larger pages
        }
    }

    /**
     * Get performance recommendations
     */
    fun getPerformanceRecommendations(): List<String> {
        val metrics = _performanceMetrics.value
        val recommendations = mutableListOf<String>()

        if (metrics.averageLoadTime > 2000) {
            recommendations.add("Consider reducing image quality for better performance")
        }

        if (metrics.cacheHits < metrics.apiCalls * 0.5) {
            recommendations.add("Cache hit rate is low - consider preloading content")
        }

        if (metrics.memoryUsage > 50 * 1024 * 1024) { // 50MB
            recommendations.add("High memory usage detected - consider clearing cache")
        }

        return recommendations
    }

    /**
     * Clear cache to free memory
     */
    fun clearCache() {
        itemCache.clear()
        updateMetrics { it.copy(memoryUsage = 0L, cacheHits = 0) }
    }

    /**
     * Update metrics safely
     */
    private fun updateMetrics(update: (PerformanceMetrics) -> PerformanceMetrics) {
        _performanceMetrics.value = update(_performanceMetrics.value)
    }

    /**
     * Estimate memory usage (simplified)
     */
    private fun estimateMemoryUsage(): Long {
        return itemCache.size * 2048L // Rough estimate per item
    }

    /**
     * Get optimization summary
     */
    fun getOptimizationSummary(): Map<String, Any> {
        val metrics = _performanceMetrics.value
        return mapOf(
            "cachedItems" to itemCache.size,
            "cacheHitRate" to if (metrics.apiCalls > 0) {
                (metrics.cacheHits.toFloat() / metrics.apiCalls.toFloat() * 100).toInt()
            } else {
                0
            },
            "averageLoadTimeMs" to metrics.averageLoadTime,
            "memoryUsageMB" to metrics.memoryUsage / 1024 / 1024,
            "adaptivePageSize" to getAdaptivePageSize(),
            "recommendations" to getPerformanceRecommendations(),
        )
    }
}
