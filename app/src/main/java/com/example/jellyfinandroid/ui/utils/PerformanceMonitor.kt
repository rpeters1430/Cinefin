package com.example.jellyfinandroid.ui.utils

import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.jellyfinandroid.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Phase 4: Performance Monitoring System
 *
 * Real-time performance analytics and automatic optimization
 * Monitors app performance and provides insights for optimization
 */
@Singleton
class PerformanceMonitor @Inject constructor() {

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val SAMPLE_INTERVAL_MS = 5000L // 5 seconds
        private const val MAX_PERFORMANCE_SAMPLES = 100
    }

    // Performance metrics
    private val _frameRate = MutableStateFlow(60f)
    val frameRate: StateFlow<Float> = _frameRate.asStateFlow()

    private val _memoryUsage = MutableStateFlow(0L)
    val memoryUsage: StateFlow<Long> = _memoryUsage.asStateFlow()

    private val _networkLatency = MutableStateFlow(0L)
    val networkLatency: StateFlow<Long> = _networkLatency.asStateFlow()

    private val _performanceScore = MutableStateFlow(100f)
    val performanceScore: StateFlow<Float> = _performanceScore.asStateFlow()

    // Device capabilities
    private val _deviceTier = MutableStateFlow(DeviceTier.HIGH_END)
    val deviceTier: StateFlow<DeviceTier> = _deviceTier.asStateFlow()

    // Performance history
    private val performanceSamples = mutableListOf<PerformanceSample>()
    private val operationTimes = mutableMapOf<String, MutableList<Long>>()

    init {
        determineDeviceTier()
    }

    /**
     * Device performance tiers for adaptive UI
     */
    enum class DeviceTier {
        LOW_END, // < 4GB RAM, older CPU
        MID_RANGE, // 4-8GB RAM, decent CPU
        HIGH_END, // > 8GB RAM, powerful CPU
    }

    /**
     * Performance sample data class
     */
    data class PerformanceSample(
        val timestamp: Long,
        val frameRate: Float,
        val memoryUsage: Long,
        val cpuUsage: Float,
        val networkLatency: Long,
    )

    /**
     * Measure operation performance
     */
    suspend fun <T> measureOperation(
        operationName: String,
        operation: suspend () -> T,
    ): T {
        val result: T
        val executionTime = measureTimeMillis {
            result = operation()
        }

        recordOperationTime(operationName, executionTime)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Operation '$operationName' took ${executionTime}ms")
        }

        return result
    }

    /**
     * Record operation execution time
     */
    private fun recordOperationTime(operationName: String, timeMs: Long) {
        val times = operationTimes.getOrPut(operationName) { mutableListOf() }
        times.add(timeMs)

        // Keep only recent samples
        if (times.size > 50) {
            times.removeAt(0)
        }
    }

    /**
     * Get average operation time
     */
    fun getAverageOperationTime(operationName: String): Long {
        val times = operationTimes[operationName] ?: return 0L
        return if (times.isNotEmpty()) times.average().toLong() else 0L
    }

    /**
     * Start continuous performance monitoring
     */
    suspend fun startMonitoring() {
        while (true) {
            updatePerformanceMetrics()
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        // Memory usage
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        _memoryUsage.value = usedMemory

        // Calculate performance score
        calculatePerformanceScore()

        // Store sample
        val sample = PerformanceSample(
            timestamp = System.currentTimeMillis(),
            frameRate = _frameRate.value,
            memoryUsage = usedMemory,
            cpuUsage = 0f, // Would need native code to get real CPU usage
            networkLatency = _networkLatency.value,
        )

        addPerformanceSample(sample)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Performance update - Memory: ${usedMemory / 1024 / 1024}MB, Score: ${_performanceScore.value}")
        }
    }

    /**
     * Add performance sample
     */
    private fun addPerformanceSample(sample: PerformanceSample) {
        performanceSamples.add(sample)

        // Keep only recent samples
        if (performanceSamples.size > MAX_PERFORMANCE_SAMPLES) {
            performanceSamples.removeAt(0)
        }
    }

    /**
     * Calculate overall performance score (0-100)
     */
    private fun calculatePerformanceScore() {
        var score = 100f

        // Memory pressure penalty
        val runtime = Runtime.getRuntime()
        val memoryUsageRatio = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / runtime.maxMemory()
        if (memoryUsageRatio > 0.8f) {
            score -= (memoryUsageRatio - 0.8f) * 100f // Penalty for high memory usage
        }

        // Frame rate penalty
        if (_frameRate.value < 30f) {
            score -= (30f - _frameRate.value) * 2f
        }

        // Network latency penalty
        if (_networkLatency.value > 1000L) {
            score -= (_networkLatency.value - 1000L) / 100f
        }

        _performanceScore.value = score.coerceIn(0f, 100f)
    }

    /**
     * Determine device performance tier
     */
    private fun determineDeviceTier() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB

        val tier = when {
            maxMemory > 6144 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> DeviceTier.HIGH_END
            maxMemory > 3072 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> DeviceTier.MID_RANGE
            else -> DeviceTier.LOW_END
        }

        _deviceTier.value = tier

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Device tier determined: $tier (${maxMemory}MB RAM)")
        }
    }

    /**
     * Get performance recommendations
     */
    fun getPerformanceRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val score = _performanceScore.value

        if (score < 70f) {
            recommendations.add("Consider reducing image quality for better performance")
        }

        if (_memoryUsage.value > Runtime.getRuntime().maxMemory() * 0.8) {
            recommendations.add("High memory usage detected - consider clearing cache")
        }

        if (_networkLatency.value > 2000L) {
            recommendations.add("Slow network detected - enable offline mode")
        }

        val avgLoadTime = getAverageOperationTime("loadLibraries")
        if (avgLoadTime > 3000L) {
            recommendations.add("Library loading is slow - consider pagination")
        }

        return recommendations
    }

    /**
     * Get optimal settings based on device tier
     */
    fun getOptimalSettings(): Map<String, Any> {
        return when (_deviceTier.value) {
            DeviceTier.LOW_END -> mapOf(
                "imageQuality" to "low",
                "maxConcurrentLoads" to 2,
                "enableAnimations" to false,
                "prefetchEnabled" to false,
                "maxCacheSize" to 50 * 1024 * 1024, // 50MB
            )
            DeviceTier.MID_RANGE -> mapOf(
                "imageQuality" to "medium",
                "maxConcurrentLoads" to 4,
                "enableAnimations" to true,
                "prefetchEnabled" to true,
                "maxCacheSize" to 100 * 1024 * 1024, // 100MB
            )
            DeviceTier.HIGH_END -> mapOf(
                "imageQuality" to "high",
                "maxConcurrentLoads" to 8,
                "enableAnimations" to true,
                "prefetchEnabled" to true,
                "maxCacheSize" to 200 * 1024 * 1024, // 200MB
            )
        }
    }

    /**
     * Record network latency
     */
    fun recordNetworkLatency(latencyMs: Long) {
        _networkLatency.value = latencyMs
    }

    /**
     * Record frame rate (would be called by UI measuring system)
     */
    fun recordFrameRate(fps: Float) {
        _frameRate.value = fps
    }

    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): Map<String, Any> {
        return mapOf(
            "currentScore" to _performanceScore.value,
            "deviceTier" to _deviceTier.value.name,
            "memoryUsageMB" to _memoryUsage.value / 1024 / 1024,
            "frameRate" to _frameRate.value,
            "networkLatency" to _networkLatency.value,
            "totalSamples" to performanceSamples.size,
            "recommendations" to getPerformanceRecommendations(),
        )
    }
}

/**
 * Composable for monitoring UI performance
 */
@Composable
fun PerformanceTracker(
    operationName: String,
    performanceMonitor: PerformanceMonitor,
    content: @Composable () -> Unit,
) {
    var startTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        startTime = System.currentTimeMillis()
    }

    content()

    LaunchedEffect(Unit) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        if (duration > 0) {
            // Record composition time
            performanceMonitor.recordOperationTime(operationName, duration)
        }
    }
}

/**
 * Extension function for PerformanceMonitor
 */
private fun PerformanceMonitor.recordOperationTime(operationName: String, timeMs: Long) {
    // This would be implemented in the PerformanceMonitor class
    if (BuildConfig.DEBUG) {
        Log.d("PerformanceMonitor", "UI operation '$operationName' took ${timeMs}ms")
    }
}
