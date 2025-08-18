package com.example.jellyfinandroid.core

import android.util.Log
import com.example.jellyfinandroid.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enhanced logging system for the Jellyfin Android app.
 * Provides structured logging, filtering, and optional crash reporting integration.
 */

/**
 * Log levels for filtering and categorization.
 */
enum class LogLevel(val priority: Int, val tag: String) {
    VERBOSE(Log.VERBOSE, "V"),
    DEBUG(Log.DEBUG, "D"),
    INFO(Log.INFO, "I"),
    WARN(Log.WARN, "W"),
    ERROR(Log.ERROR, "E"),
}

/**
 * Log categories for better organization.
 */
enum class LogCategory(val tag: String) {
    NETWORK("Network"),
    DATABASE("Database"),
    UI("UI"),
    AUTH("Auth"),
    MEDIA("Media"),
    CACHE("Cache"),
    PERFORMANCE("Performance"),
    LIFECYCLE("Lifecycle"),
    GENERAL("General"),
}

/**
 * Structured log entry for better log management.
 */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val category: LogCategory,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val metadata: Map<String, Any> = emptyMap(),
)

/**
 * Enhanced logger with structured logging and filtering capabilities.
 */
object Logger {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val maxLogEntries = 1000

    // Configuration
    var minLogLevel: LogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
    var enableFileLogging: Boolean = BuildConfig.DEBUG
    var enableCrashReporting: Boolean = !BuildConfig.DEBUG

    /**
     * Log verbose message.
     */
    fun v(category: LogCategory, tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap()) {
        log(LogLevel.VERBOSE, category, tag, message, throwable, metadata)
    }

    /**
     * Log debug message.
     */
    fun d(category: LogCategory, tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap()) {
        log(LogLevel.DEBUG, category, tag, message, throwable, metadata)
    }

    /**
     * Log info message.
     */
    fun i(category: LogCategory, tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap()) {
        log(LogLevel.INFO, category, tag, message, throwable, metadata)
    }

    /**
     * Log warning message.
     */
    fun w(category: LogCategory, tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap()) {
        log(LogLevel.WARN, category, tag, message, throwable, metadata)
    }

    /**
     * Log error message.
     */
    fun e(category: LogCategory, tag: String, message: String, throwable: Throwable? = null, metadata: Map<String, Any> = emptyMap()) {
        log(LogLevel.ERROR, category, tag, message, throwable, metadata)
    }

    /**
     * Core logging method.
     */
    private fun log(
        level: LogLevel,
        category: LogCategory,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, Any> = emptyMap(),
    ) {
        // Check if we should log based on minimum level
        if (level.priority < minLogLevel.priority) return

        val logEntry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            tag = tag,
            message = message,
            throwable = throwable,
            metadata = metadata,
        )

        // Add to in-memory queue
        addToQueue(logEntry)

        // Log to Android Log
        logToAndroidLog(logEntry)

        // Optional: Log to file or external system
        if (enableFileLogging) {
            logToFile(logEntry)
        }

        // Optional: Send to crash reporting service
        if (enableCrashReporting && level == LogLevel.ERROR) {
            reportToCrashService(logEntry)
        }
    }

    /**
     * Add log entry to in-memory queue with size limit.
     */
    private fun addToQueue(entry: LogEntry) {
        logQueue.offer(entry)

        // Remove old entries if queue is too large
        while (logQueue.size > maxLogEntries) {
            logQueue.poll()
        }
    }

    /**
     * Log to Android Log system.
     */
    private fun logToAndroidLog(entry: LogEntry) {
        val fullTag = "${entry.category.tag}_${entry.tag}"
        val formattedMessage = formatMessage(entry)

        when (entry.level) {
            LogLevel.VERBOSE -> Log.v(fullTag, formattedMessage, entry.throwable)
            LogLevel.DEBUG -> Log.d(fullTag, formattedMessage, entry.throwable)
            LogLevel.INFO -> Log.i(fullTag, formattedMessage, entry.throwable)
            LogLevel.WARN -> Log.w(fullTag, formattedMessage, entry.throwable)
            LogLevel.ERROR -> Log.e(fullTag, formattedMessage, entry.throwable)
        }
    }

    /**
     * Format log message with metadata.
     */
    private fun formatMessage(entry: LogEntry): String {
        val builder = StringBuilder(entry.message)

        if (entry.metadata.isNotEmpty()) {
            builder.append(" | Metadata: ")
            entry.metadata.forEach { (key, value) ->
                builder.append("$key=$value ")
            }
        }

        return builder.toString().trim()
    }

    /**
     * Log to file (placeholder for file logging implementation).
     */
    private fun logToFile(entry: LogEntry) {
        // In a real implementation, this would write to a file
        // For now, we'll use a coroutine to simulate async file operations
        CoroutineScope(Dispatchers.IO).launch {
            val timestamp = dateFormat.format(Date(entry.timestamp))
            val logLine = "[$timestamp] [${entry.level.tag}] [${entry.category.tag}] ${entry.tag}: ${formatMessage(entry)}"

            // TODO: Implement actual file writing
            if (BuildConfig.DEBUG) {
                println("FILE_LOG: $logLine")
            }
        }
    }

    /**
     * Report to crash reporting service (placeholder).
     */
    private fun reportToCrashService(entry: LogEntry) {
        // In a real implementation, this would integrate with services like:
        // - Firebase Crashlytics
        // - Bugsnag
        // - Sentry

        if (BuildConfig.DEBUG) {
            println("CRASH_REPORT: ${entry.level.tag} - ${entry.message}")
        }
    }

    /**
     * Get recent log entries for debugging or user support.
     */
    fun getRecentLogs(maxEntries: Int = 100): List<LogEntry> {
        return logQueue.toList().takeLast(maxEntries)
    }

    /**
     * Clear log queue.
     */
    fun clearLogs() {
        logQueue.clear()
    }

    /**
     * Get logs filtered by category.
     */
    fun getLogsByCategory(category: LogCategory, maxEntries: Int = 100): List<LogEntry> {
        return logQueue.toList().filter { it.category == category }.takeLast(maxEntries)
    }

    /**
     * Get logs filtered by level.
     */
    fun getLogsByLevel(level: LogLevel, maxEntries: Int = 100): List<LogEntry> {
        return logQueue.toList().filter { it.level == level }.takeLast(maxEntries)
    }

    /**
     * Export logs as formatted string.
     */
    fun exportLogs(maxEntries: Int = 500): String {
        val logs = getRecentLogs(maxEntries)
        val builder = StringBuilder()

        builder.appendLine("=== Jellyfin Android Logs ===")
        builder.appendLine("Generated: ${dateFormat.format(Date())}")
        builder.appendLine("Total entries: ${logs.size}")
        builder.appendLine("")

        logs.forEach { entry ->
            val timestamp = dateFormat.format(Date(entry.timestamp))
            builder.appendLine("[$timestamp] [${entry.level.tag}] [${entry.category.tag}] ${entry.tag}: ${formatMessage(entry)}")

            entry.throwable?.let { throwable ->
                builder.appendLine("  Exception: ${throwable.javaClass.simpleName}: ${throwable.message}")
                throwable.stackTrace.take(5).forEach { stackElement ->
                    builder.appendLine("    at $stackElement")
                }
            }
        }

        return builder.toString()
    }
}

/**
 * Extension functions for easier logging from different components.
 */

/**
 * Network-related logging extensions.
 */
object NetworkLogger {
    fun d(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        Logger.d(LogCategory.NETWORK, tag, message, metadata = metadata)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Logger.w(LogCategory.NETWORK, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Logger.e(LogCategory.NETWORK, tag, message, throwable)
    }
}

/**
 * UI-related logging extensions.
 */
object UILogger {
    fun d(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        Logger.d(LogCategory.UI, tag, message, metadata = metadata)
    }

    fun i(tag: String, message: String) {
        Logger.i(LogCategory.UI, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Logger.w(LogCategory.UI, tag, message, throwable)
    }
}

/**
 * Performance-related logging extensions.
 */
object PerformanceLogger {
    fun logDuration(tag: String, operation: String, durationMs: Long) {
        Logger.i(
            LogCategory.PERFORMANCE,
            tag,
            "Operation '$operation' completed",
            metadata = mapOf("duration_ms" to durationMs),
        )
    }

    fun logMemoryUsage(tag: String, usedMB: Long, maxMB: Long) {
        Logger.d(
            LogCategory.PERFORMANCE,
            tag,
            "Memory usage",
            metadata = mapOf(
                "used_mb" to usedMB,
                "max_mb" to maxMB,
                "usage_percent" to ((usedMB.toFloat() / maxMB) * 100).toInt(),
            ),
        )
    }
}
