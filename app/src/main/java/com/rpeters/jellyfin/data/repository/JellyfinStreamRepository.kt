package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.rpeters.jellyfin.data.DeviceCapabilities
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository component responsible for streaming URLs, image URLs, and media playback.
 * Extracted from JellyfinRepository to improve code organization and maintainability.
 */
@Singleton
class JellyfinStreamRepository @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val deviceCapabilities: DeviceCapabilities,
) {
    companion object {
        // Stream quality constants
        private const val DEFAULT_MAX_BITRATE = 140_000_000
        private const val DEFAULT_MAX_AUDIO_CHANNELS = 2

        // Image size constants
        private const val DEFAULT_IMAGE_MAX_HEIGHT = 400
        private const val DEFAULT_IMAGE_MAX_WIDTH = 400
        private const val BACKDROP_MAX_HEIGHT = 400
        private const val BACKDROP_MAX_WIDTH = 800

        // Default codecs
        private const val DEFAULT_VIDEO_CODEC = "h264"
        private const val DEFAULT_AUDIO_CODEC = "aac"
        private const val DEFAULT_CONTAINER = "mp4"
    }

    /**
     * Get optimal stream URL - prioritizes direct play when possible, falls back to transcoding
     */
    fun getStreamUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null

        // Validate server connection and authentication
        if (server.accessToken.isNullOrBlank()) {
            Log.w("JellyfinStreamRepository", "getStreamUrl: No access token available")
            return null
        }

        // Validate itemId format
        if (itemId.isBlank()) {
            Log.w("JellyfinStreamRepository", "getStreamUrl: Invalid item ID")
            return null
        }

        // Validate that itemId is a valid UUID format
        runCatching { UUID.fromString(itemId) }.getOrNull() ?: run {
            Log.w("JellyfinStreamRepository", "getStreamUrl: Invalid item ID format: $itemId")
            return null
        }

        return try {
            // First try to get media info to determine if we can direct play
            val directPlayUrl = getOptimalDirectPlayUrl(itemId, server.url, server.accessToken)
            if (directPlayUrl != null) {
                Log.d("JellyfinStreamRepository", "Using direct play for item $itemId")
                return directPlayUrl
            }
            
            // Fall back to adaptive transcoding with device-optimized parameters
            Log.d("JellyfinStreamRepository", "Using transcoded stream for item $itemId")
            getOptimalTranscodedUrl(itemId, server.url, server.accessToken)
        } catch (e: Exception) {
            Log.e("JellyfinStreamRepository", "getStreamUrl: Failed to generate stream URL for item $itemId", e)
            null
        }
    }

    /**
     * Get transcoded stream URL with specific quality parameters
     */
    fun getTranscodedStreamUrl(
        itemId: String,
        maxBitrate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        videoCodec: String = DEFAULT_VIDEO_CODEC,
        audioCodec: String = DEFAULT_AUDIO_CODEC,
        container: String = DEFAULT_CONTAINER,
    ): String? {
        val server = authRepository.getCurrentServer() ?: return null

        // Validate server connection and authentication
        if (server.accessToken.isNullOrBlank()) {
            Log.w("JellyfinStreamRepository", "getTranscodedStreamUrl: No access token available")
            return null
        }

        // Validate itemId format
        if (itemId.isBlank()) {
            Log.w("JellyfinStreamRepository", "getTranscodedStreamUrl: Invalid item ID")
            return null
        }

        // Validate that itemId is a valid UUID format
        runCatching { UUID.fromString(itemId) }.getOrNull() ?: run {
            Log.w("JellyfinStreamRepository", "getTranscodedStreamUrl: Invalid item ID format: $itemId")
            return null
        }

        return try {
            val params = mutableListOf<String>()

            // Add transcoding parameters
            maxBitrate?.let { params.add("MaxStreamingBitrate=$it") }
            maxWidth?.let { params.add("MaxWidth=$it") }
            maxHeight?.let { params.add("MaxHeight=$it") }
            params.add("VideoCodec=$videoCodec")
            params.add("AudioCodec=$audioCodec")
            params.add("Container=$container")
            params.add("TranscodingMaxAudioChannels=$DEFAULT_MAX_AUDIO_CHANNELS")
            params.add("BreakOnNonKeyFrames=true")
            params.add("api_key=${server.accessToken}")

            "${server.url}/Videos/$itemId/master.m3u8?${params.joinToString("&")}"
        } catch (e: Exception) {
            Log.e("JellyfinStreamRepository", "getTranscodedStreamUrl: Failed to generate transcoded stream URL for item $itemId", e)
            null
        }
    }

    /**
     * Get HLS (HTTP Live Streaming) URL for adaptive bitrate streaming
     */
    fun getHlsStreamUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null
        return "${server.url}/Videos/$itemId/master.m3u8?" +
            "VideoCodec=$DEFAULT_VIDEO_CODEC&" +
            "AudioCodec=$DEFAULT_AUDIO_CODEC&" +
            "MaxStreamingBitrate=$DEFAULT_MAX_BITRATE&" +
            "PlaySessionId=${UUID.randomUUID()}&" +
            "api_key=${server.accessToken}"
    }

    /**
     * Get DASH (Dynamic Adaptive Streaming over HTTP) URL
     */
    fun getDashStreamUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null
        return "${server.url}/Videos/$itemId/stream.mpd?" +
            "VideoCodec=$DEFAULT_VIDEO_CODEC&" +
            "AudioCodec=$DEFAULT_AUDIO_CODEC&" +
            "MaxStreamingBitrate=$DEFAULT_MAX_BITRATE&" +
            "PlaySessionId=${UUID.randomUUID()}&" +
            "api_key=${server.accessToken}"
    }

    /**
     * Get download URL for a media item
     */
    fun getDownloadUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null
        return "${server.url}/Items/$itemId/Download?api_key=${server.accessToken}"
    }

    /**
     * Get direct stream URL - forces direct play without transcoding
     */
    fun getDirectStreamUrl(itemId: String, container: String? = null): String? {
        val server = authRepository.getCurrentServer() ?: return null
        val containerParam = container?.let { "&Container=$it" } ?: ""
        return "${server.url}/Videos/$itemId/stream?static=true&api_key=${server.accessToken}$containerParam"
    }
    
    /**
     * Get optimal direct play URL if the device supports the media format
     */
    private fun getOptimalDirectPlayUrl(itemId: String, serverUrl: String, accessToken: String): String? {
        return try {
            // For now, we'll try common container formats that Android supports well
            // In a full implementation, we'd query the media info first
            
            // Try MP4 container first (most compatible)
            if (deviceCapabilities.canPlayContainer("mp4")) {
                val mp4Url = "$serverUrl/Videos/$itemId/stream?static=true&Container=mp4&api_key=$accessToken"
                Log.d("JellyfinStreamRepository", "Trying direct play with MP4 container")
                return mp4Url
            }
            
            // Try original container (MKV is supported on newer Android versions)
            if (deviceCapabilities.canPlayContainer("mkv")) {
                val directUrl = "$serverUrl/Videos/$itemId/stream?static=true&api_key=$accessToken"
                Log.d("JellyfinStreamRepository", "Trying direct play with original container")
                return directUrl
            }
            
            null
        } catch (e: Exception) {
            Log.w("JellyfinStreamRepository", "Failed to determine direct play URL", e)
            null
        }
    }
    
    /**
     * Get optimal transcoded URL based on device capabilities
     */
    private fun getOptimalTranscodedUrl(itemId: String, serverUrl: String, accessToken: String): String {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()
        val maxRes = capabilities.maxResolution
        
        val params = mutableListOf<String>()
        
        // Use best supported video codec
        val videoCodec = when {
            capabilities.supportedVideoCodecs.contains("h264") -> "h264"
            capabilities.supportedVideoCodecs.contains("mpeg4") -> "mpeg4"
            else -> "h264" // Fallback
        }
        
        // Use best supported audio codec
        val audioCodec = when {
            capabilities.supportedAudioCodecs.contains("aac") -> "aac"
            capabilities.supportedAudioCodecs.contains("mp3") -> "mp3"
            else -> "aac" // Fallback
        }
        
        // Use best supported container
        val container = when {
            capabilities.supportedContainers.contains("mp4") -> "mp4"
            capabilities.supportedContainers.contains("webm") -> "webm"
            else -> "mp4" // Fallback
        }
        
        params.add("VideoCodec=$videoCodec")
        params.add("AudioCodec=$audioCodec")
        params.add("Container=$container")
        params.add("MaxStreamingBitrate=20000000") // 20 Mbps max - can be made configurable
        params.add("MaxWidth=${maxRes.first}")
        params.add("MaxHeight=${maxRes.second}")
        params.add("TranscodingMaxAudioChannels=2")
        params.add("BreakOnNonKeyFrames=true")
        params.add("api_key=$accessToken")
        
        Log.d("JellyfinStreamRepository", "Transcoding with: $videoCodec/$audioCodec in $container, max ${maxRes.first}x${maxRes.second}")
        
        return "$serverUrl/Videos/$itemId/stream?${params.joinToString("&")}"
    }

    /**
     * Get image URL for an item
     */
    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String? {
        val server = authRepository.getCurrentServer() ?: return null
        val tagParam = tag?.let { "&tag=$it" } ?: ""
        return "${server.url}/Items/$itemId/Images/$imageType?maxHeight=$DEFAULT_IMAGE_MAX_HEIGHT&maxWidth=$DEFAULT_IMAGE_MAX_WIDTH$tagParam"
    }

    /**
     * Get series image URL for an item (uses series poster for episodes)
     */
    fun getSeriesImageUrl(item: BaseItemDto): String? {
        val server = authRepository.getCurrentServer() ?: return null
        // For episodes, use the series poster if available
        val imageId = if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
            item.seriesId.toString()
        } else {
            item.id.toString()
        }
        return "${server.url}/Items/$imageId/Images/Primary?maxHeight=$DEFAULT_IMAGE_MAX_HEIGHT&maxWidth=$DEFAULT_IMAGE_MAX_WIDTH"
    }

    /**
     * Get backdrop URL for an item
     */
    fun getBackdropUrl(item: BaseItemDto): String? {
        val server = authRepository.getCurrentServer() ?: return null
        val backdropTag = item.backdropImageTags?.firstOrNull()
        return if (backdropTag != null) {
            "${server.url}/Items/${item.id}/Images/Backdrop?tag=$backdropTag&maxHeight=$BACKDROP_MAX_HEIGHT&maxWidth=$BACKDROP_MAX_WIDTH"
        } else {
            getImageUrl(item.id.toString(), "Primary", item.imageTags?.get(ImageType.PRIMARY))
        }
    }
}
