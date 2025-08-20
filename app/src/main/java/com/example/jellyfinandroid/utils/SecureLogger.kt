package com.example.jellyfinandroid.utils

import android.util.Log
import com.example.jellyfinandroid.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern

/**
 * Unified logging utility that merges structured logging with secure sanitisation.
 *
 * Features:
 * - Structured log levels and categories
 * - Automatic sanitisation of sensitive data
 * - Optional file logging and crash reporting hooks
 */

/** Log levels for filtering and categorisation. */
enum class LogLevel(val priority: Int, val tag: String) {
    VERBOSE(Log.VERBOSE, "V"),
    DEBUG(Log.DEBUG, "D"),
    INFO(Log.INFO, "I"),
    WARN(Log.WARN, "W"),
    ERROR(Log.ERROR, "E"),
}

/** Log categories for better organisation. */
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

/** Structured log entry kept in memory for later export or diagnostics. */
data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val category: LogCategory,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)

object SecureLogger {
    private const val MAX_LOG_LENGTH = 4000 // Android Log limit
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val maxLogEntries = 1000

    // Configuration
    var minLogLevel: LogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
    var enableFileLogging: Boolean = BuildConfig.DEBUG
    var enableCrashReporting: Boolean = !BuildConfig.DEBUG

    // Patterns to identify and sanitise sensitive data
    private val SENSITIVE_PATTERNS = listOf(
        Pattern.compile("(token|access_token|refresh_token|api_key|apikey)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(password|pwd|passwd)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(authorization|auth)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(sessionid|session_id|jsessionid)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(https?://[^:/]+):([^@]+)@", Pattern.CASE_INSENSITIVE),
    )
    private const val REPLACEMENT = "\$1\$2***"

    // region Public logging API
    fun v(tag: String, message: String, data: Any? = null, category: LogCategory = LogCategory.GENERAL) {
        log(LogLevel.VERBOSE, category, tag, message, data)
    }

    fun d(tag: String, message: String, data: Any? = null, category: LogCategory = LogCategory.GENERAL) {
        log(LogLevel.DEBUG, category, tag, message, data)
    }

    fun i(tag: String, message: String, data: Any? = null, category: LogCategory = LogCategory.GENERAL) {
        log(LogLevel.INFO, category, tag, message, data)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.GENERAL) {
        log(LogLevel.WARN, category, tag, message, throwable = throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.GENERAL) {
        log(LogLevel.ERROR, category, tag, message, throwable = throwable)
    }

    fun auth(tag: String, message: String, success: Boolean = true) {
        val status = if (success) "SUCCESS" else "FAILURE"
        d(tag, "AUTH [$status]: $message", category = LogCategory.AUTH)
    }

    fun api(tag: String, method: String, url: String, responseCode: Int? = null) {
        val sanitizedUrl = sanitizeUrl(url)
        val response = responseCode?.let { " | Response: $it" } ?: ""
        d(tag, "API [$method]: $sanitizedUrl$response", category = LogCategory.NETWORK)
    }

    fun networkError(tag: String, error: String, url: String? = null, throwable: Throwable? = null) {
        val sanitizedError = sanitizeForLogging(error)
        val sanitizedUrl = url?.let { " | URL: ${sanitizeUrl(it)}" } ?: ""
        e(tag, "NETWORK ERROR: $sanitizedError$sanitizedUrl", throwable, category = LogCategory.NETWORK)
    }
    // endregion

    // region Core logging
    private fun log(
        level: LogLevel,
        category: LogCategory,
        tag: String,
        message: String,
        data: Any? = null,
        throwable: Throwable? = null,
    ) {
        if (level.priority < minLogLevel.priority) return

        val sanitizedMessage = sanitizeForLogging(message)
        val sanitizedData = data?.let { sanitizeForLogging(it.toString()) }
        val fullMessage = if (!sanitizedData.isNullOrEmpty()) "$sanitizedMessage | Data: $sanitizedData" else sanitizedMessage

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            tag = tag,
            message = fullMessage,
            throwable = throwable,
        )

        addToQueue(entry)
        logToAndroidLog(entry)

        if (enableFileLogging) {
            logToFile(entry)
        }

        if (enableCrashReporting && level == LogLevel.ERROR) {
            reportToCrashService(entry)
        }
    }

    private fun addToQueue(entry: LogEntry) {
        logQueue.offer(entry)
        while (logQueue.size > maxLogEntries) {
            logQueue.poll()
        }
    }

    private fun logToAndroidLog(entry: LogEntry) {
        val fullTag = "${entry.category.tag}_${entry.tag}"
        when (entry.level) {
            LogLevel.VERBOSE -> logChunked(Log.VERBOSE, fullTag, entry.message)
            LogLevel.DEBUG -> logChunked(Log.DEBUG, fullTag, entry.message)
            LogLevel.INFO -> logChunked(Log.INFO, fullTag, entry.message)
            LogLevel.WARN -> entry.throwable?.let { Log.w(fullTag, entry.message, it) } ?: logChunked(Log.WARN, fullTag, entry.message)
            LogLevel.ERROR -> entry.throwable?.let { Log.e(fullTag, entry.message, it) } ?: logChunked(Log.ERROR, fullTag, entry.message)
        }
    }

    private fun logChunked(priority: Int, tag: String, message: String) {
        if (message.length <= MAX_LOG_LENGTH) {
            Log.println(priority, tag, message)
            return
        }

        var index = 0
        while (index < message.length) {
            val end = minOf(index + MAX_LOG_LENGTH, message.length)
            val chunk = message.substring(index, end)
            val chunkTag = if (index > 0) "$tag[${index / MAX_LOG_LENGTH + 1}]" else tag
            Log.println(priority, chunkTag, chunk)
            index = end
        }
    }

    private fun logToFile(entry: LogEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            val timestamp = dateFormat.format(Date(entry.timestamp))
            val logLine = "[$timestamp] [${entry.level.tag}] [${entry.category.tag}] ${entry.tag}: ${entry.message}"
            // TODO: Implement actual file writing
            if (BuildConfig.DEBUG) {
                println("FILE_LOG: $logLine")
            }
        }
    }

    private fun reportToCrashService(entry: LogEntry) {
        if (BuildConfig.DEBUG) {
            println("CRASH_REPORT: ${entry.level.tag} - ${entry.message}")
        }
    }
    // endregion

    // region Log retrieval helpers
    fun getRecentLogs(maxEntries: Int = 100): List<LogEntry> {
        return logQueue.toList().takeLast(maxEntries)
    }

    fun clearLogs() {
        logQueue.clear()
    }

    fun getLogsByCategory(category: LogCategory, maxEntries: Int = 100): List<LogEntry> {
        return logQueue.toList().filter { it.category == category }.takeLast(maxEntries)
    }

    fun getLogsByLevel(level: LogLevel, maxEntries: Int = 100): List<LogEntry> {
        return logQueue.toList().filter { it.level == level }.takeLast(maxEntries)
    }

    fun exportLogs(maxEntries: Int = 500): String {
        val logs = getRecentLogs(maxEntries)
        val builder = StringBuilder()
        builder.appendLine("=== Jellyfin Android Logs ===")
        builder.appendLine("Generated: ${dateFormat.format(Date())}")
        builder.appendLine("Total entries: ${logs.size}")
        builder.appendLine("")

        logs.forEach { entry ->
            val timestamp = dateFormat.format(Date(entry.timestamp))
            builder.appendLine("[$timestamp] [${entry.level.tag}] [${entry.category.tag}] ${entry.tag}: ${entry.message}")
            entry.throwable?.let { throwable ->
                builder.appendLine("  Exception: ${throwable.javaClass.simpleName}: ${throwable.message}")
                throwable.stackTrace.take(5).forEach { stackElement ->
                    builder.appendLine("    at $stackElement")
                }
            }
        }

        return builder.toString()
    }
    // endregion

    // region Sanitisation helpers
    private fun sanitizeForLogging(text: String): String {
        var sanitized = text
        SENSITIVE_PATTERNS.forEach { pattern ->
            sanitized = pattern.matcher(sanitized).replaceAll(REPLACEMENT)
        }
        sanitized = sanitized.replace(Regex("eyJ[A-Za-z0-9+/=._-]{20,}"), "eyJ***")
        sanitized = sanitized.replace(
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE),
            "UUID-***",
        )
        return if (sanitized.length > MAX_LOG_LENGTH) {
            sanitized.take(MAX_LOG_LENGTH - 3) + "..."
        } else {
            sanitized
        }
    }

    private fun sanitizeUrl(url: String): String {
        val withoutCredentials = url.replace(Regex("(https?://)[^:/]+:[^@]+@"), "\$1***:***@")
        return withoutCredentials.replace(
            Regex("([?&])(token|api_key|apikey|password|auth)=([^&]+)", RegexOption.IGNORE_CASE),
            "\$1\$2=***",
        )
    }
    // endregion
}

// region Extension helpers
inline fun <reified T> T.logDebug(message: String, data: Any? = null, category: LogCategory = LogCategory.GENERAL) {
    SecureLogger.d(T::class.java.simpleName, message, data, category)
}

inline fun <reified T> T.logInfo(message: String, data: Any? = null, category: LogCategory = LogCategory.GENERAL) {
    SecureLogger.i(T::class.java.simpleName, message, data, category)
}

inline fun <reified T> T.logWarning(message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.GENERAL) {
    SecureLogger.w(T::class.java.simpleName, message, throwable, category)
}

inline fun <reified T> T.logError(message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.GENERAL) {
    SecureLogger.e(T::class.java.simpleName, message, throwable, category)
}

inline fun <reified T> T.logAuth(message: String, success: Boolean = true) {
    SecureLogger.auth(T::class.java.simpleName, message, success)
}

inline fun <reified T> T.logApi(method: String, url: String, responseCode: Int? = null) {
    SecureLogger.api(T::class.java.simpleName, method, url, responseCode)
}

inline fun <reified T> T.logNetworkError(error: String, url: String? = null, throwable: Throwable? = null) {
    SecureLogger.networkError(T::class.java.simpleName, error, url, throwable)
}
// endregion

