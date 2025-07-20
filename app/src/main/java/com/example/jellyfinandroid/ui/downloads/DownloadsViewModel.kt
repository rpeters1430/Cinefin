package com.example.jellyfinandroid.ui.downloads

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jellyfinandroid.data.offline.DownloadProgress
import com.example.jellyfinandroid.data.offline.DownloadStatus
import com.example.jellyfinandroid.data.offline.OfflineDownload
import com.example.jellyfinandroid.data.offline.OfflineDownloadManager
import com.example.jellyfinandroid.data.offline.OfflinePlaybackManager
import com.example.jellyfinandroid.data.offline.OfflineStorageInfo
import com.example.jellyfinandroid.ui.player.VideoPlayerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: OfflineDownloadManager,
    private val playbackManager: OfflinePlaybackManager
) : ViewModel() {
    
    val downloads: StateFlow<List<OfflineDownload>> = downloadManager.downloads
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = downloadManager.downloadProgress
    
    val storageInfo: StateFlow<OfflineStorageInfo?> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(playbackManager.getOfflineStorageInfo())
            kotlinx.coroutines.delay(5000L) // Update every 5 seconds
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    
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
                    streamUrl = "file://${download.localFilePath}",
                    startPosition = withContext(Dispatchers.IO) {
                        com.example.jellyfinandroid.data.PlaybackPositionStore.getPlaybackPosition(context, download.jellyfinItemId)
                    }
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

