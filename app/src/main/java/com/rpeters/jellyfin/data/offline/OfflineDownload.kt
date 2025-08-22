package com.rpeters.jellyfin.data.offline

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class OfflineDownload(
    val id: String = UUID.randomUUID().toString(),
    val jellyfinItemId: String,
    val itemName: String,
    val itemType: String,
    val downloadUrl: String,
    val localFilePath: String,
    val fileSize: Long,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val quality: VideoQuality? = null,
    val thumbnailUrl: String? = null,
    val downloadStartTime: Long? = null,
    val downloadCompleteTime: Long? = null,
    val lastModified: Long = System.currentTimeMillis(),
)

@Serializable
data class VideoQuality(
    val id: String,
    val label: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DownloadProgress(
    val downloadId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progressPercent: Float,
    val downloadSpeedBps: Long,
    val remainingTimeMs: Long?,
)
