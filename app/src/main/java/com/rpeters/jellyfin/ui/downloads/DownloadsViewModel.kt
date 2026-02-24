package com.rpeters.jellyfin.ui.downloads

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.offline.DownloadProgress
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import com.rpeters.jellyfin.data.offline.OfflinePlaybackManager
import com.rpeters.jellyfin.data.offline.OfflineStorageInfo
import com.rpeters.jellyfin.data.offline.VideoQuality
import com.rpeters.jellyfin.data.preferences.DownloadPreferences
import com.rpeters.jellyfin.data.preferences.DownloadPreferencesRepository
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.OfflineProgressRepository
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.network.NetworkType
import com.rpeters.jellyfin.ui.player.VideoPlayerActivity
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: OfflineDownloadManager,
    private val playbackManager: OfflinePlaybackManager,
    private val repository: JellyfinRepository,
    private val downloadPreferencesRepository: DownloadPreferencesRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val offlineProgressRepository: OfflineProgressRepository,
    private val deviceCapabilities: DeviceCapabilities,
) : ViewModel() {

    val downloads: StateFlow<List<OfflineDownload>> = downloadManager.downloads
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = downloadManager.downloadProgress
    val downloadPreferences: StateFlow<DownloadPreferences> = downloadPreferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadPreferences.DEFAULT,
    )
    val pendingOfflineSyncCount: StateFlow<Int> = offlineProgressRepository.pendingCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0,
    )

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
        val QUALITY_PRESETS: List<VideoQuality> = listOf(
            VideoQuality(id = "original", label = "Original Quality", bitrate = 0, width = 0, height = 0),
            VideoQuality(id = "high", label = "High (1080p, 6 Mbps)", bitrate = 6_000_000, width = 1920, height = 1080, audioBitrate = 192_000, audioChannels = 2),
            VideoQuality(id = "medium", label = "Medium (720p, 3 Mbps)", bitrate = 3_000_000, width = 1280, height = 720, audioBitrate = 128_000, audioChannels = 2),
            VideoQuality(id = "low", label = "Low (480p, 1 Mbps)", bitrate = 1_000_000, width = 854, height = 480, audioBitrate = 96_000, audioChannels = 2),
        )
    }

    private data class OriginalCompatibilityDecision(
        val requiresCompatibilityTranscode: Boolean = false,
        val audioOnlyTranscode: Boolean = false,
        val reason: String = "",
        val sourceBitrate: Int? = null,
    )

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
            val reqId = UUID.randomUUID().toString().take(8)
            val prefs = downloadPreferencesRepository.preferences.first()
            val networkType = connectivityChecker.getNetworkType()
            val hasWifiLikeConnection = networkType == NetworkType.WIFI || networkType == NetworkType.ETHERNET
            if (prefs.wifiOnly && !hasWifiLikeConnection) {
                Toast.makeText(
                    context,
                    "Downloads are limited to Wi-Fi. Disable Wi-Fi only in Downloads settings to continue.",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            if (prefs.autoCleanEnabled) {
                runAutoCleanInternal(prefs)
            }

            val selectedQuality = quality ?: QUALITY_PRESETS.firstOrNull { it.id == prefs.defaultQualityId }
            val originalCompatibility = if (selectedQuality?.id == "original") {
                evaluateOriginalDownloadCompatibility(item)
            } else {
                OriginalCompatibilityDecision()
            }

            val effectiveQuality = when {
                selectedQuality?.id != "original" -> selectedQuality
                !originalCompatibility.requiresCompatibilityTranscode -> selectedQuality
                originalCompatibility.audioOnlyTranscode -> VideoQuality(
                    id = "compat_audio",
                    label = "Compatibility (Original video + AAC audio)",
                    bitrate = originalCompatibility.sourceBitrate ?: 12_000_000,
                    width = 0,
                    height = 0,
                    audioBitrate = 192_000,
                    audioChannels = 2,
                )
                else -> VideoQuality(
                    id = "compat_video",
                    label = "Compatibility (H.264/AAC 1080p)",
                    bitrate = 6_000_000,
                    width = 1920,
                    height = 1080,
                    audioBitrate = 192_000,
                    audioChannels = 2,
                )
            }

            if (originalCompatibility.requiresCompatibilityTranscode) {
                Toast.makeText(
                    context,
                    "Original may not direct play on this device. Downloading a compatible version.",
                    Toast.LENGTH_LONG,
                ).show()
                SecureLogger.i(
                    "DownloadsFlow",
                    "Compatibility fallback enabled for item=${item.name}: ${originalCompatibility.reason}",
                )
            }

            val itemId = item.id.toString()
            SecureLogger.i(
                "DownloadsFlow",
                "req=$reqId startDownload requested: itemId=$itemId, itemName=${item.name}, quality=${effectiveQuality?.id ?: "default"}, wifiOnly=${prefs.wifiOnly}, networkType=$networkType",
            )
            val url = withContext(Dispatchers.IO) {
                if (effectiveQuality != null && effectiveQuality.id != "original") {
                    // Use H.264 for offline transcoded downloads for maximum Jellyfin server/device compatibility.
                    // Force actual video transcoding (AllowVideoStreamCopy=false) so the server
                    // respects MaxWidth/MaxHeight/VideoBitrate instead of copying the original stream.
                    val compatibilityAudioOnly = originalCompatibility.requiresCompatibilityTranscode &&
                        originalCompatibility.audioOnlyTranscode &&
                        quality?.id != "original"
                    repository.getTranscodedStreamUrl(
                        itemId = itemId,
                        maxBitrate = if (compatibilityAudioOnly) {
                            originalCompatibility.sourceBitrate
                        } else {
                            effectiveQuality.bitrate
                        },
                        maxWidth = effectiveQuality.width.takeIf { it > 0 },
                        maxHeight = effectiveQuality.height.takeIf { it > 0 },
                        videoCodec = "h264",
                        audioCodec = "aac",
                        audioBitrate = effectiveQuality.audioBitrate,
                        audioChannels = effectiveQuality.audioChannels ?: 2,
                        container = "mp4",
                        allowVideoStreamCopy = compatibilityAudioOnly,
                    ) ?: repository.getDownloadUrl(itemId)
                } else {
                    downloadUrl ?: repository.getDownloadUrl(itemId)
                }
            }

            SecureLogger.i(
                "DownloadsFlow",
                "req=$reqId resolved source: itemId=$itemId, hasUrl=${!url.isNullOrBlank()}, quality=${effectiveQuality?.id ?: "null"}",
            )
            val downloadId = downloadManager.startDownload(item, effectiveQuality, url)
            val cid = downloadId.take(8)
            SecureLogger.i(
                "DownloadsFlow",
                "req=$reqId mapped to cid=$cid: itemId=$itemId, downloadId=$downloadId, quality=${effectiveQuality?.id ?: "null"}",
            )
        }
    }

    private suspend fun evaluateOriginalDownloadCompatibility(item: BaseItemDto): OriginalCompatibilityDecision {
        val itemMediaSource = item.mediaSources?.firstOrNull()
        val mediaSource = itemMediaSource ?: runCatching {
            repository.getPlaybackInfo(item.id.toString()).mediaSources.firstOrNull()
        }.getOrNull()

        mediaSource ?: return OriginalCompatibilityDecision()

        val videoStream = mediaSource.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
        val audioStream = mediaSource.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }

        val container = mediaSource.container
        val videoCodec = videoStream?.codec
        val audioCodec = audioStream?.codec
        val width = videoStream?.width ?: 0
        val height = videoStream?.height ?: 0
        val audioChannels = audioStream?.channels ?: 2

        val containerOk = deviceCapabilities.canPlayContainer(container)
        val videoOk = deviceCapabilities.canPlayVideoCodec(videoCodec, width, height)
        val audioOk = deviceCapabilities.canPlayAudioCodecStrict(audioCodec, audioChannels)

        if (containerOk && videoOk && audioOk) {
            return OriginalCompatibilityDecision()
        }

        val audioOnlyTranscode = containerOk && videoOk && !audioOk && !audioCodec.isNullOrBlank()
        val reason = buildString {
            if (!containerOk) append("container=$container unsupported; ")
            if (!videoOk) append("video=$videoCodec ${width}x$height unsupported; ")
            if (!audioOk) append("audio=$audioCodec ${audioChannels}ch unsupported")
        }.trim().trimEnd(';')

        return OriginalCompatibilityDecision(
            requiresCompatibilityTranscode = true,
            audioOnlyTranscode = audioOnlyTranscode,
            reason = reason,
            sourceBitrate = mediaSource.bitrate,
        )
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

    fun deleteAllDownloads() {
        viewModelScope.launch {
            downloads.value.forEach { deleteDownload(it.id) }
        }
    }

    fun clearWatchedDownloads() {
        viewModelScope.launch {
            downloads.value
                .filter { it.status == DownloadStatus.COMPLETED }
                .forEach { download ->
                    val runtimeMs = download.runtimeTicks?.div(10_000) ?: return@forEach
                    if (runtimeMs <= 0L) return@forEach

                    val positionMs = withContext(Dispatchers.IO) {
                        com.rpeters.jellyfin.data.PlaybackPositionStore.getPlaybackPosition(context, download.jellyfinItemId)
                    }
                    val watchedThresholdMs = (runtimeMs * 0.9f).toLong()
                    if (positionMs >= watchedThresholdMs) {
                        deleteDownload(download.id)
                    }
                }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            downloadPreferencesRepository.setWifiOnly(enabled)
        }
    }

    fun setDefaultQuality(qualityId: String) {
        viewModelScope.launch {
            downloadPreferencesRepository.setDefaultQualityId(qualityId)
        }
    }

    fun setAutoCleanEnabled(enabled: Boolean) {
        viewModelScope.launch {
            downloadPreferencesRepository.setAutoCleanEnabled(enabled)
        }
    }

    fun setAutoCleanWatchedRetentionDays(days: Int) {
        viewModelScope.launch {
            downloadPreferencesRepository.setAutoCleanWatchedRetentionDays(days)
        }
    }

    fun setAutoCleanMinFreeSpaceGb(gb: Int) {
        viewModelScope.launch {
            downloadPreferencesRepository.setAutoCleanMinFreeSpaceGb(gb)
        }
    }

    fun runAutoCleanNow() {
        viewModelScope.launch {
            val prefs = downloadPreferencesRepository.preferences.first()
            val deleted = runAutoCleanInternal(prefs, force = true)
            val message = if (deleted > 0) {
                "Auto-clean removed $deleted watched download(s)."
            } else {
                "Auto-clean found nothing to remove."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun playOfflineContent(itemId: String) {
        viewModelScope.launch {
            val download = playbackManager.getOfflineDownload(itemId)
            if (download != null) {
                val storedPositionMs = withContext(Dispatchers.IO) {
                    com.rpeters.jellyfin.data.PlaybackPositionStore.getPlaybackPosition(context, download.jellyfinItemId)
                }
                val fallbackPositionMs = download.lastPlaybackPositionMs ?: 0L
                val runtimeMs = (download.runtimeTicks ?: 0L) / 10_000L
                val resumePositionMs = max(storedPositionMs, fallbackPositionMs).coerceAtLeast(0L).let { position ->
                    if (runtimeMs > 5_000L) {
                        // Avoid seeking beyond the last few seconds of content.
                        position.coerceAtMost(runtimeMs - 2_000L)
                    } else {
                        position
                    }
                }
                val intent = VideoPlayerActivity.createIntent(
                    context = context,
                    itemId = download.jellyfinItemId,
                    itemName = download.itemName,
                    startPosition = resumePositionMs,
                    forceOffline = true,
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                // Keep offline records honest if the file was removed externally.
                downloadManager.deleteOfflineCopy(itemId)
            }
        }
    }

    fun validateOfflineFiles() {
        viewModelScope.launch {
            val invalidIds = playbackManager.validateOfflineFiles()
            invalidIds.forEach { deleteDownload(it) }
        }
    }

    fun redownloadByItem(item: BaseItemDto, quality: VideoQuality) {
        viewModelScope.launch {
            downloads.value
                .firstOrNull { it.jellyfinItemId == item.id.toString() }
                ?.let { existing -> deleteDownload(existing.id) }
            startDownload(item = item, quality = quality)
        }
    }

    fun redownloadDownload(downloadId: String, quality: VideoQuality) {
        viewModelScope.launch {
            val existing = downloads.value.firstOrNull { it.id == downloadId } ?: return@launch
            deleteDownload(existing.id)
            startDownload(item = existing.toBaseItem(), quality = quality)
        }
    }

    private fun OfflineDownload.toBaseItem(): BaseItemDto {
        val itemUuid = runCatching { UUID.fromString(jellyfinItemId) }
            .getOrElse { UUID.nameUUIDFromBytes(jellyfinItemId.toByteArray()) }
        val baseItemKind = runCatching { BaseItemKind.valueOf(itemType) }
            .getOrElse { BaseItemKind.VIDEO }

        return BaseItemDto(
            id = itemUuid,
            name = itemName,
            type = baseItemKind,
            runTimeTicks = runtimeTicks,
            seriesName = seriesName,
            parentIndexNumber = seasonNumber,
            indexNumber = episodeNumber,
            overview = overview,
            productionYear = productionYear,
        )
    }

    private suspend fun runAutoCleanInternal(
        prefs: DownloadPreferences,
        force: Boolean = false,
    ): Int {
        if (!prefs.autoCleanEnabled && !force) return 0

        val completed = downloads.value
            .filter { it.status == DownloadStatus.COMPLETED }
            .sortedBy { it.downloadCompleteTime ?: it.downloadStartTime ?: Long.MAX_VALUE }
        if (completed.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val retentionMs = prefs.autoCleanWatchedRetentionDays * 24L * 60L * 60L * 1000L
        val targetFreeBytes = prefs.autoCleanMinFreeSpaceGb.toLong() * 1024L * 1024L * 1024L
        var expectedFreeBytes = storageInfo.value?.availableSpaceBytes ?: playbackManager.getOfflineStorageInfo().availableSpaceBytes
        var deletedCount = 0
        val deletedIds = mutableSetOf<String>()

        fun approxSize(download: OfflineDownload): Long {
            return max(
                download.fileSize,
                download.downloadedBytes,
            )
        }

        suspend fun isWatched(download: OfflineDownload): Boolean {
            val runtimeMs = download.runtimeTicks?.div(10_000) ?: return false
            if (runtimeMs <= 0L) return false
            val positionMs = withContext(Dispatchers.IO) {
                com.rpeters.jellyfin.data.PlaybackPositionStore.getPlaybackPosition(context, download.jellyfinItemId)
            }
            return positionMs >= (runtimeMs * 0.9f).toLong()
        }

        for (download in completed) {
            if (!isWatched(download)) continue
            val timestamp = download.downloadCompleteTime ?: download.downloadStartTime ?: continue
            if (now - timestamp >= retentionMs) {
                deleteDownload(download.id)
                expectedFreeBytes += approxSize(download)
                deletedCount++
                deletedIds.add(download.id)
            }
        }

        if (expectedFreeBytes >= targetFreeBytes) return deletedCount

        for (download in completed) {
            if (expectedFreeBytes >= targetFreeBytes) break
            if (!isWatched(download)) continue

            if (download.id in deletedIds) continue

            deleteDownload(download.id)
            expectedFreeBytes += approxSize(download)
            deletedCount++
            deletedIds.add(download.id)
        }

        return deletedCount
    }
}
