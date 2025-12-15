package com.rpeters.jellyfin.utils

import android.os.Build
import android.os.Debug
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

/**
 * Performance monitoring utilities for tracking app performance metrics.
 *
 * @deprecated This Object singleton version is deprecated.
 * Use com.rpeters.jellyfin.ui.utils.PerformanceMonitor instead, which provides
 * a comprehensive @Singleton Hilt-injected implementation with StateFlows and
 * device tier detection. All static utility methods have been migrated to the
 * companion object of the new implementation.
 *
 * Migration path:
 * - Import: com.rpeters.jellyfin.ui.utils.PerformanceMonitor
 * - Static methods (getMemoryInfo, checkMemoryPressure, etc.) remain available
 *   through the companion object
 *
 * This file will be removed in a future release.
 */
@Deprecated(
    message = "Use com.rpeters.jellyfin.ui.utils.PerformanceMonitor instead",
    replaceWith = ReplaceWith(
        "PerformanceMonitor",
        "com.rpeters.jellyfin.ui.utils.PerformanceMonitor",
    ),
    level = DeprecationLevel.WARNING,
)
object PerformanceMonitor {
    private const val TAG = "PerformanceMonitor"

    data class MemoryInfo(
        val usedMemoryMB: Long,
        val totalMemoryMB: Long,
        val freeMemoryMB: Long,
        val maxMemoryMB: Long,
        val usagePercentage: Float,
    )

    data class PerformanceMetrics(
        val memory: MemoryInfo,
        val renderTimeMs: Long,
        val frameDrops: Int,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /**
     * Get current memory usage information.
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory

        val usedMemoryMB = usedMemory / (1024 * 1024)
        val totalMemoryMB = totalMemory / (1024 * 1024)
        val freeMemoryMB = freeMemory / (1024 * 1024)
        val maxMemoryMB = maxMemory / (1024 * 1024)
        val usagePercentage = (usedMemory.toFloat() / maxMemory.toFloat()) * 100f

        return MemoryInfo(
            usedMemoryMB = usedMemoryMB,
            totalMemoryMB = totalMemoryMB,
            freeMemoryMB = freeMemoryMB,
            maxMemoryMB = maxMemoryMB,
            usagePercentage = usagePercentage,
        )
    }

    /**
     * Log memory usage with optional custom message.
     */
    fun logMemoryUsage(message: String = "Memory Usage") {
        val memoryInfo = getMemoryInfo()
        SecureLogger.v(
            TAG,
            "$message - Used: ${memoryInfo.usedMemoryMB}MB (${String.format("%.1f", memoryInfo.usagePercentage)}%), " +
                "Free: ${memoryInfo.freeMemoryMB}MB, Max: ${memoryInfo.maxMemoryMB}MB",
        )
    }

    /**
     * Measure execution time of a block of code.
     */
    fun <T> measureExecutionTime(tag: String, block: () -> T): T {
        var result: T
        val executionTime = measureTimeMillis {
            result = block()
        }
        SecureLogger.v(TAG, "$tag executed in ${executionTime}ms")
        return result
    }

    /**
     * Force garbage collection and log memory before/after.
     */
    fun forceGarbageCollection(reason: String) {
        val beforeMemory = getMemoryInfo()
        SecureLogger.v(TAG, "GC Request ($reason) - Before: ${beforeMemory.usedMemoryMB}MB")

        System.gc()

        // Small delay to allow GC to complete
        runBlocking {
            delay(100)
        }

        val afterMemory = getMemoryInfo()
        val freedMB = beforeMemory.usedMemoryMB - afterMemory.usedMemoryMB
        SecureLogger.v(TAG, "GC Request ($reason) - After: ${afterMemory.usedMemoryMB}MB (Freed: ${freedMB}MB)")
    }

    /**
     * Check if memory usage is high and suggest garbage collection.
     */
    fun checkMemoryPressure(): Boolean {
        val memoryInfo = getMemoryInfo()
        val isHighUsage = memoryInfo.usagePercentage > 80f

        if (isHighUsage) {
            SecureLogger.w(TAG, "High memory usage detected: ${String.format("%.1f", memoryInfo.usagePercentage)}%")
        }

        return isHighUsage
    }

    /**
     * Get native heap information (API 23+).
     */
    fun getNativeHeapInfo(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nativeHeapSize = Debug.getNativeHeapSize() / (1024 * 1024)
            val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val nativeHeapFree = Debug.getNativeHeapFreeSize() / (1024 * 1024)
            "Native Heap - Size: ${nativeHeapSize}MB, Allocated: ${nativeHeapAllocated}MB, Free: ${nativeHeapFree}MB"
        } else {
            null
        }
    }

    /**
     * Performance metrics collection helper.
     */
    fun collectPerformanceMetrics(renderTimeMs: Long = 0, frameDrops: Int = 0): PerformanceMetrics {
        return PerformanceMetrics(
            memory = getMemoryInfo(),
            renderTimeMs = renderTimeMs,
            frameDrops = frameDrops,
        )
    }
}

/**
 * Composable for monitoring performance during composition.
 */
@Composable
fun PerformanceTracker(
    enabled: Boolean = true,
    intervalMs: Long = 10000, // 10 seconds
    onMetricsCollected: (PerformanceMonitor.PerformanceMetrics) -> Unit = {},
) {
    var lastCollectionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect

        while (true) {
            delay(intervalMs)

            val currentTime = System.currentTimeMillis()
            val renderTime = currentTime - lastCollectionTime
            lastCollectionTime = currentTime

            @Suppress("DEPRECATION")
            val metrics = PerformanceMonitor.collectPerformanceMetrics(renderTime)
            onMetricsCollected(metrics)

            // Log metrics only if verbose logging is enabled
            SecureLogger.v(
                "PerformanceTracker",
                "Memory: ${metrics.memory.usedMemoryMB}MB (${String.format("%.1f", metrics.memory.usagePercentage)}%), " +
                    "Render: ${metrics.renderTimeMs}ms",
            )

            // Auto-GC if memory usage is very high
            if (metrics.memory.usagePercentage > 90f) {
                @Suppress("DEPRECATION")
                PerformanceMonitor.forceGarbageCollection("High memory pressure")
            }
        }
    }
}

/**
 * Wrapper for performance-sensitive operations.
 */
@Composable
fun <T> performanceOptimized(
    key: Any?,
    enabled: Boolean = true,
    computation: @Composable () -> T,
): T {
    return if (enabled) {
        remember(key) {
            @Suppress("DEPRECATION")
            PerformanceMonitor.measureExecutionTime("Composition") {
                computation
            }
        }.invoke()
    } else {
        computation()
    }
}

/**
 * Extension function for measuring block execution time.
 */
suspend inline fun <T> measureSuspendTime(tag: String, crossinline block: suspend () -> T): T {
    var result: T
    val executionTime = measureTimeMillis {
        result = block()
    }
    SecureLogger.v("PerformanceMonitor", "$tag executed in ${executionTime}ms")
    return result
}
