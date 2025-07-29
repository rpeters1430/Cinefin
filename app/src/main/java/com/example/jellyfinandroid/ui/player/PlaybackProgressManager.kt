package com.example.jellyfinandroid.ui.player

import com.example.jellyfinandroid.BuildConfig
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.jellyfinandroid.data.repository.ApiResult
import com.example.jellyfinandroid.data.repository.JellyfinRepository
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
    val lastSyncTime: Long = 0L
)

@Singleton
class PlaybackProgressManager @Inject constructor(
    private val repository: JellyfinRepository
) : DefaultLifecycleObserver {
    
    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()
    
    private var progressSyncJob: Job? = null
    private var coroutineScope: CoroutineScope? = null
    
    private var currentItemId: String = ""
    private var lastReportedPosition: Long = 0L
    private var sessionId: String = ""
    
    companion object {
        private const val PROGRESS_SYNC_INTERVAL = 10_000L // 10 seconds
        private const val WATCHED_THRESHOLD = 0.90f // 90% watched
        private const val MIN_POSITION_CHANGE = 5_000L // 5 seconds minimum change
    }
    
    fun startTracking(
        itemId: String,
        scope: CoroutineScope,
        sessionId: String = java.util.UUID.randomUUID().toString()
    ) {
        this.coroutineScope = scope
        this.currentItemId = itemId
        this.sessionId = sessionId
        
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
            isWatched = isWatched
        )
        
        // Report progress if significant change
        if (kotlin.math.abs(positionMs - lastReportedPosition) >= MIN_POSITION_CHANGE) {
            coroutineScope?.launch {
                reportProgress(positionMs, durationMs, isWatched)
            }
            lastReportedPosition = positionMs
        }
    }
    
    fun markAsWatched() {
        if (currentItemId.isEmpty()) return
        
        coroutineScope?.launch {
            try {
                when (val result = repository.markAsWatched(currentItemId)) {
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
    }
    
    fun markAsUnwatched() {
        if (currentItemId.isEmpty()) return
        
        coroutineScope?.launch {
            try {
                when (val result = repository.markAsUnwatched(currentItemId)) {
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
    }
    
    fun stopTracking() {
        progressSyncJob?.cancel()
        
        // Final progress report
        coroutineScope?.launch {
            val progress = _playbackProgress.value
            if (progress.itemId.isNotEmpty()) {
                reportProgress(progress.positionMs, progress.durationMs, progress.isWatched)
                reportPlaybackStop()
            }
        }
        
        currentItemId = ""
        lastReportedPosition = 0L
        if (BuildConfig.DEBUG) {
            Log.d("PlaybackProgressManager", "Stopped tracking")
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // Report current progress when app is paused
        coroutineScope?.launch {
            val progress = _playbackProgress.value
            if (progress.itemId.isNotEmpty()) {
                reportProgress(progress.positionMs, progress.durationMs, progress.isWatched)
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        stopTracking()
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
            // In a real implementation, you would load the existing progress from the server
            // For now, we'll simulate this by checking the item's userData
            // This would typically be done through a dedicated API endpoint
            if (BuildConfig.DEBUG) {
                Log.d("PlaybackProgressManager", "Loading existing progress for item: $itemId")
            }
            
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to load existing progress", e)
        }
    }
    
    private suspend fun reportProgress(positionMs: Long, durationMs: Long, isWatched: Boolean) {
        try {
            val server = repository.getCurrentServer() ?: return
            val positionTicks = positionMs * 10_000L // Convert to ticks (100ns units)
            
            // This would be the proper Jellyfin API call for reporting progress
            // For now, we'll simulate the API call
            if (BuildConfig.DEBUG) {
                Log.d(
                    "PlaybackProgressManager",
                    "Reporting progress: ${positionMs}ms / ${durationMs}ms (${(positionMs.toFloat() / durationMs * 100).toInt()}%)"
                )
            }
            
            _playbackProgress.value = _playbackProgress.value.copy(
                lastSyncTime = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to report progress", e)
        }
    }
    
    private suspend fun reportPlaybackStart() {
        try {
            val server = repository.getCurrentServer() ?: return
            
            // Report playback start to Jellyfin
            if (BuildConfig.DEBUG) {
                Log.d("PlaybackProgressManager", "Reporting playback start for: $currentItemId")
            }
            
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to report playback start", e)
        }
    }
    
    private suspend fun reportPlaybackStop() {
        try {
            val server = repository.getCurrentServer() ?: return
            
            // Report playback stop to Jellyfin
            if (BuildConfig.DEBUG) {
                Log.d("PlaybackProgressManager", "Reporting playback stop for: $currentItemId")
            }
            
        } catch (e: Exception) {
            Log.e("PlaybackProgressManager", "Failed to report playback stop", e)
        }
    }
    
    /**
     * Gets the resume position for an item based on previous playback progress
     */
    suspend fun getResumePosition(itemId: String): Long {
        return try {
            // In a real implementation, this would query the Jellyfin API for the item's user data
            // and return the last playback position
            0L
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