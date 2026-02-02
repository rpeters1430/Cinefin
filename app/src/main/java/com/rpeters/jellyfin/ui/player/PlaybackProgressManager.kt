package com.rpeters.jellyfin.ui.player

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    // Create a managed scope for fire-and-forget operations
    // This is a singleton, so the scope lives as long as the app
    private val managerScope = CoroutineScope(Dispatchers.Default + Job())
    private var progressSyncJob: Job? = null

    private var currentItemId: String = ""
    private var lastReportedPosition: Long = 0L
    private var lastStateUpdateTime: Long = 0L
    private var sessionId: String = ""
    private var hasReportedStart: Boolean = false

    companion object {
        private const val PROGRESS_SYNC_INTERVAL = 10_000L // 10 seconds
        private const val WATCHED_THRESHOLD = 0.90f // 90% watched
        private const val MIN_POSITION_CHANGE = 5_000L // 5 seconds minimum change
        private const val STATE_UPDATE_THROTTLE_MS = 500L // Throttle UI state updates to max 2 per second
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

        _playbackProgress.update { PlaybackProgress(itemId = itemId) }

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
        val currentTime = System.currentTimeMillis()

        // Throttle state updates to prevent excessive recompositions (max 2 per second)
        // Only update state if enough time has passed OR if there's a significant change
        val shouldUpdateState = (currentTime - lastStateUpdateTime >= STATE_UPDATE_THROTTLE_MS) ||
            kotlin.math.abs(positionMs - _playbackProgress.value.positionMs) >= MIN_POSITION_CHANGE

        if (shouldUpdateState) {
            _playbackProgress.update {
                it.copy(
                    itemId = currentItemId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    percentageWatched = percentageWatched,
                    isWatched = isWatched,
                )
            }
            lastStateUpdateTime = currentTime
        }

        if (!hasReportedStart) {
            hasReportedStart = true
            // Use managed scope for fire-and-forget reporting
            managerScope.launch {
                reportPlaybackStart(positionMs, durationMs)
            }
        }

        // Report progress to server if significant change
        if (kotlin.math.abs(positionMs - lastReportedPosition) >= MIN_POSITION_CHANGE) {
            managerScope.launch {
                reportProgress(positionMs, durationMs, isWatched)
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
                    _playbackProgress.update { it.copy(isWatched = true) }
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
        } catch (e: CancellationException) {
            throw e
        }
    }

    // Made suspend to avoid storing scope reference (memory leak fix)
    suspend fun markAsUnwatched() {
        if (currentItemId.isEmpty()) return

        try {
            when (val result = userRepository.markAsUnwatched(currentItemId)) {
                is ApiResult.Success -> {
                    _playbackProgress.update { it.copy(isWatched = false) }
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
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun stopTracking(reportStop: Boolean = true) {
        progressSyncJob?.cancel()

        // Final progress report
        val progress = _playbackProgress.value
        if (progress.itemId.isNotEmpty()) {
            reportProgress(progress.positionMs, progress.durationMs, progress.isWatched)
            if (reportStop) {
                reportPlaybackStop(progress.positionMs, progress.durationMs)
            }
        }

        currentItemId = ""
        lastReportedPosition = 0L
        lastStateUpdateTime = 0L
        sessionId = ""
        hasReportedStart = false
        _playbackProgress.update { PlaybackProgress() }
        if (BuildConfig.DEBUG) {
            Log.d("PlaybackProgressManager", "Stopped tracking")
        }
    }

    /**
     * Non-blocking version of stopTracking for use during cleanup.
     * Uses the manager's own coroutine scope to report stop asynchronously,
     * avoiding blocking the calling (main) thread with network calls.
     */
    fun stopTrackingAsync(reportStop: Boolean = true) {
        progressSyncJob?.cancel()
        managerScope.launch {
            try {
                stopTracking(reportStop)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // Report current progress when app is paused using managed scope
        managerScope.launch {
            val progress = _playbackProgress.value
            if (progress.itemId.isNotEmpty()) {
                reportProgress(progress.positionMs, progress.durationMs, progress.isWatched)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // stopTracking is suspend, so launch in managed scope
        managerScope.launch {
            stopTracking()
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
        } catch (e: CancellationException) {
            throw e
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
                    _playbackProgress.update {
                        it.copy(
                            lastSyncTime = System.currentTimeMillis(),
                        )
                    }
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
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
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
                    _playbackProgress.update {
                        it.copy(
                            itemId = itemId,
                            positionMs = resumeMs,
                            percentageWatched = percentage.coerceIn(0f, 1f),
                            isWatched = data.played,
                            lastSyncTime = System.currentTimeMillis(),
                        )
                    }
                    resumeMs
                }
                is ApiResult.Error -> {
                    Log.e("PlaybackProgressManager", "Failed to load resume position: ${result.message}")
                    0L
                }
                else -> 0L
            }
        } catch (e: CancellationException) {
            throw e
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
