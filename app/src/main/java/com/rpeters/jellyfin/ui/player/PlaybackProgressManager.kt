package com.rpeters.jellyfin.ui.player

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackProgress(
    val itemId: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val percentageWatched: Float = 0f,
    val isWatched: Boolean = false,
    val lastSyncTime: Long = 0L,
)

@Singleton
class PlaybackProgressManager @Inject constructor(
    private val userRepository: JellyfinUserRepository,
) : DefaultLifecycleObserver {

    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

    private var progressSyncJob: Job? = null
    // Memory leak fix: Don't store reference to external scope
    // Instead, pass scope through parameters or use internal scope for async operations
    // private var coroutineScope: CoroutineScope? = null

    private var currentItemId: String = ""
    private var lastReportedPosition: Long = 0L
    private var sessionId: String = ""
    private var hasReportedStart: Boolean = false

    companion object {
        private const val PROGRESS_SYNC_INTERVAL = 10_000L // 10 seconds
        private const val WATCHED_THRESHOLD = 0.90f // 90% watched
        private const val MIN_POSITION_CHANGE = 5_000L // 5 seconds minimum change
    }

    fun startTracking(
        itemId: String,
        scope: CoroutineScope,
        sessionId: String = java.util.UUID.randomUUID().toString(),
    ) {
        // Don't store the scope - prevents memory leak
        this.currentItemId = itemId
        this.sessionId = sessionId
        this.hasReportedStart = false
        this.lastReportedPosition = 0L

        _playbackProgress.value = PlaybackProgress(itemId = itemId)

        // Load existing progress from server
        scope.launch {
            loadExistingProgress(itemId)
        }

        // Start periodic progress sync
        startProgressSync(scope)

        if (BuildConfig.DEBUG) {
            Log.d("PlaybackProgressManager", "Started tracking for item: $itemId")
        }
    }

    fun updateProgress(positionMs: Long, durationMs: Long) {
        if (currentItemId.isEmpty() || durationMs <= 0) return

        val percentageWatched = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val isWatched = percentageWatched >= WATCHED_THRESHOLD

        _playbackProgress.value = _playbackProgress.value.copy(
            itemId = currentItemId,
            positionMs = positionMs,
            durationMs = durationMs,
            percentageWatched = percentageWatched,
            isWatched = isWatched,
        )

        if (!hasReportedStart) {
            hasReportedStart = true
            // Use progressSyncJob's scope for fire-and-forget reporting
            progressSyncJob?.let { job ->
                kotlinx.coroutines.CoroutineScope(job.coroutineContext).launch {
                    reportPlaybackStart(positionMs, durationMs)
                }
            }
        }

        // Report progress if significant change
        if (kotlin.math.abs(positionMs - lastReportedPosition) >= MIN_POSITION_CHANGE) {
            progressSyncJob?.let { job ->
                kotlinx.coroutines.CoroutineScope(job.coroutineContext).launch {
                    reportProgress(positionMs, durationMs, isWatched)
                }
            }
            lastReportedPosition = positionMs
        }
    }

    // Made suspend to avoid storing scope reference (memory leak fix)
    suspend fun markAsWatched() {
        if (currentItemId.isEmpty()) return

        try {
            when (val result = userRepository.markAsWatched(currentItemId)) {
                is ApiResult.Success -> {
                    _playbackProgress.value = _playbackProgress.value.copy(isWatched = true)
                    if (BuildConfig.DEBUG) {
                        Log.d("PlaybackProgressManager", "Marked item as watched: $currentItemId")
                    }
                }
                is ApiResult.Error -> {
                    Log.e("PlaybackProgressManager", "Failed to mark as watched: ${result.message}")
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Error marking as watched", e)
        }
    }

    // Made suspend to avoid storing scope reference (memory leak fix)
    suspend fun markAsUnwatched() {
        if (currentItemId.isEmpty()) return

        try {
            when (val result = userRepository.markAsUnwatched(currentItemId)) {
                is ApiResult.Success -> {
                    _playbackProgress.value = _playbackProgress.value.copy(isWatched = false)
                    if (BuildConfig.DEBUG) {
                        Log.d("PlaybackProgressManager", "Marked item as unwatched: $currentItemId")
                    }
                }
                is ApiResult.Error -> {
                    Log.e("PlaybackProgressManager", "Failed to mark as unwatched: ${result.message}")
                }
                is ApiResult.Loading -> {
                    // Handle loading state if needed
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Error marking as unwatched", e)
        }
    }

    suspend fun stopTracking() {
        progressSyncJob?.cancel()

        // Final progress report
        val progress = _playbackProgress.value
        if (progress.itemId.isNotEmpty()) {
            reportProgress(progress.positionMs, progress.durationMs, progress.isWatched)
            reportPlaybackStop(progress.positionMs, progress.durationMs)
        }

        currentItemId = ""
        lastReportedPosition = 0L
        sessionId = ""
        hasReportedStart = false
        _playbackProgress.value = PlaybackProgress()
        if (BuildConfig.DEBUG) {
            Log.d("PlaybackProgressManager", "Stopped tracking")
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // Report current progress when app is paused
        // Use progressSyncJob's scope for fire-and-forget reporting
        progressSyncJob?.let { job ->
            kotlinx.coroutines.CoroutineScope(job.coroutineContext).launch {
                val progress = _playbackProgress.value
                if (progress.itemId.isNotEmpty()) {
                    reportProgress(progress.positionMs, progress.durationMs, progress.isWatched)
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // stopTracking is now suspend, so launch in progressSyncJob's scope
        progressSyncJob?.let { job ->
            kotlinx.coroutines.CoroutineScope(job.coroutineContext).launch {
                stopTracking()
            }
        } ?: run {
            // If no job, just cancel and clear state
            progressSyncJob?.cancel()
            currentItemId = ""
            lastReportedPosition = 0L
            sessionId = ""
            hasReportedStart = false
            _playbackProgress.value = PlaybackProgress()
        }
    }

    private fun startProgressSync(scope: CoroutineScope) {
        progressSyncJob?.cancel()
        progressSyncJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_SYNC_INTERVAL)
                val progress = _playbackProgress.value
                if (progress.itemId.isNotEmpty()) {
                    reportProgress(progress.positionMs, progress.durationMs, progress.isWatched)
                }
            }
        }
    }

    private suspend fun loadExistingProgress(itemId: String) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d("PlaybackProgressManager", "Loading existing progress for item: $itemId")
            }
            getResumePosition(itemId)
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to load existing progress", e)
        }
    }

    private suspend fun reportProgress(positionMs: Long, durationMs: Long, isWatched: Boolean) {
        if (currentItemId.isEmpty()) return
        try {
            val ticks = positionMs.toTicks()
            when (
                val result = userRepository.reportPlaybackProgress(
                    itemId = currentItemId,
                    sessionId = sessionId,
                    positionTicks = ticks,
                    isPaused = false,
                    canSeek = durationMs > 0,
                )
            ) {
                is ApiResult.Success -> {
                    _playbackProgress.value = _playbackProgress.value.copy(
                        lastSyncTime = System.currentTimeMillis(),
                    )
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "PlaybackProgressManager",
                            "Reported progress at ${positionMs}ms for $currentItemId",
                        )
                    }
                }
                is ApiResult.Error -> {
                    Log.e(
                        "PlaybackProgressManager",
                        "Failed to report progress: ${result.message}",
                    )
                }
                else -> Unit
            }
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to report progress", e)
        }
    }

    private suspend fun reportPlaybackStart(positionMs: Long, durationMs: Long) {
        if (currentItemId.isEmpty()) return
        try {
            val ticks = positionMs.toTicks()
            val result = userRepository.reportPlaybackStart(
                itemId = currentItemId,
                sessionId = sessionId,
                positionTicks = ticks,
                isPaused = false,
                canSeek = durationMs > 0,
            )
            if (result is ApiResult.Error) {
                Log.e(
                    "PlaybackProgressManager",
                    "Failed to report playback start: ${result.message}",
                )
            } else if (BuildConfig.DEBUG) {
                Log.d("PlaybackProgressManager", "Reported playback start for: $currentItemId")
            }
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to report playback start", e)
        }
    }

    private suspend fun reportPlaybackStop(positionMs: Long, durationMs: Long) {
        if (currentItemId.isEmpty()) return
        try {
            val ticks = positionMs.toTicks()
            val result = userRepository.reportPlaybackStopped(
                itemId = currentItemId,
                sessionId = sessionId,
                positionTicks = ticks,
                failed = false,
            )
            if (result is ApiResult.Error) {
                Log.e(
                    "PlaybackProgressManager",
                    "Failed to report playback stop: ${result.message}",
                )
            } else if (BuildConfig.DEBUG) {
                Log.d(
                    "PlaybackProgressManager",
                    "Reported playback stop for: $currentItemId at ${positionMs}ms",
                )
            }
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to report playback stop", e)
        }
    }

    /**
     * Gets the resume position for an item based on previous playback progress
     */
    suspend fun getResumePosition(itemId: String): Long {
        val current = _playbackProgress.value
        if (current.itemId == itemId && current.lastSyncTime > 0) {
            return current.positionMs
        }

        return try {
            when (val result = userRepository.getItemUserData(itemId)) {
                is ApiResult.Success -> {
                    val data = result.data
                    val resumeMs = data.playbackPositionTicks / 10_000L
                    val percentage = ((data.playedPercentage ?: 0.0) / 100.0).toFloat()
                    lastReportedPosition = resumeMs
                    _playbackProgress.value = _playbackProgress.value.copy(
                        itemId = itemId,
                        positionMs = resumeMs,
                        percentageWatched = percentage.coerceIn(0f, 1f),
                        isWatched = data.played,
                        lastSyncTime = System.currentTimeMillis(),
                    )
                    resumeMs
                }
                is ApiResult.Error -> {
                    Log.e("PlaybackProgressManager", "Failed to load resume position: ${result.message}")
                    0L
                }
                else -> 0L
            }
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to get resume position", e)
            0L
        }
    }

    /**
     * Checks if an item should be marked as watched based on playback progress
     */
    fun shouldMarkAsWatched(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        return (positionMs.toFloat() / durationMs.toFloat()) >= WATCHED_THRESHOLD
    }
}

private fun Long.toTicks(): Long = this * 10_000L
