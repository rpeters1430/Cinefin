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
        val cid = cid(downloadId)
        SecureLogger.i(TAG, "cid=$cid worker starting for downloadId=$downloadId, itemName=${existingDownload.itemName}")

        ensureNotificationChannel()
        val initialName = existingDownload.itemName
        // Always promote to a foreground service so the download survives while backgrounded.
        // The OS silently drops the notification if POST_NOTIFICATIONS is not granted — that is
        // fine. What matters is that the WorkManager service itself stays alive.
        try {
            setForeground(createForegroundInfo(downloadId, initialName, 0, 0L, 0L, true))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SecureLogger.w(TAG, "Could not promote download to foreground: ${e.message}")
        }
        val notificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        SecureLogger.i(TAG, "cid=$cid notification state for downloadId=$downloadId: enabled=$notificationsEnabled")
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
                    SecureLogger.d(
                        TAG,
                        "cid=$cid foreground progress update: downloadId=$downloadId, percent=$percent, bytes=${progress.downloadedBytes}, total=${progress.totalBytes}, transcoding=${progress.isTranscoding}",
                    )
                    setForeground(
                        createForegroundInfo(
                            downloadId = downloadId,
                            itemName = download.itemName,
                            progressPercent = percent,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                            indeterminate = progress.totalBytes <= 0L,
                            isTranscoding = progress.isTranscoding,
                            transcodingProgress = progress.transcodingProgress,
                            transcodingEtaMs = progress.transcodingEtaMs,
                        ),
                    )
                }
            ) {
                OfflineDownloadManager.DownloadExecutionResult.SUCCESS -> {
                    SecureLogger.i(TAG, "cid=$cid worker finished SUCCESS for downloadId=$downloadId")
                    if (notificationsEnabled) {
                        showCompletionNotification(downloadId, success = true)
                    }
                    Result.success()
                }
                OfflineDownloadManager.DownloadExecutionResult.RETRY -> {
                    SecureLogger.w(TAG, "cid=$cid worker requested RETRY for downloadId=$downloadId")
                    Result.retry()
                }
                OfflineDownloadManager.DownloadExecutionResult.FAILURE -> {
                    SecureLogger.e(TAG, "cid=$cid worker finished FAILURE for downloadId=$downloadId")
                    if (notificationsEnabled) {
                        showCompletionNotification(downloadId, success = false)
                    }
                    Result.failure()
                }
                OfflineDownloadManager.DownloadExecutionResult.CANCELLED -> {
                    SecureLogger.w(TAG, "cid=$cid worker cancelled for downloadId=$downloadId")
                    Result.failure()
                }
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
        transcodingEtaMs: Long? = null,
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
                if (isTranscoding) {
                    val tcPercent = transcodingProgress?.toInt()?.coerceIn(0, 100)
                    if (tcPercent != null) {
                        setProgress(100, tcPercent, false)
                    } else {
                        setProgress(100, 0, true)
                    }
                    val etaText = transcodingEtaMs?.let { " · ETA ${formatDuration(it)}" } ?: ""
                    val progressText = tcPercent?.let { "$it%" } ?: "starting"
                    setSubText("Server transcoding: $progressText$etaText")
                } else if (totalBytes > 0L) {
                    // Known or estimated size - show determinate progress
                    setProgress(100, progressPercent, false)
                    setSubText("${formatBytes(downloadedBytes)} / ~${formatBytes(totalBytes)}")
                } else if (downloadedBytes > 0L) {
                    // Unknown size, data flowing - show downloaded amount
                    setProgress(100, 0, true)
                    setSubText("Downloaded ${formatBytes(downloadedBytes)}")
                } else {
                    // Initial state before data starts flowing
                    setProgress(100, 0, true)
                    setSubText("Preparing download")
                }
            }
            .addAction(R.drawable.ic_launcher_monochrome, "Pause", pausePendingIntent)
            .addAction(R.drawable.ic_launcher_monochrome, "Cancel", cancelPendingIntent)
            .build()

        val notificationId = NOTIFICATION_ID_FOREGROUND_BASE + (downloadId.hashCode() and 0x7FFFFFFF) % 1000
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
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

        SecureLogger.i(
            TAG,
            "cid=${cid(downloadId)} posting completion notification: downloadId=$downloadId, success=$success, notificationId=$notificationId",
        )
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

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "<1m"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private fun cid(downloadId: String): String = downloadId.take(8)

    companion object {
        private const val TAG = "OfflineDownloadWorker"
        const val KEY_DOWNLOAD_ID = "offline_download_id"
        private const val CHANNEL_DOWNLOADS = "offline_downloads_progress"
        private const val CHANNEL_COMPLETED = "offline_downloads_completed"
        private const val NOTIFICATION_ID_FOREGROUND_BASE = 3000
        private const val NOTIFICATION_ID_COMPLETION_BASE = 4100
        private const val FOREGROUND_UPDATE_MIN_INTERVAL_MS = 1500L
        private const val FOREGROUND_UPDATE_MIN_PERCENT_DELTA = 2

        fun inputData(downloadId: String): Data = Data.Builder()
            .putString(KEY_DOWNLOAD_ID, downloadId)
            .build()
    }
}
