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

    fun startDownload(
        item: BaseItemDto,
        quality: VideoQuality? = null,
        downloadUrl: String? = null,
    ) {
        viewModelScope.launch {
            downloadManager.startDownload(item, quality, downloadUrl)
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
