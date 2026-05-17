package com.rpeters.jellyfin.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.data.preferences.DownloadPreferencesRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.math.roundToLong

/**
 * Worker that automatically downloads the next unplayed episodes of a show.
 */
@HiltWorker
class SmartDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: JellyfinMediaRepository,
    private val offlineDownloadManager: OfflineDownloadManager,
    private val downloadPreferencesRepository: DownloadPreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = downloadPreferencesRepository.preferences.first()
        if (!prefs.smartDownloadsEnabled) {
            return Result.success()
        }

        SecureLogger.d(TAG, "Starting smart download check")

        // 1. Clean up watched episodes
        if (prefs.autoCleanEnabled) {
            cleanUpWatchedEpisodes()
        }

        // 2. Fetch Next Up items
        return when (val nextUpResult = mediaRepository.getNextUp(limit = 10)) {
            is ApiResult.Success -> {
                val nextUpItems = nextUpResult.data
                
                // 3. For each show in next up, ensure the next episode is downloaded
                // We'll only download the first 3 items in Next Up to avoid mass downloads
                nextUpItems.take(3).forEach { episode ->
                    if (episode.type == BaseItemKind.EPISODE) {
                        val itemId = episode.id.toString()
                        if (!offlineDownloadManager.isItemDownloaded(itemId)) {
                            SecureLogger.i(TAG, "Smart downloading next episode: ${episode.seriesName} - ${episode.name}")
                            offlineDownloadManager.startDownload(
                                item = episode,
                                quality = null // Uses default quality
                            )
                        }
                    }
                }
                Result.success()
            }
            is ApiResult.Error -> {
                SecureLogger.e(TAG, "Failed to fetch next up for smart downloads: ${nextUpResult.message}")
                Result.retry()
            }
            else -> Result.success()
        }
    }

    private fun cleanUpWatchedEpisodes() {
        val completedDownloads = offlineDownloadManager.getCompletedDownloads()
        val watchedDownloads = completedDownloads.filter { download ->
            val runtimeMs = download.runtimeTicks?.let { it / 10_000L } ?: return@filter false
            val watchedThresholdMs = (runtimeMs * WATCHED_CLEANUP_THRESHOLD).roundToLong()
            runtimeMs > 0 && (download.lastPlaybackPositionMs ?: 0L) >= watchedThresholdMs
        }

        watchedDownloads.forEach { download ->
            SecureLogger.i(TAG, "Smart cleaning up watched episode: ${download.itemName}")
            offlineDownloadManager.deleteDownload(download.id)
        }
    }

    companion object {
        private const val TAG = "SmartDownloadWorker"
        private const val WATCHED_CLEANUP_THRESHOLD = 0.9
        const val WORK_NAME = "smart-download-worker"

        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED) // Only over Wi-Fi
                .setRequiresBatteryNotLow(true)
                .build()

            val request = androidx.work.OneTimeWorkRequestBuilder<SmartDownloadWorker>()
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
