package com.rpeters.jellyfin.data.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced playback manager that intelligently determines the best playback method
 * based on device capabilities, network conditions, and media characteristics.
 */
@Singleton
class EnhancedPlaybackManager @Inject constructor(
    private val context: Context,
    private val repository: JellyfinRepository,
    private val streamRepository: JellyfinStreamRepository,
    private val deviceCapabilities: DeviceCapabilities,
) {

    companion object {
        private const val TAG = "EnhancedPlaybackManager"

        // Network quality thresholds (in Mbps)
        private const val HIGH_QUALITY_THRESHOLD = 25
        private const val MEDIUM_QUALITY_THRESHOLD = 10
        private const val LOW_QUALITY_THRESHOLD = 3

        // Direct play bitrate thresholds
        private const val DIRECT_PLAY_MAX_BITRATE = 100_000_000 // 100 Mbps
        private const val WIFI_DIRECT_PLAY_THRESHOLD = 50_000_000 // 50 Mbps
        private const val CELLULAR_DIRECT_PLAY_THRESHOLD = 15_000_000 // 15 Mbps
    }

    /**
     * Get optimal playback URL with intelligent Direct Play detection
     */
    suspend fun getOptimalPlaybackUrl(item: BaseItemDto): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                val itemId = item.id?.toString()
                    ?: return@withContext PlaybackResult.Error("Item ID is null")

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Getting optimal playback URL for: ${item.name} (${item.type})")
                }

                // Get detailed playback info from server
                val playbackInfo = getPlaybackInfo(itemId)
                if (playbackInfo == null) {
                    return@withContext PlaybackResult.Error("Failed to get playback info")
                }

                // Analyze media sources for Direct Play capability
                val directPlayResult = analyzeDirectPlayCapability(item, playbackInfo)
                if (directPlayResult != null) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Direct Play available: ${directPlayResult.url}")
                    }
                    return@withContext directPlayResult
                }

                // Fallback to optimized transcoding
                val transcodingResult = getOptimalTranscodingUrl(item, playbackInfo)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Using transcoding: ${transcodingResult.url}")
                }

                return@withContext transcodingResult
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get optimal playback URL", e)
                PlaybackResult.Error("Failed to get playback URL: ${e.message}")
            }
        }
    }

    /**
     * Analyze if Direct Play is possible based on device capabilities and network conditions
     */
    private suspend fun analyzeDirectPlayCapability(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
    ): PlaybackResult.DirectPlay? {
        val mediaSources = playbackInfo.mediaSources
        if (mediaSources.isNullOrEmpty()) {
            Log.d(TAG, "No media sources available for Direct Play analysis")
            return null
        }

        // Check each media source for Direct Play compatibility
        for (mediaSource in mediaSources) {
            val canDirectPlay = canDirectPlayMediaSource(mediaSource, item)
            if (canDirectPlay) {
                val directPlayUrl = mediaSource.path
                    ?: streamRepository.getDirectStreamUrl(item.id.toString(), mediaSource.container)

                if (directPlayUrl != null) {
                    return PlaybackResult.DirectPlay(
                        url = directPlayUrl,
                        container = mediaSource.container ?: "unknown",
                        videoCodec = getVideoCodec(mediaSource),
                        audioCodec = getAudioCodec(mediaSource),
                        bitrate = mediaSource.bitrate ?: 0,
                        reason = "Device supports all codecs and container format",
                    )
                }
            }
        }

        return null
    }

    /**
     * Check if a media source can be directly played
     */
    private fun canDirectPlayMediaSource(
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        item: BaseItemDto,
    ): Boolean {
        // Check container support
        val container = mediaSource.container
        if (!deviceCapabilities.canPlayContainer(container)) {
            Log.d(TAG, "Container '$container' not supported for Direct Play")
            return false
        }

        // Check video codec support
        val videoStream = mediaSource.mediaStreams?.find { it.type == MediaStreamType.VIDEO }
        if (videoStream != null) {
            val videoCodec = videoStream.codec
            val width = videoStream.width ?: 0
            val height = videoStream.height ?: 0

            if (!deviceCapabilities.canPlayVideoCodec(videoCodec, width, height)) {
                Log.d(TAG, "Video codec '$videoCodec' at ${width}x$height not supported for Direct Play")
                return false
            }
        }

        // Check audio codec support
        val audioStream = mediaSource.mediaStreams?.find { it.type == MediaStreamType.AUDIO }
        if (audioStream != null) {
            val audioCodec = audioStream.codec
            if (!deviceCapabilities.canPlayAudioCodec(audioCodec)) {
                Log.d(TAG, "Audio codec '$audioCodec' not supported for Direct Play")
                return false
            }
        }

        // Check network conditions for high-bitrate content
        val bitrate = mediaSource.bitrate ?: 0
        if (!isNetworkSuitableForDirectPlay(bitrate)) {
            Log.d(TAG, "Network conditions not suitable for Direct Play (bitrate: ${bitrate / 1_000_000} Mbps)")
            return false
        }

        return true
    }

    /**
     * Check if network conditions are suitable for Direct Play
     */
    private fun isNetworkSuitableForDirectPlay(bitrate: Int): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                bitrate <= WIFI_DIRECT_PLAY_THRESHOLD
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                bitrate <= CELLULAR_DIRECT_PLAY_THRESHOLD
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                bitrate <= DIRECT_PLAY_MAX_BITRATE // Ethernet can handle high bitrates
            }
            else -> {
                bitrate <= CELLULAR_DIRECT_PLAY_THRESHOLD // Conservative fallback
            }
        }
    }

    /**
     * Get optimal transcoding URL based on device capabilities and network conditions
     */
    private fun getOptimalTranscodingUrl(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
    ): PlaybackResult.Transcoding {
        val itemId = item.id.toString()
        val networkQuality = getNetworkQuality()
        val deviceCaps = deviceCapabilities.getDirectPlayCapabilities()

        val transcodingParams = when (networkQuality) {
            NetworkQuality.HIGH -> TranscodingParams(
                maxBitrate = 20_000_000, // 20 Mbps
                maxWidth = if (deviceCaps.supports4K) 3840 else 1920,
                maxHeight = if (deviceCaps.supports4K) 2160 else 1080,
                videoCodec = getBestVideoCodec(deviceCaps.supportedVideoCodecs),
                audioCodec = getBestAudioCodec(deviceCaps.supportedAudioCodecs),
                container = "mp4",
            )
            NetworkQuality.MEDIUM -> TranscodingParams(
                maxBitrate = 8_000_000, // 8 Mbps
                maxWidth = 1920,
                maxHeight = 1080,
                videoCodec = "h264", // Most compatible
                audioCodec = "aac",
                container = "mp4",
            )
            NetworkQuality.LOW -> TranscodingParams(
                maxBitrate = 3_000_000, // 3 Mbps
                maxWidth = 1280,
                maxHeight = 720,
                videoCodec = "h264",
                audioCodec = "aac",
                container = "mp4",
            )
        }

        val transcodingUrl = streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = transcodingParams.maxBitrate,
            maxWidth = transcodingParams.maxWidth,
            maxHeight = transcodingParams.maxHeight,
            videoCodec = transcodingParams.videoCodec,
            audioCodec = transcodingParams.audioCodec,
            container = transcodingParams.container,
        ) ?: streamRepository.getStreamUrl(itemId) // Fallback to default

        return PlaybackResult.Transcoding(
            url = transcodingUrl ?: "",
            targetBitrate = transcodingParams.maxBitrate,
            targetResolution = "${transcodingParams.maxWidth}x${transcodingParams.maxHeight}",
            targetVideoCodec = transcodingParams.videoCodec,
            targetAudioCodec = transcodingParams.audioCodec,
            targetContainer = transcodingParams.container,
            reason = "Optimized for $networkQuality network quality",
        )
    }

    /**
     * Get current network quality assessment
     */
    private fun getNetworkQuality(): NetworkQuality {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> NetworkQuality.HIGH
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                // Could add WiFi signal strength detection here
                NetworkQuality.HIGH
            }
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                // Could add cellular signal strength and type (4G/5G) detection here
                NetworkQuality.MEDIUM
            }
            else -> NetworkQuality.LOW
        }
    }

    /**
     * Get playback info from server using repository method
     */
    private suspend fun getPlaybackInfo(itemId: String): PlaybackInfoResponse? {
        return try {
            repository.getPlaybackInfo(itemId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get playback info for item $itemId", e)
            null
        }
    }

    /**
     * Get the best video codec from supported list
     */
    private fun getBestVideoCodec(supportedCodecs: List<String>): String {
        return when {
            supportedCodecs.contains("h265") || supportedCodecs.contains("hevc") -> "h265"
            supportedCodecs.contains("h264") -> "h264"
            supportedCodecs.contains("vp9") -> "vp9"
            supportedCodecs.contains("vp8") -> "vp8"
            else -> "h264" // Fallback
        }
    }

    /**
     * Get the best audio codec from supported list
     */
    private fun getBestAudioCodec(supportedCodecs: List<String>): String {
        return when {
            supportedCodecs.contains("opus") -> "opus"
            supportedCodecs.contains("aac") -> "aac"
            supportedCodecs.contains("mp3") -> "mp3"
            else -> "aac" // Fallback
        }
    }

    /**
     * Extract video codec from media source
     */
    private fun getVideoCodec(mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo): String? {
        return mediaSource.mediaStreams?.find { it.type == MediaStreamType.VIDEO }?.codec
    }

    /**
     * Extract audio codec from media source
     */
    private fun getAudioCodec(mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo): String? {
        return mediaSource.mediaStreams?.find { it.type == MediaStreamType.AUDIO }?.codec
    }
}

/**
 * Network quality assessment
 */
enum class NetworkQuality {
    HIGH, MEDIUM, LOW
}

/**
 * Transcoding parameters
 */
private data class TranscodingParams(
    val maxBitrate: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val videoCodec: String,
    val audioCodec: String,
    val container: String,
)

/**
 * Playback result sealed class
 */
sealed class PlaybackResult {
    data class DirectPlay(
        val url: String,
        val container: String,
        val videoCodec: String?,
        val audioCodec: String?,
        val bitrate: Int,
        val reason: String,
    ) : PlaybackResult()

    data class Transcoding(
        val url: String,
        val targetBitrate: Int,
        val targetResolution: String,
        val targetVideoCodec: String,
        val targetAudioCodec: String,
        val targetContainer: String,
        val reason: String,
    ) : PlaybackResult()

    data class Error(val message: String) : PlaybackResult()
}
