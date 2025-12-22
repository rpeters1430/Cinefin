package com.rpeters.jellyfin.data.offline

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jellyfin.sdk.model.api.BaseItemDto
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// DataStore extension
private val Context.offlineDownloadsDataStore: DataStore<Preferences> by preferencesDataStore(name = "offline_downloads")

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val okHttpClient: OkHttpClient,
) {

    private val _downloads = MutableStateFlow<List<OfflineDownload>>(emptyList())
    val downloads: StateFlow<List<OfflineDownload>> = _downloads.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val supervisorJob = SupervisorJob()
    private val downloadScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val DOWNLOADS_KEY = "offline_downloads"
        private const val JELLYFIN_OFFLINE_DIR = "JellyfinOffline"
        private const val CHUNK_SIZE = 8192
    }

    init {
        // Load existing downloads on initialization
        downloadScope.launch {
            loadDownloads()
        }
    }

    fun startDownload(
        item: BaseItemDto,
        quality: VideoQuality? = null,
        downloadUrl: String? = null,
    ): String {
        val download = createDownload(item, quality, downloadUrl)

        downloadScope.launch {
            addDownload(download)
            executeDownload(download)
        }

        return download.id
    }

    fun pauseDownload(downloadId: String) {
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)

        downloadScope.launch {
            updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
        }
    }

    fun resumeDownload(downloadId: String) {
        val download = _downloads.value.find { it.id == downloadId }
        if (download != null && download.status == DownloadStatus.PAUSED) {
            downloadScope.launch {
                executeDownload(download)
            }
        }
    }

    fun deleteDownload(downloadId: String) {
        downloadScope.launch {
            val download = _downloads.value.find { it.id == downloadId }
            download?.let {
                // Cancel if downloading
                downloadJobs[downloadId]?.cancel()
                downloadJobs.remove(downloadId)

                // Delete file
                deleteDownloadFile(it)

                // Remove from list
                removeDownload(downloadId)
            }
        }
    }

    fun getDownloadFile(downloadId: String): File? {
        val download = _downloads.value.find { it.id == downloadId }
        return download?.let { File(it.localFilePath) }
    }

    fun isItemDownloaded(itemId: String): Boolean {
        return _downloads.value.any {
            it.jellyfinItemId == itemId && it.status == DownloadStatus.COMPLETED
        }
    }

    fun getAvailableStorage(): Long {
        val offlineDir = getOfflineDirectory()
        return offlineDir.freeSpace
    }

    fun getUsedStorage(): Long {
        val offlineDir = getOfflineDirectory()
        return calculateDirectorySize(offlineDir)
    }

    private suspend fun executeDownload(download: OfflineDownload) {
        val job = downloadScope.launch {
            try {
                updateDownloadStatus(download.id, DownloadStatus.DOWNLOADING)

                val request = Request.Builder()
                    .url(download.downloadUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()

                // Use response in a use block to ensure proper resource cleanup
                response.use {
                    if (!it.isSuccessful) {
                        throw IOException("Download failed: ${it.code}")
                    }

                    downloadFile(it, download)
                }
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Log.d("OfflineDownloadManager", "Download cancelled: ${download.id}")
                }
                // Re-throw to propagate cancellation up the coroutine hierarchy
                throw e
            }
        }

        downloadJobs[download.id] = job
    }

    private suspend fun downloadFile(response: Response, download: OfflineDownload) {
        // Validate response body exists
        val body = response.body
            ?: throw IOException("Response body is null - cannot download file")

        val contentLength = body.contentLength()
        val outputFile = File(download.localFilePath)

        // Create parent directories if they don't exist
        outputFile.parentFile?.mkdirs()

        // Handle resume for partial downloads
        val existingSize = if (outputFile.exists()) outputFile.length() else 0L
        val startByte = if (download.status == DownloadStatus.PAUSED) existingSize else 0L

        body.byteStream().use { inputStream ->
            FileOutputStream(outputFile, download.status == DownloadStatus.PAUSED).use { outputStream ->

                if (startByte > 0) {
                    inputStream.skip(startByte)
                }

                val buffer = ByteArray(CHUNK_SIZE)
                var totalBytesRead = startByte
                var bytesRead: Int
                val startTime = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!currentCoroutineContext().isActive) break // Check for cancellation

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Update progress
                    val currentTime = System.currentTimeMillis()
                    val elapsedTime = currentTime - startTime
                    val speed = if (elapsedTime > 0) (totalBytesRead * 1000L) / elapsedTime else 0L
                    val remainingBytes = contentLength - totalBytesRead
                    val remainingTime = if (speed > 0) (remainingBytes * 1000L) / speed else null

                    val progress = DownloadProgress(
                        downloadId = download.id,
                        downloadedBytes = totalBytesRead,
                        totalBytes = contentLength,
                        progressPercent = if (contentLength > 0) (totalBytesRead.toFloat() / contentLength * 100f) else 0f,
                        downloadSpeedBps = speed,
                        remainingTimeMs = remainingTime,
                    )

                    updateDownloadProgress(progress)
                    updateDownloadBytes(download.id, totalBytesRead)
                }

                // Mark as completed if still active and either:
                // 1. We've read the expected content length, OR
                // 2. Content length is unknown (negative) and stream ended naturally
                if (currentCoroutineContext().isActive &&
                    (totalBytesRead == contentLength || contentLength < 0)
                ) {
                    updateDownloadStatus(download.id, DownloadStatus.COMPLETED)
                    downloadJobs.remove(download.id)
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "OfflineDownloadManager",
                            "Download completed: ${download.itemName} ($totalBytesRead bytes)",
                        )
                    }
                }
            }
        }
    }

    private fun createDownload(
        item: BaseItemDto,
        quality: VideoQuality?,
        downloadUrl: String?,
    ): OfflineDownload {
        val url = downloadUrl ?: repository.getStreamUrl(item.id.toString()) ?: ""
        val fileName = "${item.name?.replace(Regex("[^a-zA-Z0-9.-]"), "_")}_${System.currentTimeMillis()}.mp4"
        val localPath = File(getOfflineDirectory(), fileName).absolutePath

        return OfflineDownload(
            jellyfinItemId = item.id.toString(),
            itemName = item.name ?: context.getString(R.string.unknown),
            itemType = item.type?.toString() ?: "Video",
            downloadUrl = url,
            localFilePath = localPath,
            fileSize = 0L, // Will be updated during download
            quality = quality,
            downloadStartTime = System.currentTimeMillis(),
        )
    }

    private fun getOfflineDirectory(): File {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val offlineDir = File(externalDir, JELLYFIN_OFFLINE_DIR)
        if (!offlineDir.exists()) {
            offlineDir.mkdirs()
        }
        return offlineDir
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun deleteDownloadFile(download: OfflineDownload) {
        try {
            val file = File(download.localFilePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("OfflineDownloadManager", "Failed to delete file: ${download.localFilePath}", e)
        }
    }

    private suspend fun loadDownloads() {
        try {
            context.offlineDownloadsDataStore.data.collect { preferences ->
                val downloadsJson = preferences[stringPreferencesKey(DOWNLOADS_KEY)] ?: "[]"
                val downloads = json.decodeFromString<List<OfflineDownload>>(downloadsJson)
                _downloads.update { downloads }
            }
        } catch (e: Exception) {
            Log.e("OfflineDownloadManager", "Failed to load downloads", e)
            _downloads.update { emptyList() }
        }
    }

    private suspend fun saveDownloads() {
        try {
            context.offlineDownloadsDataStore.edit { preferences ->
                val downloadsJson = json.encodeToString(_downloads.value)
                preferences[stringPreferencesKey(DOWNLOADS_KEY)] = downloadsJson
            }
        } catch (e: Exception) {
            Log.e("OfflineDownloadManager", "Failed to save downloads", e)
        }
    }

    private suspend fun addDownload(download: OfflineDownload) {
        _downloads.update { it + download }
        saveDownloads()
    }

    private suspend fun removeDownload(downloadId: String) {
        _downloads.update { currentDownloads -> currentDownloads.filter { it.id != downloadId } }
        saveDownloads()
    }

    private suspend fun updateDownloadStatus(downloadId: String, status: DownloadStatus) {
        _downloads.update { currentDownloads ->
            currentDownloads.map { download ->
                if (download.id == downloadId) {
                    download.copy(
                        status = status,
                        downloadCompleteTime = if (status == DownloadStatus.COMPLETED) System.currentTimeMillis() else null,
                    )
                } else {
                    download
                }
            }
        }
        saveDownloads()
    }

    private suspend fun updateDownloadBytes(downloadId: String, downloadedBytes: Long) {
        _downloads.update { currentDownloads ->
            currentDownloads.map { download ->
                if (download.id == downloadId) {
                    download.copy(downloadedBytes = downloadedBytes)
                } else {
                    download
                }
            }
        }
        saveDownloads()
    }

    private fun updateDownloadProgress(progress: DownloadProgress) {
        _downloadProgress.update { it + (progress.downloadId to progress) }
    }

    /**
     * Clean up all resources and cancel ongoing downloads.
     * Should be called when the application is terminating or when the manager is no longer needed.
     */
    fun cleanup() {
        // Cancel all ongoing download jobs
        downloadJobs.values.forEach { job ->
            if (job.isActive) {
                job.cancel("OfflineDownloadManager cleanup")
            }
        }
        downloadJobs.clear()

        // Clear progress state
        _downloadProgress.update { emptyMap() }

        // Cancel the supervisor job and scope
        supervisorJob.cancel("OfflineDownloadManager cleanup")
    }

    /**
     * Cancel a specific download and clean up its resources.
     */
    fun cancelDownload(downloadId: String) {
        downloadJobs[downloadId]?.let { job ->
            if (job.isActive) {
                job.cancel("Download cancelled by user")
            }
            downloadJobs.remove(downloadId)
        }

        // Remove from progress tracking
        _downloadProgress.update { it - downloadId }

        // Update download status to cancelled
        _downloads.update { currentDownloads ->
            currentDownloads.map { download ->
                if (download.id == downloadId) {
                    download.copy(status = DownloadStatus.CANCELLED)
                } else {
                    download
                }
            }
        }

        downloadScope.launch {
            saveDownloads()
        }
    }
}
