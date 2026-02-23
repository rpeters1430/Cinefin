package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.offline.DownloadStatus
import com.rpeters.jellyfin.data.offline.OfflineDownload
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages offline capabilities for the Jellyfin Android app.
 *
 * Provides connectivity monitoring, offline library browsing data,
 * and fallback behavior when the app is used without internet.
 */
@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineDownloadManager: OfflineDownloadManager,
) {

    companion object {
        private const val TAG = "OfflineManager"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Network state tracking
    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkType = MutableStateFlow(getCurrentNetworkType())
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    // Offline library tracking
    private val _offlineContent = MutableStateFlow<List<OfflineLibraryItem>>(emptyList())
    val offlineContent: StateFlow<List<OfflineLibraryItem>> = _offlineContent.asStateFlow()

    // Network callback for monitoring connectivity changes
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Network available: $network")
            }
            _isOnline.value = true
            _networkType.value = getCurrentNetworkType()
        }

        override fun onLost(network: Network) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Network lost: $network")
            }
            val stillOnline = isCurrentlyOnline()
            _isOnline.value = stillOnline
            if (!stillOnline) {
                _networkType.value = NetworkType.NONE
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Device is now offline")
                }
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _networkType.value = getCurrentNetworkType()
        }
    }

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        scope.launch {
            offlineDownloadManager.downloads.collect { downloads ->
                _offlineContent.value = downloads
                    .filter { it.status == DownloadStatus.COMPLETED && hasReadableFile(it.localFilePath) }
                    .map { download ->
                        OfflineLibraryItem(
                            item = toBaseItem(download),
                            localFilePath = download.localFilePath,
                            posterLocalPath = download.thumbnailLocalPath,
                            qualityLabel = download.quality?.label,
                            fileSizeBytes = download.fileSize.takeIf { it > 0L } ?: download.downloadedBytes,
                            downloadDateMs = download.downloadCompleteTime ?: download.downloadStartTime,
                        )
                    }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Offline library refreshed: ${_offlineContent.value.size} items")
                }
            }
        }
    }

    /**
     * Checks if the device is currently online.
     */
    fun isCurrentlyOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Gets the current network type.
     */
    private fun getCurrentNetworkType(): NetworkType {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }

    /**
     * Checks if an item is available offline.
     */
    fun isAvailableOffline(item: BaseItemDto): Boolean {
        return offlineDownloadManager.isItemDownloaded(item.id.toString())
    }

    /**
     * Gets the local file path for an offline item.
     */
    fun getOfflineFilePath(item: BaseItemDto): String? {
        return _offlineContent.value.firstOrNull { it.item.id == item.id }?.localFilePath
    }

    /**
     * Gets the cached poster path for an offline item.
     */
    fun getOfflinePosterPath(item: BaseItemDto): String? {
        return _offlineContent.value.firstOrNull { it.item.id == item.id }?.posterLocalPath
    }

    fun deleteOfflineCopy(itemId: String) {
        offlineDownloadManager.deleteOfflineCopy(itemId)
    }

    /**
     * Refreshes the list of offline content.
     */
    fun refreshOfflineContent() {
        _offlineContent.value = offlineDownloadManager.getCompletedDownloads()
            .map { download ->
                OfflineLibraryItem(
                    item = toBaseItem(download),
                    localFilePath = download.localFilePath,
                    posterLocalPath = download.thumbnailLocalPath,
                    qualityLabel = download.quality?.label,
                    fileSizeBytes = download.fileSize.takeIf { it > 0L } ?: download.downloadedBytes,
                    downloadDateMs = download.downloadCompleteTime ?: download.downloadStartTime,
                )
            }
    }

    /**
     * Gets offline content filtered by type.
     */
    fun getOfflineContentByType(itemType: BaseItemKind): List<OfflineLibraryItem> {
        return _offlineContent.value.filter { it.item.type == itemType }
    }

    /**
     * Calculates storage usage for offline content.
     */
    fun getOfflineStorageUsage(): OfflineStorageInfo {
        val completedDownloads = offlineDownloadManager.getCompletedDownloads()
        val totalSize = completedDownloads.sumOf { download ->
            download.fileSize
                .takeIf { it > 0L }
                ?: download.downloadedBytes.takeIf { it > 0L }
                ?: File(download.localFilePath).takeIf { it.exists() }?.length()
                ?: 0L
        }

        return OfflineStorageInfo(
            totalSizeBytes = totalSize,
            itemCount = _offlineContent.value.size,
            formattedSize = formatBytes(totalSize),
        )
    }

    /**
     * Clears all offline content to free up storage.
     */
    fun clearOfflineContent(): Boolean {
        return try {
            val downloads = offlineDownloadManager.getCompletedDownloads()
            downloads.forEach { offlineDownloadManager.deleteDownload(it.id) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Determines if the current network is suitable for streaming.
     */
    fun isNetworkSuitableForStreaming(): Boolean {
        if (!isCurrentlyOnline()) return false

        return when (networkType.value) {
            NetworkType.WIFI, NetworkType.ETHERNET -> true
            NetworkType.CELLULAR -> true
            NetworkType.OTHER -> true
            NetworkType.NONE -> false
        }
    }

    /**
     * Suggests the best playback source based on connectivity.
     */
    fun suggestPlaybackSource(item: BaseItemDto): PlaybackSource {
        val isOfflineAvailable = isAvailableOffline(item)
        val isOnlineAvailable = isNetworkSuitableForStreaming()

        return when {
            isOfflineAvailable -> PlaybackSource.LOCAL
            isOnlineAvailable -> PlaybackSource.STREAM
            else -> PlaybackSource.UNAVAILABLE
        }
    }

    /**
     * Gets offline-specific error messages and suggestions.
     */
    fun getOfflineErrorMessage(requestedAction: String): String {
        return when {
            !isCurrentlyOnline() -> {
                "You're offline. Only downloaded content is available. Consider downloading more content when online."
            }
            networkType.value == NetworkType.CELLULAR -> {
                "You're on cellular data. For best experience, connect to Wi-Fi or use downloaded content."
            }
            else -> {
                "Network connection is limited. $requestedAction may not work properly."
            }
        }
    }

    /**
     * Cleanup resources when no longer needed.
     */
    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            scope.cancel()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cleanup completed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun toBaseItem(download: OfflineDownload): BaseItemDto {
        val itemUuid = runCatching { UUID.fromString(download.jellyfinItemId) }
            .getOrElse { UUID.nameUUIDFromBytes(download.jellyfinItemId.toByteArray()) }
        val itemType = runCatching { BaseItemKind.valueOf(download.itemType) }
            .getOrElse { BaseItemKind.VIDEO }

        return BaseItemDto(
            id = itemUuid,
            name = download.itemName,
            type = itemType,
            seriesName = download.seriesName,
            parentIndexNumber = download.seasonNumber,
            indexNumber = download.episodeNumber,
            overview = download.overview,
            productionYear = download.productionYear,
            runTimeTicks = download.runtimeTicks,
        )
    }

    private fun hasReadableFile(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.isFile && file.canRead()
        } catch (_: Exception) {
            false
        }
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return "%.1f %s".format(size, units[unitIndex])
    }
}

/**
 * Enum representing different network types.
 */
enum class NetworkType {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
}

/**
 * Enum representing playback source options.
 */
enum class PlaybackSource {
    LOCAL,
    STREAM,
    UNAVAILABLE,
}

/**
 * Offline browse model for library-style listing while disconnected.
 */
data class OfflineLibraryItem(
    val item: BaseItemDto,
    val localFilePath: String,
    val posterLocalPath: String?,
    val qualityLabel: String?,
    val fileSizeBytes: Long,
    val downloadDateMs: Long?,
)

/**
 * Data class for offline storage information.
 */
data class OfflineStorageInfo(
    val totalSizeBytes: Long,
    val itemCount: Int,
    val formattedSize: String,
)

fun BaseItemDto.shouldUseOfflineMode(offlineManager: OfflineManager): Boolean {
    return !offlineManager.isCurrentlyOnline() && offlineManager.isAvailableOffline(this)
}

fun BaseItemDto.getBestPlaybackUrl(
    offlineManager: OfflineManager,
    onlineStreamUrl: String?,
): String? {
    return when (offlineManager.suggestPlaybackSource(this)) {
        PlaybackSource.LOCAL -> offlineManager.getOfflineFilePath(this)
        PlaybackSource.STREAM -> onlineStreamUrl
        PlaybackSource.UNAVAILABLE -> null
    }
}
