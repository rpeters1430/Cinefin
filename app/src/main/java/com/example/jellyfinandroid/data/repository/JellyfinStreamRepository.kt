package com.example.jellyfinandroid.data.repository

import android.util.Log
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
     * Get basic stream URL for a media item
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
            "${server.url}/Videos/$itemId/stream?static=true&api_key=${server.accessToken}"
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
     * Get direct stream URL optimized for downloads
     */
    fun getDirectStreamUrl(itemId: String, container: String? = null): String? {
        val server = authRepository.getCurrentServer() ?: return null
        val containerParam = container?.let { "&Container=$it" } ?: ""
        return "${server.url}/Videos/$itemId/stream.${container ?: "mp4"}?static=true&api_key=${server.accessToken}$containerParam"
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
