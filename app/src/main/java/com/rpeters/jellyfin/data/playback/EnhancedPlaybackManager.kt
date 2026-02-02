package com.rpeters.jellyfin.data.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.ui.utils.findDefaultAudioStream
import com.rpeters.jellyfin.ui.utils.findDefaultVideoStream
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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
                val itemId = item.id.toString()

                if (BuildConfig.DEBUG) {
                    SecureLogger.v(TAG, "Getting optimal playback URL for: ${item.name} (${item.type})")
                }

                // Get detailed playback info from server
                val playbackInfo = getPlaybackInfo(itemId)
                if (playbackInfo == null) {
                    return@withContext PlaybackResult.Error("Failed to get playback info")
                }

                val serverDirectedResult = getServerDirectedPlaybackUrl(item, playbackInfo)
                if (BuildConfig.DEBUG) {
                    when (serverDirectedResult) {
                        is PlaybackResult.DirectPlay -> {
                            SecureLogger.v(TAG, "Server-directed direct play: ${serverDirectedResult.url}")
                        }
                        is PlaybackResult.Transcoding -> {
                            SecureLogger.v(TAG, "Server-directed transcoding: ${serverDirectedResult.url}")
                        }
                        is PlaybackResult.Error -> {
                            SecureLogger.e(TAG, "Server-directed playback failed: ${serverDirectedResult.message}")
                        }
                    }
                }

                return@withContext serverDirectedResult
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Force transcoding for an item, bypassing direct play checks.
     * Used as a fallback when Direct Play fails at runtime (e.g., audio codec
     * reports supported but actually fails during decoding).
     */
    suspend fun getTranscodingPlaybackUrl(item: BaseItemDto): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                val itemId = item.id.toString()
                SecureLogger.d(TAG, "Forcing transcoding for: ${item.name} (fallback from Direct Play failure)")

                val playbackInfo = getPlaybackInfo(itemId)
                if (playbackInfo == null) {
                    return@withContext PlaybackResult.Error("Failed to get playback info for transcoding fallback")
                }

                getOptimalTranscodingUrl(item, playbackInfo)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Use server playback decision (PlaybackInfo) to select direct play vs transcoding.
     */
    private fun getServerDirectedPlaybackUrl(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val serverUrl = repository.getCurrentServer()?.url
        val mediaSources = playbackInfo.mediaSources

        if (mediaSources.isNullOrEmpty()) {
            SecureLogger.v(TAG, "No media sources available for server-directed playback")
            return PlaybackResult.Error("No media sources available for playback")
        }

        // Check if server recommends transcoding
        val transcodingSource = mediaSources.firstOrNull {
            it.supportsTranscoding && !it.supportsDirectPlay
        } ?: mediaSources.firstOrNull { it.supportsTranscoding }

        if (transcodingSource != null && !transcodingSource.supportsDirectPlay) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d(
                    TAG,
                    "Server recommends transcoding: id=${transcodingSource.id}, " +
                        "container=${transcodingSource.container}, " +
                        "directPlay=${transcodingSource.supportsDirectPlay}, " +
                        "transcode=${transcodingSource.supportsTranscoding}",
                )
            }

            // Use optimized transcoding URL builder which constructs proper parameters
            return getOptimalTranscodingUrl(item, playbackInfo)
        }

        val mediaSource = mediaSources.firstOrNull { it.supportsDirectPlay }
            ?: mediaSources.firstOrNull { it.supportsDirectStream }
            ?: mediaSources.first()
        val mediaSourceId = mediaSource.id
        val container = mediaSource.container ?: "mkv"
        val playSessionId = playbackInfo.playSessionId
        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                TAG,
                "Server-directed direct play source id=$mediaSourceId, " +
                    "container=$container, " +
                    "directPlay=${mediaSource.supportsDirectPlay}, " +
                    "directStream=${mediaSource.supportsDirectStream}, " +
                    "playSessionId=${playSessionId ?: "none"}",
            )
        }
        val directPlayUrl = if (serverUrl != null && mediaSourceId != null) {
            buildString {
                append(serverUrl)
                append("/Videos/")
                append(itemId)
                append("/stream.")
                append(container)
                append("?static=true&mediaSourceId=")
                append(mediaSourceId)
                if (!playSessionId.isNullOrBlank()) {
                    append("&PlaySessionId=")
                    append(playSessionId)
                }
            }
        } else {
            streamRepository.getDirectStreamUrl(itemId, container)
        }

        if (directPlayUrl.isNullOrBlank()) {
            return PlaybackResult.Error("Unable to generate playback URL")
        }

        return PlaybackResult.DirectPlay(
            url = directPlayUrl,
            container = container,
            videoCodec = getVideoCodec(mediaSource),
            audioCodec = getAudioCodec(mediaSource),
            bitrate = mediaSource.bitrate ?: 0,
            reason = "Server-directed direct play",
            playSessionId = playSessionId,
        )
    }

    /**
     * Check if a media source can be directly played
     */
    private fun canDirectPlayMediaSource(
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        item: BaseItemDto,
    ): Boolean {
        if (!mediaSource.supportsDirectPlay) {
            SecureLogger.v(TAG, "Server reports media source does not support Direct Play")
            return false
        }

        // Check container support
        val container = mediaSource.container
        if (!deviceCapabilities.canPlayContainer(container)) {
            SecureLogger.v(TAG, "Container '$container' not supported for Direct Play")
            return false
        }

        // Check video codec support
        val videoStream = mediaSource.mediaStreams.findDefaultVideoStream()
        if (videoStream != null) {
            val videoCodec = videoStream.codec
            val width = videoStream.width ?: 0
            val height = videoStream.height ?: 0

            if (!deviceCapabilities.canPlayVideoCodec(videoCodec, width, height)) {
                SecureLogger.v(TAG, "Video codec '$videoCodec' at ${width}x$height not supported for Direct Play")
                return false
            }
        }

        // Check audio codec support with actual channel count
        val audioStream = mediaSource.mediaStreams.findDefaultAudioStream()
        if (audioStream != null) {
            val audioCodec = audioStream.codec
            val audioChannels = audioStream.channels ?: 2
            if (!deviceCapabilities.canPlayAudioCodec(audioCodec, audioChannels)) {
                SecureLogger.v(TAG, "Audio codec '$audioCodec' ($audioChannels ch) not supported for Direct Play")
                return false
            }
        }

        // Check network conditions for high-bitrate content
        val bitrate = mediaSource.bitrate ?: 0
        if (!isNetworkSuitableForDirectPlay(bitrate)) {
            SecureLogger.v(TAG, "Network conditions not suitable for Direct Play (bitrate: ${bitrate / 1_000_000} Mbps)")
            return false
        }

        return true
    }

    /**
     * Check if network conditions are suitable for Direct Play
     */
    private fun isNetworkSuitableForDirectPlay(bitrate: Int): Boolean {
        // Use safe cast to prevent ClassCastException if service is unavailable
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false // Conservative: assume unsuitable if we can't check
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
     * Get optimal transcoding URL based on device capabilities and network conditions.
     * Returns PlaybackResult.Error if no valid URL can be generated.
     */
    private fun getOptimalTranscodingUrl(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val serverUrl = repository.getCurrentServer()?.url
        val transcodingSource = playbackInfo.mediaSources.firstOrNull {
            it.supportsTranscoding || it.supportsDirectStream
        }
        val serverTranscodingUrl = transcodingSource?.transcodingUrl?.let { url ->
            if (!serverUrl.isNullOrBlank()) buildServerUrl(serverUrl, url) else null
        }
        val mediaSourceId = transcodingSource?.id
        val playSessionId = playbackInfo.playSessionId
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
            NetworkQuality.LOW, NetworkQuality.UNKNOWN -> TranscodingParams(
                maxBitrate = 3_000_000, // 3 Mbps
                maxWidth = 1280,
                maxHeight = 720,
                videoCodec = "h264",
                audioCodec = "aac",
                container = "mp4",
            )
        }

        // Note: We no longer use server-provided transcoding URL as it may be incomplete.
        // Instead, we always build a proper transcoding URL with all required parameters.

        // Try primary transcoding URL
        val transcodingUrl = streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = transcodingParams.maxBitrate,
            maxWidth = transcodingParams.maxWidth,
            maxHeight = transcodingParams.maxHeight,
            videoCodec = transcodingParams.videoCodec,
            audioCodec = transcodingParams.audioCodec,
            container = transcodingParams.container,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
        )

        // Return error if no valid URL could be generated
        if (transcodingUrl.isNullOrBlank()) {
            SecureLogger.e(TAG, "Failed to generate any valid transcoding URL for item $itemId")
            return PlaybackResult.Error("Unable to generate playback URL. Please check server connection.")
        }

        return PlaybackResult.Transcoding(
            url = transcodingUrl,
            targetBitrate = transcodingParams.maxBitrate,
            targetResolution = "${transcodingParams.maxWidth}x${transcodingParams.maxHeight}",
            targetVideoCodec = transcodingParams.videoCodec,
            targetAudioCodec = transcodingParams.audioCodec,
            targetContainer = transcodingParams.container,
            reason = "Optimized for $networkQuality network quality",
            playSessionId = playSessionId,
        )
    }

    /**
     * Get current network quality assessment
     */
    private fun getNetworkQuality(): NetworkQuality {
        // Use safe cast to prevent ClassCastException if service is unavailable
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkQuality.LOW
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
        } catch (e: CancellationException) {
            throw e
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
        return mediaSource.mediaStreams.findDefaultVideoStream()?.codec
    }

    /**
     * Extract audio codec from media source
     */
    private fun getAudioCodec(mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo): String? {
        return mediaSource.mediaStreams?.find { it.type == MediaStreamType.AUDIO }?.codec
    }

    private fun buildServerUrl(serverUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val normalizedServer = serverUrl.removeSuffix("/")
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizedServer + normalizedPath
    }
}

/**
 * Network quality assessment
 */
enum class NetworkQuality {
    HIGH, MEDIUM, LOW, UNKNOWN
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
        val playSessionId: String? = null,
    ) : PlaybackResult()

    data class Transcoding(
        val url: String,
        val targetBitrate: Int,
        val targetResolution: String,
        val targetVideoCodec: String,
        val targetAudioCodec: String,
        val targetContainer: String,
        val reason: String,
        val playSessionId: String? = null,
    ) : PlaybackResult()

    data class Error(val message: String) : PlaybackResult()
}
