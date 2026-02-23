package com.rpeters.jellyfin.data.offline

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
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
                resolveReadableOfflineFile(download, logWarnings = false) != null
        }
    }

    fun getOfflineMediaItem(itemId: String): MediaItem? {
        val download = downloads.value.find { download ->
            download.jellyfinItemId == itemId &&
                download.status == DownloadStatus.COMPLETED
        } ?: return null

        val file = resolveReadableOfflineFile(download) ?: return null

        val metadata = MediaMetadata.Builder()
            .setTitle(download.itemName)
            .setDisplayTitle(download.itemName)
            .build()

        return MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(metadata)
            .build()
    }

    fun createOfflineMediaSource(itemId: String, exoPlayer: ExoPlayer): MediaSource? {
        val download = downloads.value.find { download ->
            download.jellyfinItemId == itemId &&
                download.status == DownloadStatus.COMPLETED
        } ?: return null

        val file = resolveReadableOfflineFile(download) ?: return null

        val dataSourceFactory = DefaultDataSource.Factory(context)

        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
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
                if (resolveReadableOfflineFile(download, logWarnings = false) == null) {
                    invalidDownloads.add(download.id)
                    Log.w("OfflinePlaybackManager", "Missing offline file for download: ${download.id}")
                }
            }
        }

        return invalidDownloads
    }

    private fun resolveReadableOfflineFile(
        download: OfflineDownload,
        logWarnings: Boolean = true,
    ): File? {
        val file = try {
            File(download.localFilePath).canonicalFile
        } catch (e: IOException) {
            if (logWarnings) {
                Log.w("OfflinePlaybackManager", "Invalid offline file path: ${download.localFilePath}", e)
            }
            return null
        }

        if (!file.exists() || !file.isFile() || !file.canRead()) {
            if (logWarnings) {
                Log.w("OfflinePlaybackManager", "Offline file unavailable: ${download.localFilePath}")
            }
            return null
        }

        if (!isInAppSpecificStorage(file)) {
            if (logWarnings) {
                Log.w(
                    "OfflinePlaybackManager",
                    "Rejected non-app-specific offline path: ${download.localFilePath}",
                )
            }
            return null
        }

        return file
    }

    private fun isInAppSpecificStorage(file: File): Boolean {
        val filePath = file.path
        return appSpecificStorageRoots().any { root ->
            val rootPath = root.path
            filePath == rootPath || filePath.startsWith("$rootPath${File.separator}")
        }
    }

    private fun appSpecificStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let(roots::add)
        context.getExternalFilesDir(null)?.let(roots::add)
        roots.add(context.filesDir)

        return roots.mapNotNull { root ->
            try {
                root.canonicalFile
            } catch (_: IOException) {
                null
            }
        }
    }

    fun getOfflineStorageInfo(): OfflineStorageInfo {
        val totalSpace = downloadManager.getTotalStorage()
        val freeSpace = downloadManager.getAvailableStorage()
        val usedSpace = downloadManager.getUsedStorage()
        val completedDownloads = downloads.value.filter { it.status == DownloadStatus.COMPLETED }

        return OfflineStorageInfo(
            totalSpaceBytes = totalSpace,
            usedSpaceBytes = usedSpace,
            availableSpaceBytes = freeSpace,
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
