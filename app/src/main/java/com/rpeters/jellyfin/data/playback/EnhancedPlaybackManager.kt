package com.rpeters.jellyfin.data.playback

import android.content.Context
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.data.preferences.PlaybackPreferencesRepository
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.network.ConnectivityQuality
import com.rpeters.jellyfin.network.NetworkType
import com.rpeters.jellyfin.ui.utils.findDefaultAudioStream
import com.rpeters.jellyfin.ui.utils.findDefaultVideoStream
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    private val connectivityChecker: ConnectivityChecker,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
) {

    companion object {
        private const val TAG = "EnhancedPlaybackManager"

        // Network quality thresholds (in Mbps)
        private const val HIGH_QUALITY_THRESHOLD = 25
        private const val MEDIUM_QUALITY_THRESHOLD = 10
        private const val LOW_QUALITY_THRESHOLD = 3

        // Direct play bitrate thresholds (defaults, will be overridden by user prefs)
        private const val DIRECT_PLAY_MAX_BITRATE = 140_000_000 // 140 Mbps
    }

    /**
     * Get optimal playback URL with intelligent Direct Play detection
     */
    suspend fun getOptimalPlaybackUrl(
        item: BaseItemDto,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                val itemId = item.id.toString()

                if (BuildConfig.DEBUG) {
                    SecureLogger.v(TAG, "Getting optimal playback URL for: ${item.name} (${item.type}) [Audio: $audioStreamIndex, Sub: $subtitleStreamIndex]")
                }

                // Get detailed playback info from server
                val playbackInfo = getPlaybackInfo(itemId, audioStreamIndex, subtitleStreamIndex)
                if (playbackInfo == null) {
                    return@withContext PlaybackResult.Error("Failed to get playback info")
                }

                val serverDirectedResult = getServerDirectedPlaybackUrl(item, playbackInfo, audioStreamIndex, subtitleStreamIndex)
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
     * Used as a fallback when Direct Play fails at runtime.
     */
    suspend fun getTranscodingPlaybackUrl(
        item: BaseItemDto,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                val itemId = item.id.toString()
                SecureLogger.d(TAG, "Forcing transcoding for: ${item.name} (fallback from Direct Play failure)")

                val playbackInfo = getPlaybackInfo(itemId, audioStreamIndex, subtitleStreamIndex)
                if (playbackInfo == null) {
                    return@withContext PlaybackResult.Error("Failed to get playback info for transcoding fallback")
                }

                getOptimalTranscodingUrl(item, playbackInfo, audioStreamIndex, subtitleStreamIndex)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Use server playback decision (PlaybackInfo) to select direct play vs transcoding.
     */
    private suspend fun getServerDirectedPlaybackUrl(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val serverUrl = repository.getCurrentServer()?.url
        val mediaSources = playbackInfo.mediaSources

        if (mediaSources.isNullOrEmpty()) {
            SecureLogger.v(TAG, "No media sources available for server-directed playback")
            return PlaybackResult.Error("No media sources available for playback")
        }

        if (BuildConfig.DEBUG) {
            SecureLogger.d(TAG, "Received ${mediaSources.size} media sources from server:")
            mediaSources.forEachIndexed { index, source ->
                SecureLogger.d(TAG, "  Source[$index]: directPlay=${source.supportsDirectPlay}, directStream=${source.supportsDirectStream}, transcode=${source.supportsTranscoding}")
            }
        }

        // WORKAROUND: If server says directPlay=false BUT we know the device can play it, force it anyway
        // This bypasses broken Jellyfin server logic that incorrectly rejects 10-bit HEVC
        val anySource = mediaSources.firstOrNull()
        if (anySource != null && !anySource.supportsDirectPlay && canDirectPlayMediaSource(anySource, item, bypassServerDecision = true)) {
            val mediaSourceId = anySource.id
            val container = anySource.container ?: "mkv"
            val playSessionId = playbackInfo.playSessionId

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

            if (!directPlayUrl.isNullOrBlank()) {
                val videoStream = anySource.mediaStreams?.findDefaultVideoStream()
                val audioStream = anySource.mediaStreams?.findDefaultAudioStream()

                SecureLogger.d(TAG, "🔥 BYPASSING broken server decision - forcing Direct Play despite server saying NO")
                return PlaybackResult.DirectPlay(
                    url = directPlayUrl,
                    container = container,
                    videoCodec = videoStream?.codec,
                    audioCodec = audioStream?.codec,
                    bitrate = anySource.bitrate ?: 0,
                    reason = "Server incorrectly rejected 10-bit HEVC - device IS capable",
                    playSessionId = playSessionId,
                )
            }
        }

        // FORCE Direct Play if server says it's supported AND device is capable
        val directPlaySource = mediaSources.firstOrNull { it.supportsDirectPlay }
        if (directPlaySource != null) {
            // Verify device can actually play it
            if (canDirectPlayMediaSource(directPlaySource, item)) {
                val mediaSourceId = directPlaySource.id
                val container = directPlaySource.container ?: "mkv"
                val playSessionId = playbackInfo.playSessionId

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

                if (!directPlayUrl.isNullOrBlank()) {
                    val videoStream = directPlaySource.mediaStreams?.findDefaultVideoStream()
                    val audioStream = directPlaySource.mediaStreams?.findDefaultAudioStream()

                    SecureLogger.d(TAG, "✅ FORCING Direct Play - server supports it and device is capable")
                    return PlaybackResult.DirectPlay(
                        url = directPlayUrl,
                        container = container,
                        videoCodec = videoStream?.codec,
                        audioCodec = audioStream?.codec,
                        bitrate = directPlaySource.bitrate ?: 0,
                        reason = "Server SupportsDirectPlay=true and device supports ${videoStream?.codec}/${audioStream?.codec}",
                        playSessionId = playSessionId,
                    )
                }
            } else {
                SecureLogger.d(TAG, "⚠️ Server supports DirectPlay but device check failed")
            }
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
            return getOptimalTranscodingUrl(item, playbackInfo, audioStreamIndex, subtitleStreamIndex)
        }

        // Potential Direct Play source
        val mediaSource = mediaSources.firstOrNull { it.supportsDirectPlay }
            ?: mediaSources.firstOrNull { it.supportsDirectStream }
            ?: mediaSources.first()

        // Double check if it's REALLY suitable for Direct Play based on our logic
        if (!canDirectPlayMediaSource(mediaSource, item)) {
            // Full Direct Play not possible, but check if Direct Stream is possible
            // (video can be played natively, only audio needs transcoding)
            if (canDirectStreamMediaSource(mediaSource, item)) {
                SecureLogger.d(TAG, "🎬 Video codec supported but audio needs transcoding - using Direct Stream (audio-only transcode)")
                return getDirectStreamWithAudioTranscode(item, mediaSource, playbackInfo, audioStreamIndex, subtitleStreamIndex)
            }

            // Neither Direct Play nor Direct Stream possible - must transcode everything
            return getOptimalTranscodingUrl(item, playbackInfo, audioStreamIndex, subtitleStreamIndex)
        }

        val mediaSourceId = mediaSource.id
        val container = mediaSource.container ?: "mkv"
        val playSessionId = playbackInfo.playSessionId

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
     * Check if a media source can be directly played (both video and audio supported)
     */
    private suspend fun canDirectPlayMediaSource(
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        item: BaseItemDto,
        bypassServerDecision: Boolean = false,
    ): Boolean {
        if (!bypassServerDecision) {
            if (!mediaSource.supportsDirectPlay && !mediaSource.supportsDirectStream) {
                SecureLogger.d(TAG, "❌ Server says DirectPlay=false, DirectStream=false")
                return false
            }
        }

        // Check container support
        val container = mediaSource.container
        if (!deviceCapabilities.canPlayContainer(container)) {
            SecureLogger.d(TAG, "❌ Container '$container' not supported for Direct Play")
            return false
        }

        // Check video codec support
        val videoStream = mediaSource.mediaStreams.findDefaultVideoStream()
        if (videoStream != null) {
            val videoCodec = videoStream.codec
            val width = videoStream.width ?: 0
            val height = videoStream.height ?: 0

            if (!deviceCapabilities.canPlayVideoCodec(videoCodec, width, height)) {
                SecureLogger.d(TAG, "❌ Video codec '$videoCodec' at ${width}x$height not supported for Direct Play")
                return false
            }
        }

        // Check audio codec support with actual channel count
        val audioStream = mediaSource.mediaStreams.findDefaultAudioStream()
        if (audioStream != null) {
            val audioCodec = audioStream.codec
            val audioChannels = audioStream.channels ?: 2
            if (!deviceCapabilities.canPlayAudioCodec(audioCodec, audioChannels)) {
                SecureLogger.d(TAG, "❌ Audio codec '$audioCodec' ($audioChannels ch) not supported for Direct Play")
                return false
            }
        }

        // Check network conditions for high-bitrate content
        val bitrate = mediaSource.bitrate ?: 0
        if (!isNetworkSuitableForDirectPlay(bitrate)) {
            SecureLogger.d(TAG, "❌ Network conditions not suitable for Direct Play (bitrate: ${bitrate / 1_000_000} Mbps)")
            return false
        }

        SecureLogger.d(TAG, "✅ Device CAN direct play: container=$container, video=${videoStream?.codec}, audio=${audioStream?.codec}")
        return true
    }

    /**
     * Check if a media source can be direct streamed (video supported, audio may need transcoding)
     * This is better than full transcoding when only audio is incompatible.
     */
    private suspend fun canDirectStreamMediaSource(
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        item: BaseItemDto,
    ): Boolean {
        // Check container support
        val container = mediaSource.container
        if (!deviceCapabilities.canPlayContainer(container)) {
            SecureLogger.d(TAG, "❌ Container '$container' not supported for Direct Stream")
            return false
        }

        // Check video codec support (REQUIRED for direct stream)
        val videoStream = mediaSource.mediaStreams.findDefaultVideoStream()
        if (videoStream != null) {
            val videoCodec = videoStream.codec
            val width = videoStream.width ?: 0
            val height = videoStream.height ?: 0

            if (!deviceCapabilities.canPlayVideoCodec(videoCodec, width, height)) {
                SecureLogger.d(TAG, "❌ Video codec '$videoCodec' at ${width}x$height not supported for Direct Stream")
                return false
            }
        } else {
            // No video stream means this is audio-only content
            return false
        }

        // Check network conditions
        val bitrate = mediaSource.bitrate ?: 0
        if (!isNetworkSuitableForDirectPlay(bitrate)) {
            SecureLogger.d(TAG, "❌ Network conditions not suitable for Direct Stream (bitrate: ${bitrate / 1_000_000} Mbps)")
            return false
        }

        // Audio codec check is intentionally NOT included - that's the whole point of Direct Stream!
        val audioStream = mediaSource.mediaStreams.findDefaultAudioStream()
        val audioCodec = audioStream?.codec
        SecureLogger.d(TAG, "✅ Device CAN direct stream: container=$container, video=${videoStream.codec}, audio=$audioCodec (may transcode audio)")
        return true
    }

    /**
     * Check if network conditions are suitable for Direct Play
     */
    private suspend fun isNetworkSuitableForDirectPlay(bitrate: Int): Boolean {
        val type = connectivityChecker.getNetworkType()
        val prefs = playbackPreferencesRepository.preferences.first()

        return when (type) {
            NetworkType.WIFI -> bitrate <= prefs.maxBitrateWifi
            NetworkType.CELLULAR -> bitrate <= prefs.maxBitrateCellular
            NetworkType.ETHERNET -> bitrate <= DIRECT_PLAY_MAX_BITRATE
            else -> bitrate <= LOW_QUALITY_THRESHOLD * 1_000_000
        }
    }

    /**
     * Get Direct Stream URL with audio-only transcoding.
     * This keeps the original video stream and only transcodes the audio,
     * which is much more efficient than transcoding both.
     */
    private suspend fun getDirectStreamWithAudioTranscode(
        item: BaseItemDto,
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        playbackInfo: PlaybackInfoResponse,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val serverUrl = repository.getCurrentServer()?.url
        val mediaSourceId = mediaSource.id
        val playSessionId = playbackInfo.playSessionId
        val prefs = playbackPreferencesRepository.preferences.first()

        // Get source video info (we're keeping this unchanged)
        val sourceVideoStream = mediaSource.mediaStreams?.findDefaultVideoStream()
        val sourceAudioStream = mediaSource.mediaStreams?.findDefaultAudioStream()

        val maxAudioChannels = prefs.audioChannels.channels ?: 2

        // Build Direct Stream URL with audio transcoding parameters
        // VideoCodec=copy tells the server to NOT transcode video
        val directStreamUrl = streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = mediaSource.bitrate ?: 20_000_000, // Use source bitrate
            maxWidth = sourceVideoStream?.width ?: 1920,
            maxHeight = sourceVideoStream?.height ?: 1080,
            videoCodec = "copy", // CRITICAL: This tells server to copy video stream without transcoding
            audioCodec = "aac", // Transcode audio to AAC (universally supported)
            container = "ts", // Use TS container for better streaming compatibility
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxAudioChannels = maxAudioChannels,
        )

        if (directStreamUrl.isNullOrBlank()) {
            SecureLogger.e(TAG, "Failed to generate Direct Stream URL with audio transcode for item $itemId")
            return PlaybackResult.Error("Unable to generate playback URL")
        }

        SecureLogger.d(
            TAG,
            "Direct Stream with audio transcode: video=${sourceVideoStream?.codec} (copy), " +
                "audio=${sourceAudioStream?.codec} → aac, " +
                "channels: ${sourceAudioStream?.channels} → $maxAudioChannels"
        )

        return PlaybackResult.Transcoding(
            url = directStreamUrl,
            targetBitrate = mediaSource.bitrate ?: 20_000_000,
            targetResolution = "${sourceVideoStream?.width ?: 1920}x${sourceVideoStream?.height ?: 1080}",
            targetVideoCodec = sourceVideoStream?.codec ?: "h264", // Original codec
            targetAudioCodec = "aac",
            targetContainer = "ts",
            reason = "Direct Stream: Original video (${sourceVideoStream?.codec}), audio transcoded (${sourceAudioStream?.codec} → aac)",
            playSessionId = playSessionId,
        )
    }

    /**
     * Get optimal transcoding URL based on device capabilities and network conditions.
     * Returns PlaybackResult.Error if no valid URL can be generated.
     */
    private suspend fun getOptimalTranscodingUrl(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val transcodingSource = playbackInfo.mediaSources.firstOrNull {
            it.supportsTranscoding || it.supportsDirectStream
        }
        val mediaSourceId = transcodingSource?.id
        val playSessionId = playbackInfo.playSessionId
        val deviceCaps = deviceCapabilities.getDirectPlayCapabilities()
        val prefs = playbackPreferencesRepository.preferences.first()

        // Get source video resolution and codec to prevent upscaling and unnecessary transcoding
        val sourceVideoStream = transcodingSource?.mediaStreams?.findDefaultVideoStream()
        val sourceWidth = sourceVideoStream?.width ?: 1920
        val sourceHeight = sourceVideoStream?.height ?: 1080
        val sourceVideoCodec = sourceVideoStream?.codec?.lowercase() ?: "h264"

        // Determine effective quality based on network AND user preference
        val networkQuality = connectivityChecker.getNetworkQuality()
        val effectiveQuality = if (prefs.transcodingQuality != TranscodingQuality.AUTO) {
            prefs.transcodingQuality
        } else {
            // Map ConnectivityQuality to TranscodingQuality
            when (networkQuality) {
                ConnectivityQuality.EXCELLENT -> TranscodingQuality.MAXIMUM
                ConnectivityQuality.GOOD -> TranscodingQuality.HIGH
                ConnectivityQuality.FAIR -> TranscodingQuality.MEDIUM
                ConnectivityQuality.POOR -> TranscodingQuality.LOW
            }
        }

        val transcodingParams = when (effectiveQuality) {
            TranscodingQuality.MAXIMUM -> TranscodingParams(
                maxBitrate = 60_000_000, // 60 Mbps
                maxWidth = minOf(if (deviceCaps.supports4K) 3840 else 1920, sourceWidth),
                maxHeight = minOf(if (deviceCaps.supports4K) 2160 else 1080, sourceHeight),
                videoCodec = if (deviceCaps.supportedVideoCodecs.contains(sourceVideoCodec)) sourceVideoCodec else getBestVideoCodec(deviceCaps.supportedVideoCodecs),
                audioCodec = getBestAudioCodec(deviceCaps.supportedAudioCodecs),
                container = "mp4",
            )
            TranscodingQuality.HIGH -> TranscodingParams(
                maxBitrate = 20_000_000, // 20 Mbps
                maxWidth = minOf(1920, sourceWidth),
                maxHeight = minOf(1080, sourceHeight),
                videoCodec = if (deviceCaps.supportedVideoCodecs.contains(sourceVideoCodec)) sourceVideoCodec else "h264",
                audioCodec = "aac",
                container = "mp4",
            )
            TranscodingQuality.MEDIUM -> TranscodingParams(
                maxBitrate = 8_000_000, // 8 Mbps
                maxWidth = minOf(1280, sourceWidth),
                maxHeight = minOf(720, sourceHeight),
                videoCodec = "h264",
                audioCodec = "aac",
                container = "mp4",
            )
            TranscodingQuality.LOW -> TranscodingParams(
                maxBitrate = 3_000_000, // 3 Mbps
                maxWidth = minOf(854, sourceWidth),
                maxHeight = minOf(480, sourceHeight),
                videoCodec = "h264",
                audioCodec = "aac",
                container = "mp4",
            )
            TranscodingQuality.AUTO -> {
                // This branch shouldn't be reached due to effectiveQuality logic above
                TranscodingParams(20_000_000, 1920, 1080, "h264", "aac", "mp4")
            }
        }

        val maxAudioChannels = prefs.audioChannels.channels ?: 2

        SecureLogger.d(
            TAG,
            "Transcoding params: source=${sourceWidth}x$sourceHeight, " +
                "target=${transcodingParams.maxWidth}x${transcodingParams.maxHeight}, " +
                "bitrate=${transcodingParams.maxBitrate / 1_000_000}Mbps, " +
                "quality=$effectiveQuality, channels=$maxAudioChannels",
        )

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
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxAudioChannels = maxAudioChannels,
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
            reason = "Optimized for $effectiveQuality quality",
            playSessionId = playSessionId,
        )
    }

    /**
     * Get playback info from server using repository method
     */
    private suspend fun getPlaybackInfo(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackInfoResponse? {
        return try {
            repository.getPlaybackInfo(itemId, audioStreamIndex, subtitleStreamIndex)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SecureLogger.e(TAG, "Failed to get playback info for item $itemId", e)
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
