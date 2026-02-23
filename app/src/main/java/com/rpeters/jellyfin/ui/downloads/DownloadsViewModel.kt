package com.rpeters.jellyfin.ui.downloads

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.offline.DownloadProgress
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.data.offline.OfflinePlaybackManager
import com.rpeters.jellyfin.data.offline.OfflineStorageInfo
import com.rpeters.jellyfin.data.offline.VideoQuality
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.ui.player.VideoPlayerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: OfflineDownloadManager,
    private val playbackManager: OfflinePlaybackManager,
    private val repository: JellyfinRepository,
) : ViewModel() {

    val downloads: StateFlow<List<OfflineDownload>> = downloadManager.downloads
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = downloadManager.downloadProgress

    val storageInfo: StateFlow<OfflineStorageInfo?> = kotlinx.coroutines.flow.flow {
        while (currentCoroutineContext().isActive) {
            emit(playbackManager.getOfflineStorageInfo())
            kotlinx.coroutines.delay(5000L) // Update every 5 seconds
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    companion object {
        val QUALITY_PRESETS = listOf(
            VideoQuality(id = "original", label = "Original Quality", bitrate = 0, width = 0, height = 0),
            VideoQuality(id = "high", label = "High (1080p, 6 Mbps)", bitrate = 6_000_000, width = 1920, height = 1080, audioBitrate = 192_000, audioChannels = 2),
            VideoQuality(id = "medium", label = "Medium (720p, 3 Mbps)", bitrate = 3_000_000, width = 1280, height = 720, audioBitrate = 128_000, audioChannels = 2),
            VideoQuality(id = "low", label = "Low (480p, 1 Mbps)", bitrate = 1_000_000, width = 854, height = 480, audioBitrate = 96_000, audioChannels = 2),
        )
    }

    fun getAvailableQualityPresets(item: BaseItemDto): List<VideoQuality> {
        val mediaSource = item.mediaSources?.firstOrNull()
        val videoStream = mediaSource?.mediaStreams?.find { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
        val originalHeight = videoStream?.height ?: 0
        val originalWidth = videoStream?.width ?: 0

        return QUALITY_PRESETS.filter { preset ->
            preset.id == "original" ||
                (preset.height < originalHeight && originalHeight > 0) ||
                (preset.width < originalWidth && originalWidth > 0)
        }
    }

    fun startDownload(
        item: BaseItemDto,
        quality: VideoQuality? = null,
        downloadUrl: String? = null,
    ) {
        viewModelScope.launch {
            val itemId = item.id.toString()
            val url = withContext(Dispatchers.IO) {
                if (quality != null && quality.id != "original") {
                    // Use H.264 for offline transcoded downloads for maximum Jellyfin server/device compatibility.
                    repository.getTranscodedStreamUrl(
                        itemId = itemId,
                        maxBitrate = quality.bitrate,
                        maxWidth = quality.width,
                        maxHeight = quality.height,
                        videoCodec = "h264",
                        audioCodec = "aac",
                        audioBitrate = quality.audioBitrate,
                        audioChannels = quality.audioChannels ?: 2,
                        container = "mp4",
                    ) ?: repository.getDownloadUrl(itemId)
                } else {
                    downloadUrl ?: repository.getDownloadUrl(itemId)
                }
            }

            downloadManager.startDownload(item, quality, url)
        }
    }

    fun pauseDownload(downloadId: String) {
        downloadManager.pauseDownload(downloadId)
    }

    fun resumeDownload(downloadId: String) {
        downloadManager.resumeDownload(downloadId)
    }

    fun cancelDownload(downloadId: String) {
        downloadManager.cancelDownload(downloadId)
    }

    fun deleteDownload(downloadId: String) {
        downloadManager.deleteDownload(downloadId)
    }

    fun pauseAllDownloads() {
        viewModelScope.launch {
            downloads.value
                .filter { it.status == DownloadStatus.DOWNLOADING }
                .forEach { pauseDownload(it.id) }
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            downloads.value
                .filter { it.status == DownloadStatus.COMPLETED }
                .forEach { deleteDownload(it.id) }
        }
    }

    fun playOfflineContent(itemId: String) {
        viewModelScope.launch {
            val download = playbackManager.getOfflineDownload(itemId)
            if (download != null) {
                val intent = VideoPlayerActivity.createIntent(
                    context = context,
                    itemId = download.jellyfinItemId,
                    itemName = download.itemName,
                    startPosition = withContext(Dispatchers.IO) {
                        com.rpeters.jellyfin.data.PlaybackPositionStore.getPlaybackPosition(context, download.jellyfinItemId)
                    },
                    forceOffline = true,
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    fun validateOfflineFiles() {
        viewModelScope.launch {
            val invalidIds = playbackManager.validateOfflineFiles()
            invalidIds.forEach { deleteDownload(it) }
        }
    }
}
