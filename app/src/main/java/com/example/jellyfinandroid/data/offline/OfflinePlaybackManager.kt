package com.example.jellyfinandroid.data.offline

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class OfflinePlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: OfflineDownloadManager,
) {

    private val downloads: StateFlow<List<OfflineDownload>> = downloadManager.downloads

    fun isOfflinePlaybackAvailable(itemId: String): Boolean {
        return downloads.value.any { download ->
            download.jellyfinItemId == itemId &&
                download.status == DownloadStatus.COMPLETED &&
                File(download.localFilePath).exists()
        }
    }

    fun getOfflineMediaItem(itemId: String): MediaItem? {
        val download = downloads.value.find { download ->
            download.jellyfinItemId == itemId &&
                download.status == DownloadStatus.COMPLETED
        } ?: return null

        val file = File(download.localFilePath)
        if (!file.exists()) {
            Log.w("OfflinePlaybackManager", "Offline file not found: ${download.localFilePath}")
            return null
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(download.itemName)
            .setDisplayTitle(download.itemName)
            .build()

        return MediaItem.Builder()
            .setUri(file.toURI().toString())
            .setMediaMetadata(metadata)
            .build()
    }

    fun createOfflineMediaSource(itemId: String, exoPlayer: ExoPlayer): MediaSource? {
        val download = downloads.value.find { download ->
            download.jellyfinItemId == itemId &&
                download.status == DownloadStatus.COMPLETED
        } ?: return null

        val file = File(download.localFilePath)
        if (!file.exists()) {
            Log.w("OfflinePlaybackManager", "Offline file not found: ${download.localFilePath}")
            return null
        }

        val dataSourceFactory = DefaultDataSourceFactory(context, "JellyfinAndroid")

        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(file.toURI().toString()))
    }

    fun getOfflineDownload(itemId: String): OfflineDownload? {
        return downloads.value.find { download ->
            download.jellyfinItemId == itemId &&
                download.status == DownloadStatus.COMPLETED
        }
    }

    fun getAllOfflineDownloads(): List<OfflineDownload> {
        return downloads.value.filter { it.status == DownloadStatus.COMPLETED }
    }

    fun validateOfflineFiles(): List<String> {
        val invalidDownloads = mutableListOf<String>()

        downloads.value.forEach { download ->
            if (download.status == DownloadStatus.COMPLETED) {
                val file = File(download.localFilePath)
                if (!file.exists()) {
                    invalidDownloads.add(download.id)
                    Log.w("OfflinePlaybackManager", "Missing offline file for download: ${download.id}")
                }
            }
        }

        return invalidDownloads
    }

    fun getOfflineStorageInfo(): OfflineStorageInfo {
        val totalSpace = downloadManager.getAvailableStorage()
        val usedSpace = downloadManager.getUsedStorage()
        val completedDownloads = downloads.value.filter { it.status == DownloadStatus.COMPLETED }

        return OfflineStorageInfo(
            totalSpaceBytes = totalSpace,
            usedSpaceBytes = usedSpace,
            availableSpaceBytes = totalSpace - usedSpace,
            downloadCount = completedDownloads.size,
            totalDownloadSizeBytes = completedDownloads.sumOf { it.fileSize },
        )
    }
}

data class OfflineStorageInfo(
    val totalSpaceBytes: Long,
    val usedSpaceBytes: Long,
    val availableSpaceBytes: Long,
    val downloadCount: Int,
    val totalDownloadSizeBytes: Long,
) {
    val usedSpacePercentage: Float
        get() = if (totalSpaceBytes > 0) (usedSpaceBytes.toFloat() / totalSpaceBytes * 100f) else 0f
}
