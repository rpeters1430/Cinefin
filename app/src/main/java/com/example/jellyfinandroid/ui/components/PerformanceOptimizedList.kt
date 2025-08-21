package com.example.jellyfinandroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.ui.image.rememberImagePreloader
import com.example.jellyfinandroid.utils.PerformanceMonitor
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Performance-optimized LazyColumn with automatic item virtualization and memory management.
 */
@Composable
fun PerformanceOptimizedLazyColumn(
    items: List<BaseItemDto>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxVisibleItems: Int = 100,
    preloadOffset: Int = 10,
    content: @Composable (item: BaseItemDto, index: Int, isVisible: Boolean) -> Unit,
) {
    // Limit items for performance
    val optimizedItems = remember(items) {
        if (items.size > maxVisibleItems) {
            items.take(maxVisibleItems)
        } else {
            items
        }
    }

    // Track visible range for performance optimization
    val firstVisibleIndex by remember {
        derivedStateOf { state.firstVisibleItemIndex }
    }

    val visibleItemCount by remember {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.size }
    }

    val visibleRange = remember(firstVisibleIndex, visibleItemCount) {
        val start = (firstVisibleIndex - preloadOffset).coerceAtLeast(0)
        val end = (firstVisibleIndex + visibleItemCount + preloadOffset).coerceAtMost(optimizedItems.size)
        start until end
    }

    // Performance monitoring
    LaunchedEffect(optimizedItems.size) {
        if (optimizedItems.size != items.size) {
            PerformanceMonitor.measureExecutionTime("ItemLimiting") {
                android.util.Log.d(
                    "PerformanceOptimizedList",
                    "Limited ${items.size} items to ${optimizedItems.size} for performance",
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
    ) {
        itemsIndexed(
            items = optimizedItems,
            key = { _, item -> item.id?.toString() ?: "" },
        ) { index, item ->
            val isVisible = index in visibleRange
            content(item, index, isVisible)
        }
    }
}

/**
 * Performance-optimized LazyRow with automatic image preloading.
 */
@Composable
fun PerformanceOptimizedLazyRow(
    items: List<BaseItemDto>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    maxVisibleItems: Int = 50,
    preloadImages: Boolean = true,
    getImageUrl: ((BaseItemDto) -> String?)? = null,
    content: @Composable (item: BaseItemDto, index: Int, isVisible: Boolean) -> Unit,
) {
    // Limit items for performance
    val optimizedItems = remember(items) {
        if (items.size > maxVisibleItems) {
            items.take(maxVisibleItems)
        } else {
            items
        }
    }

    // Track visible range
    val firstVisibleIndex by remember {
        derivedStateOf { state.firstVisibleItemIndex }
    }

    val visibleItemCount by remember {
        derivedStateOf { state.layoutInfo.visibleItemsInfo.size }
    }

    val visibleRange = remember(firstVisibleIndex, visibleItemCount) {
        val start = firstVisibleIndex.coerceAtLeast(0)
        val end = (firstVisibleIndex + visibleItemCount + 5).coerceAtMost(optimizedItems.size)
        start until end
    }

    // Image preloading
    val imagePreloader = rememberImagePreloader()
    LaunchedEffect(optimizedItems, preloadImages) {
        if (preloadImages && getImageUrl != null) {
            val imagesToPreload = optimizedItems.take(10).mapNotNull(getImageUrl)
            imagePreloader.preloadImages(imagesToPreload)
        }
    }

    LazyRow(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
    ) {
        itemsIndexed(
            items = optimizedItems,
            key = { _, item -> item.id?.toString() ?: "" },
        ) { index, item ->
            val isVisible = index in visibleRange
            content(item, index, isVisible)
        }
    }
}

/**
 * Performance-optimized LazyVerticalGrid with automatic item management.
 */
@Composable
fun PerformanceOptimizedLazyGrid(
    items: List<BaseItemDto>,
    columns: GridCells,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    maxVisibleItems: Int = 200,
    content: @Composable (item: BaseItemDto, index: Int) -> Unit,
) {
    // Limit items for performance
    val optimizedItems = remember(items) {
        if (items.size > maxVisibleItems) {
            items.take(maxVisibleItems)
        } else {
            items
        }
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        itemsIndexed(
            items = optimizedItems,
            key = { _, item -> item.id?.toString() ?: "" },
        ) { index, item ->
            content(item, index)
        }
    }
}

/**
 * Adaptive performance optimization based on device capabilities.
 */
@Composable
fun rememberPerformanceSettings(): PerformanceSettings {
    return remember {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / 1024 / 1024
        val availableProcessors = runtime.availableProcessors()

        when {
            maxMemoryMB > 512 && availableProcessors >= 8 -> {
                // High-end device
                PerformanceSettings(
                    maxVisibleItems = 200,
                    imageQuality = com.example.jellyfinandroid.ui.image.ImageQuality.HIGH,
                    enableImagePreloading = true,
                    enableAnimations = true,
                    preloadOffset = 15,
                )
            }
            maxMemoryMB > 256 && availableProcessors >= 4 -> {
                // Mid-range device
                PerformanceSettings(
                    maxVisibleItems = 100,
                    imageQuality = com.example.jellyfinandroid.ui.image.ImageQuality.MEDIUM,
                    enableImagePreloading = true,
                    enableAnimations = true,
                    preloadOffset = 10,
                )
            }
            else -> {
                // Low-end device
                PerformanceSettings(
                    maxVisibleItems = 50,
                    imageQuality = com.example.jellyfinandroid.ui.image.ImageQuality.LOW,
                    enableImagePreloading = false,
                    enableAnimations = false,
                    preloadOffset = 5,
                )
            }
        }
    }
}

/**
 * Performance configuration data class.
 */
data class PerformanceSettings(
    val maxVisibleItems: Int,
    val imageQuality: com.example.jellyfinandroid.ui.image.ImageQuality,
    val enableImagePreloading: Boolean,
    val enableAnimations: Boolean,
    val preloadOffset: Int,
)

/**
 * Memory-aware item counter for virtualization.
 */
@Composable
fun rememberMemoryAwareItemLimit(
    totalItems: Int,
    itemSizeEstimateKB: Int = 10, // Estimate per item
): Int {
    return remember(totalItems) {
        val memoryInfo = PerformanceMonitor.getMemoryInfo()
        val availableMemoryMB = (memoryInfo.maxMemoryMB - memoryInfo.usedMemoryMB).coerceAtLeast(50)

        // Use max 10% of available memory for list items
        val memoryForItemsMB = availableMemoryMB / 10
        val maxItemsByMemory = (memoryForItemsMB * 1024) / itemSizeEstimateKB

        val limit = minOf(totalItems, maxItemsByMemory.toInt(), 500) // Cap at 500 items max

        android.util.Log.d(
            "MemoryAwareLimit",
            "Limited $totalItems items to $limit based on ${availableMemoryMB}MB available memory",
        )

        limit
    }
}
