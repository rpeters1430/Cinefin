package com.rpeters.jellyfin.data.offline

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import com.rpeters.jellyfin.data.worker.DownloadActionReceiver
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.worker.OfflineDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jellyfin.sdk.model.api.BaseItemDto
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellyfinRepository,
    private val okHttpClient: OkHttpClient,
    private val encryptedPreferences: com.rpeters.jellyfin.data.security.EncryptedPreferences,
    private val dispatchers: com.rpeters.jellyfin.data.common.DispatcherProvider,
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    private val deviceCapabilities: com.rpeters.jellyfin.data.DeviceCapabilities,
) {

    private val _downloads = MutableStateFlow<List<OfflineDownload>>(emptyList())
    val downloads: StateFlow<List<OfflineDownload>> = _downloads.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val supervisorJob = SupervisorJob()
    private val downloadScope = CoroutineScope(dispatchers.io + supervisorJob)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val TAG = "OfflineDownloadManager"
        private const val DOWNLOADS_KEY = "offline_downloads"
        private const val JELLYFIN_OFFLINE_DIR = "JellyfinOffline"
        private const val CHUNK_SIZE = 8192
        private const val ENCRYPTED_URL_PREFIX = "encrypted_url_"
        private const val OFFLINE_DOWNLOAD_WORK_TAG = "offline_download"
        private const val TRANSCODING_POLL_INTERVAL_MS = 3000L
    }

    enum class DownloadExecutionResult {
        SUCCESS,
        RETRY,
        FAILURE,
        CANCELLED,
    }

    init {
        // Load existing downloads on initialization
        downloadScope.launch {
            loadDownloads()
        }
    }

    suspend fun startDownload(
        item: BaseItemDto,
        quality: VideoQuality? = null,
        downloadUrl: String? = null,
    ): String {
        return withContext(dispatchers.io) {
            val download = createDownload(item, quality, downloadUrl)
            addDownload(download)
            scheduleDownload(download.id, ExistingWorkPolicy.REPLACE)
            download.id
        }
    }

    fun pauseDownload(downloadId: String) {
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)
        cancelWork(downloadId)

        downloadScope.launch {
            updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
            showPausedNotification(downloadId)
        }
    }

    private fun showPausedNotification(downloadId: String) {
        val download = getDownloadById(downloadId) ?: return

        val resumeIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_RESUME
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val resumePendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.hashCode() + 3,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            downloadId.hashCode() + 4,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, "offline_downloads_progress")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Download Paused")
            .setContentText(download.itemName)
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher_monochrome, "Resume", resumePendingIntent)
            .addAction(R.drawable.ic_launcher_monochrome, "Cancel", cancelPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(downloadId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to show paused notification", e)
        }
    }

    fun resumeDownload(downloadId: String) {
        // Clear paused notification
        try {
            NotificationManagerCompat.from(context).cancel(downloadId.hashCode())
        } catch (e: Exception) {
            // Ignore
        }

        val download = _downloads.value.find { it.id == downloadId }
        if (download != null && (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.FAILED)) {
            downloadScope.launch {
                updateDownloadStatus(downloadId, DownloadStatus.PENDING)
                scheduleDownload(downloadId, ExistingWorkPolicy.REPLACE)
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
                cancelWork(downloadId)

                // Delete file
                deleteDownloadFile(it)

                // SECURITY: Delete encrypted URL from storage
                if (it.downloadUrl.startsWith(ENCRYPTED_URL_PREFIX)) {
                    encryptedPreferences.removeKey(it.downloadUrl)
                }

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

    suspend fun executeDownload(downloadId: String): DownloadExecutionResult {
        return executeDownload(downloadId, onProgress = null)
    }

    suspend fun executeDownload(
        downloadId: String,
        onProgress: (suspend (OfflineDownload, DownloadProgress) -> Unit)? = null,
    ): DownloadExecutionResult {
        val download = _downloads.value.find { it.id == downloadId }
            ?: return DownloadExecutionResult.FAILURE

        return try {
            updateDownloadStatus(download.id, DownloadStatus.DOWNLOADING)

            val actualUrl = getDecryptedUrl(download.downloadUrl)
            if (actualUrl == null) {
                Log.e(TAG, "Failed to decrypt download URL for ${download.id}")
                updateDownloadStatus(download.id, DownloadStatus.FAILED)
                return DownloadExecutionResult.FAILURE
            }

            val outputFile = File(download.localFilePath)
            outputFile.parentFile?.mkdirs()
            val existingSize = if (outputFile.exists()) outputFile.length() else 0L

            val requestBuilder = Request.Builder().url(actualUrl)
            if (existingSize > 0L) {
                requestBuilder.header("Range", "bytes=$existingSize-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()

            response.use {
                if (!it.isSuccessful) {
                    if (it.code in 500..599 || it.code == 429 || it.code == 408) {
                        updateDownloadStatus(download.id, DownloadStatus.PENDING)
                        return DownloadExecutionResult.RETRY
                    }
                    throw IOException("Download failed: ${it.code}")
                }

                val canAppend = existingSize > 0L && it.code == 206
                val startByte = if (canAppend) existingSize else 0L
                val totalBytes = downloadFile(it, download, startByte, canAppend, onProgress)

                if (currentCoroutineContext().isActive) {
                    updateDownloadBytes(download.id, totalBytes)
                    updateDownloadStatus(download.id, DownloadStatus.COMPLETED)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Download completed: ${download.itemName} ($totalBytes bytes)")
                    }
                    return DownloadExecutionResult.SUCCESS
                }
            }

            DownloadExecutionResult.CANCELLED
        } catch (e: CancellationException) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Download cancelled: ${download.id}")
            }
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Transient download error for ${download.id}: ${e.message}")
            updateDownloadStatus(download.id, DownloadStatus.PENDING)
            DownloadExecutionResult.RETRY
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${download.id}", e)
            updateDownloadStatus(download.id, DownloadStatus.FAILED)
            DownloadExecutionResult.FAILURE
        }
    }

    private fun executeDownloadInProcess(downloadId: String) {
        val download = _downloads.value.find { it.id == downloadId } ?: return
        val job = downloadScope.launch {
            when (executeDownload(downloadId)) {
                DownloadExecutionResult.RETRY -> updateDownloadStatus(downloadId, DownloadStatus.FAILED)
                DownloadExecutionResult.CANCELLED -> updateDownloadStatus(downloadId, DownloadStatus.CANCELLED)
                else -> Unit
            }
        }

        downloadJobs[downloadId] = job
    }

    /**
     * Decrypts the download URL from encrypted storage.
     * SECURITY: URLs contain authentication tokens and must be encrypted.
     */
    private suspend fun getDecryptedUrl(urlKeyOrUrl: String): String? {
        // Check if this is an encrypted key (new format)
        if (urlKeyOrUrl.startsWith(ENCRYPTED_URL_PREFIX)) {
            return withTimeoutOrNull(2000L) {
                encryptedPreferences.getEncryptedString(urlKeyOrUrl).first()
            }
        }

        // Legacy format: URL was stored directly (unencrypted)
        // For backward compatibility, return as-is but log warning
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Found unencrypted download URL - consider migrating to encrypted format")
        }
        return urlKeyOrUrl
    }

    private suspend fun downloadFile(
        response: Response,
        download: OfflineDownload,
        startByte: Long,
        append: Boolean,
        onProgress: (suspend (OfflineDownload, DownloadProgress) -> Unit)?,
    ): Long {
        val body = response.body

        val bodyLength = body.contentLength()
        val totalBytesExpected = if (bodyLength > 0L) startByte + bodyLength else bodyLength
        val outputFile = File(download.localFilePath)

        // Create parent directories if they don't exist
        outputFile.parentFile?.mkdirs()

        // Start transcoding progress poller for non-original quality downloads
        val transcodingRef = AtomicReference<com.rpeters.jellyfin.data.repository.TranscodingProgressInfo?>(null)
        val pollerJob = downloadScope.launchTranscodingPoller(download, transcodingRef)

        try {
            body.byteStream().use { inputStream ->
                FileOutputStream(outputFile, append).use { outputStream ->

                    val buffer = ByteArray(CHUNK_SIZE)
                    var totalBytesRead = startByte
                    var bytesRead: Int
                    val startTime = System.currentTimeMillis()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!currentCoroutineContext().isActive) break

                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        val currentTime = System.currentTimeMillis()
                        val elapsedTime = currentTime - startTime
                        val speed = if (elapsedTime > 0) ((totalBytesRead - startByte) * 1000L) / elapsedTime else 0L
                        val remainingBytes = totalBytesExpected - totalBytesRead
                        val remainingTime = if (speed > 0) (remainingBytes * 1000L) / speed else null

                        // Read latest transcoding progress
                        val transcodingInfo = transcodingRef.get()
                        val isNonOriginal = download.quality != null && download.quality.id != "original"
                        val transcodingActive = isNonOriginal &&
                            (transcodingInfo == null || transcodingInfo.completionPercentage < 100.0)
                        val isTranscoding = totalBytesExpected <= 0L && transcodingActive

                        val progress = DownloadProgress(
                            downloadId = download.id,
                            downloadedBytes = totalBytesRead,
                            totalBytes = totalBytesExpected,
                            progressPercent = if (totalBytesExpected > 0) {
                                (totalBytesRead.toFloat() / totalBytesExpected * 100f)
                            } else {
                                // Use transcoding progress as the displayed percentage when no content length
                                transcodingInfo?.completionPercentage?.toFloat() ?: 0f
                            },
                            downloadSpeedBps = speed,
                            remainingTimeMs = remainingTime,
                            isTranscoding = isTranscoding,
                            transcodingProgress = transcodingInfo?.completionPercentage?.toFloat(),
                        )

                        updateDownloadProgress(progress)
                        onProgress?.invoke(download, progress)
                        updateDownloadBytes(download.id, totalBytesRead)
                    }
                    return totalBytesRead
                }
            }
        } finally {
            pollerJob?.cancel()
        }
    }

    /**
     * Poll server for transcoding progress in a background coroutine.
     * Updates the provided AtomicReference with the latest progress.
     * Stops when transcoding completes or coroutine is cancelled.
     */
    private fun CoroutineScope.launchTranscodingPoller(
        download: OfflineDownload,
        progressRef: AtomicReference<com.rpeters.jellyfin.data.repository.TranscodingProgressInfo?>,
    ): Job? {
        // Only poll for non-original quality downloads
        val quality = download.quality ?: return null
        if (quality.id == "original") return null

        val deviceId = deviceCapabilities.getDeviceId()

        return launch {
            try {
                while (isActive) {
                    delay(TRANSCODING_POLL_INTERVAL_MS)
                    val progress = repository.getTranscodingProgress(
                        deviceId = deviceId,
                        jellyfinItemId = download.jellyfinItemId,
                    )
                    progressRef.set(progress)

                    // Stop polling once transcoding is complete
                    if (progress != null && progress.completionPercentage >= 100.0) {
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Transcoding poller stopped: ${e.message}")
                }
            }
        }
    }

    private suspend fun createDownload(
        item: BaseItemDto,
        quality: VideoQuality?,
        downloadUrl: String?,
    ): OfflineDownload {
        val url = downloadUrl ?: repository.getStreamUrl(item.id.toString()) ?: ""
        val fileName = "${item.name?.replace(Regex("[^a-zA-Z0-9.-]"), "_")}_${System.currentTimeMillis()}.mp4"
        val localPath = File(getOfflineDirectory(), fileName).absolutePath

        // SECURITY: Store download URL encrypted
        // Download URLs contain authentication tokens in query parameters
        val downloadId = java.util.UUID.randomUUID().toString()
        val encryptedUrlKey = "$ENCRYPTED_URL_PREFIX$downloadId"
        encryptedPreferences.putEncryptedString(encryptedUrlKey, url)

        return OfflineDownload(
            id = downloadId,
            jellyfinItemId = item.id.toString(),
            itemName = item.name ?: context.getString(R.string.unknown),
            itemType = item.type.toString(),
            downloadUrl = encryptedUrlKey, // Store the key, not the actual URL
            localFilePath = localPath,
            fileSize = 0L, // Will be updated during download
            quality = quality,
            playSessionId = extractPlaySessionId(url),
            downloadStartTime = System.currentTimeMillis(),
        )
    }

    /**
     * Extract PlaySessionId from a Jellyfin transcoded stream URL.
     * URLs contain PlaySessionId as a query parameter.
     */
    private fun extractPlaySessionId(url: String): String? {
        return try {
            android.net.Uri.parse(url).getQueryParameter("PlaySessionId")
        } catch (e: Exception) {
            null
        }
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
        } catch (e: IOException) {
            Log.e("OfflineDownloadManager", "Failed to delete file: ${download.localFilePath}", e)
        }
    }

    private suspend fun loadDownloads() {
        try {
            var initialized = false
            dataStore.data.collect { preferences ->
                val downloadsJson = preferences[androidx.datastore.preferences.core.stringPreferencesKey(DOWNLOADS_KEY)] ?: "[]"
                val downloads = json.decodeFromString<List<OfflineDownload>>(downloadsJson)
                _downloads.update { downloads }
                if (!initialized) {
                    initialized = true
                    requeueIncompleteDownloads(downloads)
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private suspend fun saveDownloads() {
        try {
            dataStore.edit { preferences ->
                val downloadsJson = json.encodeToString(_downloads.value)
                preferences[androidx.datastore.preferences.core.stringPreferencesKey(DOWNLOADS_KEY)] = downloadsJson
            }
        } catch (e: CancellationException) {
            throw e
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
                job.cancel(CancellationException("OfflineDownloadManager cleanup"))
            }
        }
        downloadJobs.clear()

        // Clear progress state
        _downloadProgress.update { emptyMap() }

        // Cancel the supervisor job and scope
        supervisorJob.cancel(CancellationException("OfflineDownloadManager cleanup"))
    }

    /**
     * Cancel a specific download and clean up its resources.
     */
    fun cancelDownload(downloadId: String) {
        // Clear notifications
        try {
            NotificationManagerCompat.from(context).cancel(downloadId.hashCode())
        } catch (e: Exception) {
            // Ignore
        }

        cancelWork(downloadId)
        downloadJobs[downloadId]?.let { job ->
            if (job.isActive) {
                job.cancel(CancellationException("Download cancelled by user"))
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

    fun getDownloadById(downloadId: String): OfflineDownload? {
        return _downloads.value.find { it.id == downloadId }
    }

    private fun scheduleDownload(downloadId: String, policy: ExistingWorkPolicy) {
        val enqueued = enqueueDownloadWork(downloadId, policy)
        if (!enqueued) {
            executeDownloadInProcess(downloadId)
        }
    }

    private fun enqueueDownloadWork(downloadId: String, policy: ExistingWorkPolicy): Boolean {
        val workManager = workManagerOrNull() ?: return false
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setInputData(workDataOf(OfflineDownloadWorker.KEY_DOWNLOAD_ID to downloadId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .addTag(OFFLINE_DOWNLOAD_WORK_TAG)
            .addTag(downloadTag(downloadId))
            .build()

        workManager.enqueueUniqueWork(downloadWorkName(downloadId), policy, request)
        return true
    }

    private fun cancelWork(downloadId: String) {
        workManagerOrNull()?.cancelUniqueWork(downloadWorkName(downloadId))
    }

    private fun requeueIncompleteDownloads(downloads: List<OfflineDownload>) {
        downloads
            .filter { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING }
            .forEach { scheduleDownload(it.id, ExistingWorkPolicy.KEEP) }
    }

    private fun workManagerOrNull(): WorkManager? {
        return try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "WorkManager unavailable; falling back to in-process downloads", e)
            }
            null
        }
    }

    private fun downloadWorkName(downloadId: String): String = "offline-download-$downloadId"

    private fun downloadTag(downloadId: String): String = "offline-download-tag-$downloadId"
}
