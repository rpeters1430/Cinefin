package com.rpeters.jellyfin.data.offline

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
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
import com.rpeters.jellyfin.data.worker.DownloadActionReceiver
import com.rpeters.jellyfin.data.worker.OfflineDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
        private const val JELLYFIN_OFFLINE_THUMBNAIL_DIR = "JellyfinOfflineThumbs"
        private const val CHUNK_SIZE = 8192
        private const val ENCRYPTED_URL_PREFIX = "encrypted_url_"
        private const val OFFLINE_DOWNLOAD_WORK_TAG = "offline_download"
        private const val TRANSCODING_POLL_INTERVAL_MS = 3000L

        private val KNOWN_VIDEO_CONTAINERS = setOf(
            "mkv", "mp4", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts", "mpg", "mpeg",
        )
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
                deleteThumbnailFile(it)

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
        return download?.takeIf { hasValidLocalFile(it) }?.let { File(it.localFilePath) }
    }

    fun isItemDownloaded(itemId: String): Boolean {
        return _downloads.value.any {
            it.jellyfinItemId == itemId &&
                it.status == DownloadStatus.COMPLETED &&
                hasValidLocalFile(it)
        }
    }

    fun getCompletedDownloads(): List<OfflineDownload> {
        return _downloads.value.filter {
            it.status == DownloadStatus.COMPLETED && hasValidLocalFile(it)
        }
    }

    fun observeIsDownloaded(itemId: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return observeDownloadInfo(itemId).map { it != null }
            .distinctUntilChanged()
    }

    fun observeDownloadInfo(itemId: String): kotlinx.coroutines.flow.Flow<OfflineDownload?> {
        return downloads
            .map { items ->
                items.firstOrNull { download ->
                    download.jellyfinItemId == itemId &&
                        download.status == DownloadStatus.COMPLETED &&
                        hasValidLocalFile(download)
                }
            }
            .distinctUntilChanged()
    }

    fun deleteOfflineCopy(itemId: String) {
        val downloadId = _downloads.value.firstOrNull {
            it.jellyfinItemId == itemId && it.status == DownloadStatus.COMPLETED
        }?.id ?: return
        deleteDownload(downloadId)
    }

    fun getAvailableStorage(): Long {
        val offlineDir = getOfflineDirectory()
        return offlineDir.freeSpace
    }

    fun getTotalStorage(): Long {
        val offlineDir = getOfflineDirectory()
        return offlineDir.totalSpace
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

            // Transcoded streams (any quality other than original) are served with
            // Accept-Ranges: none — the server starts a fresh transcode on every request
            // and cannot seek into a live stream. Sending a Range header would be silently
            // ignored, so we proactively delete any stale partial file and reset the
            // stored byte count so the UI doesn't show stale progress.
            val isTranscoded = download.quality != null && download.quality.id != "original"
            if (isTranscoded && outputFile.exists()) {
                outputFile.delete()
                updateDownloadBytes(download.id, 0L)
            }

            val existingSize = if (!isTranscoded && outputFile.exists()) outputFile.length() else 0L

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
        val isEstimatedSize = bodyLength <= 0L
        // If server provides Content-Length, use it. Otherwise estimate from bitrate × duration.
        val totalBytesExpected = when {
            bodyLength > 0L -> startByte + bodyLength
            else -> estimateTotalBytes(download)
        }
        val outputFile = File(download.localFilePath)

        // Create parent directories if they don't exist
        outputFile.parentFile?.mkdirs()

        val transcodingRef = AtomicReference<com.rpeters.jellyfin.data.repository.TranscodingProgressInfo?>(null)
        val pollerJob = downloadScope.launchTranscodingPoller(download, transcodingRef)

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Download starting: contentLength=$bodyLength, " +
                    "estimatedTotal=$totalBytesExpected, quality=${download.quality?.label}",
            )
        }

        try {
            body.byteStream().use { inputStream ->
                FileOutputStream(outputFile, append).use { outputStream ->

                    val buffer = ByteArray(CHUNK_SIZE)
                    var totalBytesRead = startByte
                    var bytesRead: Int
                    val startTime = System.currentTimeMillis()
                    var lastSavedBytes = startByte
                    val SAVE_INTERVAL_BYTES = 1_048_576L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!currentCoroutineContext().isActive) break

                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        val currentTime = System.currentTimeMillis()
                        val elapsedTime = currentTime - startTime
                        val speed = if (elapsedTime > 0) ((totalBytesRead - startByte) * 1000L) / elapsedTime else 0L
                        val remainingBytes = if (totalBytesExpected > 0L) totalBytesExpected - totalBytesRead else 0L
                        val remainingTime = if (speed > 0 && totalBytesExpected > 0L) {
                            (remainingBytes * 1000L) / speed
                        } else {
                            null
                        }

                        val rawPercent = if (totalBytesExpected > 0L) {
                            totalBytesRead.toFloat() / totalBytesExpected * 100f
                        } else {
                            0f
                        }
                        // Cap estimated progress at 99% since the estimate may be inaccurate
                        val progressPercent = if (isEstimatedSize) rawPercent.coerceAtMost(99f) else rawPercent

                        val progress = DownloadProgress(
                            downloadId = download.id,
                            downloadedBytes = totalBytesRead,
                            totalBytes = totalBytesExpected,
                            progressPercent = progressPercent,
                            downloadSpeedBps = speed,
                            remainingTimeMs = remainingTime,
                            isTranscoding = false,
                            transcodingProgress = null,
                            transcodingEtaMs = null,
                        )

                        updateDownloadProgress(progress)
                        onProgress?.invoke(download, progress)
                        // Only persist to DataStore every 1 MB to avoid hammering storage
                        if (totalBytesRead - lastSavedBytes >= SAVE_INTERVAL_BYTES) {
                            updateDownloadBytes(download.id, totalBytesRead)
                            lastSavedBytes = totalBytesRead
                        }
                    }
                    return totalBytesRead
                }
            }
        } finally {
            pollerJob?.cancel()
        }
    }

    /**
     * Poll server for transcoding progress before media bytes start flowing.
     * This allows notifications/UI to show "transcoding" state and ETA.
     */
    private fun CoroutineScope.launchTranscodingPoller(
        download: OfflineDownload,
        progressRef: AtomicReference<com.rpeters.jellyfin.data.repository.TranscodingProgressInfo?>,
    ): Job? {
        val quality = download.quality ?: return null
        if (quality.id == "original") return null

        val deviceId = deviceCapabilities.getDeviceId()

        return launch {
            var previousPercent: Double? = null
            var previousAtMs: Long? = null

            try {
                while (isActive) {
                    val progress = repository.getTranscodingProgress(
                        deviceId = deviceId,
                        jellyfinItemId = download.jellyfinItemId,
                    )
                    progressRef.set(progress)

                    val percent = progress?.completionPercentage?.coerceIn(0.0, 100.0)
                    val now = System.currentTimeMillis()
                    val etaMs = if (percent != null && previousPercent != null && previousAtMs != null) {
                        val prevPercent = previousPercent
                        val prevAtMs = previousAtMs
                        val deltaPercent = percent - prevPercent
                        val deltaMs = now - prevAtMs
                        if (deltaPercent > 0.0 && deltaMs > 0L && percent < 100.0) {
                            val percentPerMs = deltaPercent / deltaMs.toDouble()
                            (((100.0 - percent) / percentPerMs).toLong()).coerceAtLeast(0L)
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    if (percent != null) {
                        val progressUpdate = DownloadProgress(
                            downloadId = download.id,
                            downloadedBytes = 0L,
                            totalBytes = -1L,
                            progressPercent = percent.toFloat(),
                            downloadSpeedBps = 0L,
                            remainingTimeMs = null,
                            isTranscoding = true,
                            transcodingProgress = percent.toFloat(),
                            transcodingEtaMs = etaMs,
                        )
                        updateDownloadProgress(progressUpdate)
                    }

                    previousPercent = percent
                    previousAtMs = now

                    if (percent != null && percent >= 100.0) {
                        break
                    }

                    delay(TRANSCODING_POLL_INTERVAL_MS)
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

    /**
     * Estimate total download bytes from bitrate and runtime when Content-Length is unavailable.
     * This happens for transcoded streams where the server sends chunked encoding.
     * The estimate uses: totalBytes = (videoBitrate + audioBitrate) × durationSeconds / 8
     * with a 5% overhead factor for container metadata.
     */
    private fun estimateTotalBytes(download: OfflineDownload): Long {
        val quality = download.quality ?: return -1L
        val runtimeTicks = download.runtimeTicks ?: return -1L
        if (quality.id == "original") return -1L

        val durationSeconds = runtimeTicks / 10_000_000.0
        val videoBitrate = quality.bitrate.toLong()
        val audioBitrate = (quality.audioBitrate ?: 128_000).toLong()
        val totalBitrate = videoBitrate + audioBitrate

        // Convert bits to bytes, add 5% for container overhead
        val estimatedBytes = (totalBitrate * durationSeconds / 8.0 * 1.05).toLong()

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Estimated download size: ${estimatedBytes / (1024 * 1024)}MB " +
                    "(${durationSeconds.toLong()}s @ ${totalBitrate / 1000}kbps)",
            )
        }

        return if (estimatedBytes > 0L) estimatedBytes else -1L
    }

    private suspend fun createDownload(
        item: BaseItemDto,
        quality: VideoQuality?,
        downloadUrl: String?,
    ): OfflineDownload {
        val itemId = item.id.toString()
        val url = downloadUrl
            ?: repository.getDownloadUrl(itemId)
            ?: repository.getStreamUrl(itemId)
            ?: ""
        val fileExtension = if (quality == null || quality.id == "original") {
            // Derive extension from the item's original container so files are correctly labelled
            // (e.g. "mkv", "avi") when the user downloads at Original quality.
            val container = item.mediaSources?.firstOrNull()?.container
                ?.lowercase()
                ?.trimStart('.')
            if (container != null && container in KNOWN_VIDEO_CONTAINERS) ".$container" else ".mp4"
        } else {
            ".mp4" // Transcoded downloads are always MP4
        }
        val fileName = "${item.name?.replace(Regex("[^a-zA-Z0-9.-]"), "_")}_${System.currentTimeMillis()}$fileExtension"
        val localPath = File(getOfflineDirectory(), fileName).absolutePath

        // SECURITY: Store download URL encrypted
        // Download URLs contain authentication tokens in query parameters
        val downloadId = java.util.UUID.randomUUID().toString()
        val encryptedUrlKey = "$ENCRYPTED_URL_PREFIX$downloadId"
        encryptedPreferences.putEncryptedString(encryptedUrlKey, url)
        val thumbnailUrl = repository.getSeriesImageUrl(item) ?: repository.getImageUrl(itemId)
        val thumbnailLocalPath = cacheThumbnailLocally(itemId, thumbnailUrl)

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
            runtimeTicks = item.runTimeTicks,
            seriesName = item.seriesName,
            seasonNumber = item.parentIndexNumber,
            episodeNumber = item.indexNumber,
            overview = item.overview,
            productionYear = item.productionYear,
            thumbnailUrl = thumbnailUrl,
            thumbnailLocalPath = thumbnailLocalPath,
            downloadStartTime = System.currentTimeMillis(),
        )
    }

    private suspend fun cacheThumbnailLocally(itemId: String, imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) return null
        return withContext(dispatchers.io) {
            try {
                val thumbnailDir = File(context.filesDir, JELLYFIN_OFFLINE_THUMBNAIL_DIR).apply { mkdirs() }
                val destination = File(thumbnailDir, "$itemId.jpg")
                val request = Request.Builder()
                    .url(imageUrl)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext null
                    }
                    response.body.byteStream().use { input ->
                        FileOutputStream(destination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                destination.absolutePath
            } catch (_: Exception) {
                null
            }
        }
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
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir // fallback to internal storage if external unavailable
        val offlineDir = File(baseDir, JELLYFIN_OFFLINE_DIR)
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

    private fun deleteThumbnailFile(download: OfflineDownload) {
        val thumbnailPath = download.thumbnailLocalPath ?: return
        try {
            val file = File(thumbnailPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun loadDownloads() {
        try {
            var initialized = false
            dataStore.data.collect { preferences ->
                val downloadsJson = preferences[androidx.datastore.preferences.core.stringPreferencesKey(DOWNLOADS_KEY)] ?: "[]"
                val downloads = try {
                    json.decodeFromString<List<OfflineDownload>>(downloadsJson)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to deserialize downloads — resetting to empty list", e)
                    emptyList()
                }
                _downloads.update { current ->
                    val currentMap = current.associateBy { it.id }
                    downloads.map { persisted ->
                        val inMemory = currentMap[persisted.id]
                        // Guard against stale intermediate DataStore saves (e.g. DOWNLOADING or
                        // PENDING with byte-count updates) overwriting a stable status that was
                        // already applied in memory. This prevents a race where a prior DataStore
                        // emission arrives at the collector after the in-memory state has already
                        // advanced to a terminal/stable status. The subsequent DataStore emission
                        // from saveDownloads() for the stable status will resync shortly.
                        val persistedIsTransitional = persisted.status == DownloadStatus.DOWNLOADING ||
                            persisted.status == DownloadStatus.PENDING
                        val inMemoryIsStable = inMemory != null &&
                            inMemory.status != DownloadStatus.DOWNLOADING &&
                            inMemory.status != DownloadStatus.PENDING
                        if (inMemoryIsStable && persistedIsTransitional) {
                            inMemory!!
                        } else {
                            persisted
                        }
                    }
                }
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

    private fun hasValidLocalFile(download: OfflineDownload): Boolean {
        val file = try {
            File(download.localFilePath).canonicalFile
        } catch (_: IOException) {
            return false
        }

        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return false
        }

        return isInAppSpecificStorage(file)
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
}
