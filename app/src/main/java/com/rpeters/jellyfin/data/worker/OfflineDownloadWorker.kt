package com.rpeters.jellyfin.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class OfflineDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val offlineDownloadManager: OfflineDownloadManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
        if (downloadId.isNullOrBlank()) {
            SecureLogger.w(TAG, "Missing download ID input")
            return Result.failure()
        }

        val existingDownload = offlineDownloadManager.getDownloadById(downloadId)
        if (existingDownload == null) {
            SecureLogger.w(TAG, "Skipping orphan offline download work: $downloadId")
            return Result.failure()
        }

        ensureNotificationChannel()
        val initialName = existingDownload.itemName
        val notificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        if (notificationsEnabled) {
            try {
                setForeground(createForegroundInfo(downloadId, initialName, 0, 0L, 0L, true))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                SecureLogger.w(TAG, "Could not promote download to foreground", e)
            }
        }
        var lastForegroundUpdateAtMs = System.currentTimeMillis()
        var lastForegroundPercent = 0

        return try {
            when (
                offlineDownloadManager.executeDownload(downloadId) { download, progress ->
                    if (!notificationsEnabled) return@executeDownload
                    val percent = progress.progressPercent.toInt().coerceIn(0, 100)
                    val now = System.currentTimeMillis()
                    val shouldUpdate = shouldUpdateForegroundNotification(
                        nowMs = now,
                        lastUpdateMs = lastForegroundUpdateAtMs,
                        percent = percent,
                        lastPercent = lastForegroundPercent,
                    )
                    if (!shouldUpdate) return@executeDownload

                    lastForegroundUpdateAtMs = now
                    lastForegroundPercent = percent
                    setForeground(
                        createForegroundInfo(
                            downloadId = downloadId,
                            itemName = download.itemName,
                            progressPercent = percent,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            indeterminate = progress.totalBytes <= 0L && !progress.isTranscoding,
                            isTranscoding = progress.isTranscoding,
                            transcodingProgress = progress.transcodingProgress,
                        ),
                    )
                }
            ) {
                OfflineDownloadManager.DownloadExecutionResult.SUCCESS -> {
                    if (notificationsEnabled) {
                        showCompletionNotification(downloadId, success = true)
                    }
                    Result.success()
                }
                OfflineDownloadManager.DownloadExecutionResult.RETRY -> Result.retry()
                OfflineDownloadManager.DownloadExecutionResult.FAILURE -> {
                    if (notificationsEnabled) {
                        showCompletionNotification(downloadId, success = false)
                    }
                    Result.failure()
                }
                OfflineDownloadManager.DownloadExecutionResult.CANCELLED -> Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Offline download work failed for $downloadId", e)
            Result.retry()
        }
    }

    private fun createForegroundInfo(
        downloadId: String,
        itemName: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        indeterminate: Boolean,
        isTranscoding: Boolean = false,
        transcodingProgress: Float? = null,
    ): ForegroundInfo {
        val pauseIntent = Intent(applicationContext, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_PAUSE
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            downloadId.hashCode() + 1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(applicationContext, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            downloadId.hashCode() + 2,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Downloading")
            .setContentText(itemName)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (isTranscoding && transcodingProgress != null) {
                    // Phase 1: Server is transcoding
                    val tcPercent = transcodingProgress.toInt().coerceIn(0, 100)
                    setProgress(100, tcPercent, false)
                    setSubText("Server transcoding: $tcPercent% \u00b7 Downloaded: ${formatBytes(downloadedBytes)}")
                } else if (totalBytes > 0L) {
                    // Phase 2: Normal download with known size
                    setProgress(100, progressPercent, false)
                    setSubText("${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}")
                } else {
                    // Fallback: indeterminate
                    setProgress(100, 0, true)
                    setSubText("Preparing download")
                }
            }
            .addAction(R.drawable.ic_launcher_monochrome, "Pause", pausePendingIntent)
            .addAction(R.drawable.ic_launcher_monochrome, "Cancel", cancelPendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun showCompletionNotification(downloadId: String, success: Boolean) {
        val download = offlineDownloadManager.getDownloadById(downloadId)
        val title = if (success) "Download complete" else "Download failed"
        val itemName = download?.itemName ?: "Download"
        val message = if (success) itemName else "Could not download $itemName"
        val notificationId = NOTIFICATION_ID_COMPLETION_BASE + downloadId.hashCode()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_COMPLETED)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Offline downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows active offline download progress"
            setShowBadge(false)
        }

        val completedChannel = NotificationChannel(
            CHANNEL_COMPLETED,
            "Download status",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Shows completed and failed offline downloads"
        }

        manager.createNotificationChannel(downloadChannel)
        manager.createNotificationChannel(completedChannel)
    }

    private fun shouldUpdateForegroundNotification(
        nowMs: Long,
        lastUpdateMs: Long,
        percent: Int,
        lastPercent: Int,
    ): Boolean {
        if (percent >= 100) return true
        val elapsed = nowMs - lastUpdateMs
        val percentDelta = kotlin.math.abs(percent - lastPercent)
        return elapsed >= FOREGROUND_UPDATE_MIN_INTERVAL_MS && percentDelta >= FOREGROUND_UPDATE_MIN_PERCENT_DELTA
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            String.format("%.1f %s", value, units[unitIndex])
        }
    }

    companion object {
        private const val TAG = "OfflineDownloadWorker"
        const val KEY_DOWNLOAD_ID = "offline_download_id"
        private const val CHANNEL_DOWNLOADS = "offline_downloads_progress"
        private const val CHANNEL_COMPLETED = "offline_downloads_completed"
        private const val NOTIFICATION_ID_FOREGROUND = 3901
        private const val NOTIFICATION_ID_COMPLETION_BASE = 4100
        private const val FOREGROUND_UPDATE_MIN_INTERVAL_MS = 1500L
        private const val FOREGROUND_UPDATE_MIN_PERCENT_DELTA = 2

        fun inputData(downloadId: String): Data = Data.Builder()
            .putString(KEY_DOWNLOAD_ID, downloadId)
            .build()
    }
}
