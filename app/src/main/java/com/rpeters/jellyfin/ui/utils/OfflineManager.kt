package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Manages offline capabilities for the Jellyfin Android app.
 *
 * Provides connectivity monitoring, offline content management,
 * and intelligent fallback strategies for when the app is used without internet.
 */
class OfflineManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineManager"
        private const val OFFLINE_CACHE_DIR = "offline_cache"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Network state tracking
    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkType = MutableStateFlow(getCurrentNetworkType())
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    // Offline content tracking
    private val _offlineContent = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val offlineContent: StateFlow<List<BaseItemDto>> = _offlineContent.asStateFlow()

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
        // Register for network connectivity changes
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Load offline content on initialization
        refreshOfflineContent()
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
        return MediaDownloadManager.isDownloaded(context, item)
    }

    /**
     * Gets the local file path for an offline item.
     */
    fun getOfflineFilePath(item: BaseItemDto): String? {
        return MediaDownloadManager.getLocalFilePath(context, item)
    }

    /**
     * Refreshes the list of offline content.
     */
    fun refreshOfflineContent() {
        try {
            val offlineItems = mutableListOf<BaseItemDto>()

            // This is a simplified implementation - in a real app, you would:
            // 1. Scan the download directory for media files
            // 2. Parse metadata to reconstruct BaseItemDto objects
            // 3. Validate that files are still accessible

            // For now, return empty list as we would need to store metadata
            // alongside downloaded files to properly reconstruct BaseItemDto objects
            _offlineContent.value = offlineItems

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Refreshed offline content: ${offlineItems.size} items available")
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Gets offline content filtered by type.
     */
    fun getOfflineContentByType(itemType: BaseItemKind): List<BaseItemDto> {
        return _offlineContent.value.filter { it.type == itemType }
    }

    /**
     * Calculates storage usage for offline content.
     */
    fun getOfflineStorageUsage(): OfflineStorageInfo {
        val totalSize = MediaDownloadManager.getTotalDownloadSize(context)
        val itemCount = _offlineContent.value.size

        return OfflineStorageInfo(
            totalSizeBytes = totalSize,
            itemCount = itemCount,
            formattedSize = formatBytes(totalSize),
        )
    }

    /**
     * Clears all offline content to free up storage.
     */
    fun clearOfflineContent(): Boolean {
        return try {
            val cleared = MediaDownloadManager.clearAllDownloads(context)
            if (cleared) {
                _offlineContent.value = emptyList()
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Cleared all offline content")
                }
            }
            cleared
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Determines if the current network is suitable for streaming.
     */
    fun isNetworkSuitableForStreaming(): Boolean {
        if (!isCurrentlyOnline()) return false

        return when (networkType.value) {
            NetworkType.WIFI, NetworkType.ETHERNET -> true
            NetworkType.CELLULAR -> {
                // In a real app, you might check user preferences for cellular streaming
                // For now, allow cellular streaming but could be made configurable
                true
            }
            NetworkType.OTHER -> true // Assume other connection types are suitable
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
            // Always prefer local files when available and reliable
            isOfflineAvailable -> PlaybackSource.LOCAL

            // Fall back to streaming if network is good
            isOnlineAvailable -> PlaybackSource.STREAM

            // No good options available
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
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Cleanup completed")
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    // Helper functions

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
    LOCAL, // Play from downloaded file
    STREAM, // Stream from server
    UNAVAILABLE, // No playback option available
}

/**
 * Data class for offline storage information.
 */
data class OfflineStorageInfo(
    val totalSizeBytes: Long,
    val itemCount: Int,
    val formattedSize: String,
)

/**
 * Extension functions for offline capabilities.
 */

/**
 * Extension to easily check if content should use offline mode.
 */
fun BaseItemDto.shouldUseOfflineMode(offlineManager: OfflineManager): Boolean {
    return !offlineManager.isCurrentlyOnline() && offlineManager.isAvailableOffline(this)
}

/**
 * Extension to get the best playback URL based on offline capabilities.
 */
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
