package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.net.URLEncoder

/**
 * Builds Plex Media Server URLs for images and transcode operations (KD8).
 */
object PlexUrlBuilder {
    const val DEFAULT_WIDTH = 800
    const val DEFAULT_HEIGHT = 800
    const val BACKDROP_WIDTH = 1920
    const val BACKDROP_HEIGHT = 1080

    /**
     * Builds a resized image URL through PMS `/photo/:/transcode` with auth token attached.
     */
    fun transcodeImageUrl(
        serverUrl: String,
        token: String,
        path: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        minSize: Int = 1,
        upscale: Int = 1,
    ): String {
        val base = serverUrl.trimEnd('/')
        val encodedPath = URLEncoder.encode(path, "UTF-8")
        return "$base/photo/:/transcode?url=$encodedPath&width=$width&height=$height&minSize=$minSize&upscale=$upscale&${PlexTokenUrls.TOKEN_PARAMETER}=$token"
    }

    /**
     * Primary, backdrop, or logo image URL for an item.
     */
    fun getImageUrl(
        server: JellyfinServer,
        itemId: String,
        imageType: String = "Primary",
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
    ): String? {
        val token = server.accessToken ?: return null
        val rk = ServerIdCodec.decode(itemId)
        val imagePath = when (imageType.lowercase()) {
            "backdrop", "art" -> "/library/metadata/$rk/art"
            "logo", "clearlogo" -> "/library/metadata/$rk/clearLogo"
            else -> "/library/metadata/$rk/thumb"
        }
        return transcodeImageUrl(server.url, token, imagePath, width, height)
    }

    /**
     * Series image URL for an item (uses series poster for episodes).
     */
    fun getSeriesImageUrl(
        server: JellyfinServer,
        item: BaseItemDto,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
    ): String? {
        val token = server.accessToken ?: return null
        val rk = if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
            ServerIdCodec.decode(item.seriesId.toString())
        } else {
            ServerIdCodec.decode(item.id.toString())
        }
        val imagePath = "/library/metadata/$rk/thumb"
        return transcodeImageUrl(server.url, token, imagePath, width, height)
    }

    /**
     * Backdrop URL for an item.
     */
    fun getBackdropUrl(
        server: JellyfinServer,
        item: BaseItemDto,
        width: Int = BACKDROP_WIDTH,
        height: Int = BACKDROP_HEIGHT,
    ): String? {
        val token = server.accessToken ?: return null
        val rk = ServerIdCodec.decode(item.id.toString())
        val imagePath = "/library/metadata/$rk/art"
        return transcodeImageUrl(server.url, token, imagePath, width, height)
    }

    /**
     * Builds direct play media stream URL (KD5).
     */
    fun buildDirectPlayStreamUrl(
        server: JellyfinServer,
        itemId: String,
        partKey: String? = null,
    ): String? {
        val token = server.accessToken ?: return null
        val base = server.url.trimEnd('/')
        val path = if (!partKey.isNullOrBlank()) {
            if (partKey.startsWith("/")) partKey else "/$partKey"
        } else {
            val rk = ServerIdCodec.decode(itemId)
            "/library/metadata/$rk"
        }
        return "$base$path?download=0&${PlexTokenUrls.TOKEN_PARAMETER}=$token"
    }

    /**
     * Builds HLS transcode / direct stream URL (KD5).
     */
    fun buildTranscodeStreamUrl(
        server: JellyfinServer,
        itemId: String,
        session: String = java.util.UUID.randomUUID().toString(),
        maxBitrate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        audioChannels: Int? = null,
        allowVideoStreamCopy: Boolean = true,
        allowAudioStreamCopy: Boolean = true,
    ): String? {
        val token = server.accessToken ?: return null
        val base = server.url.trimEnd('/')
        val rk = ServerIdCodec.decode(itemId)
        val metadataPath = URLEncoder.encode("/library/metadata/$rk", "UTF-8")

        val queryParams = mutableListOf(
            "path=$metadataPath",
            "mediaIndex=0",
            "partIndex=0",
            "protocol=hls",
            "fastSeek=1",
            "copyts=1",
            "session=$session",
            "directPlay=0",
            "directStream=${if (allowVideoStreamCopy) 1 else 0}",
            "subtitleSize=100",
            "audioBoost=100",
            "autoAdjustQuality=0",
        )
        if (maxBitrate != null && maxBitrate > 0) {
            queryParams.add("maxVideoBitrate=${maxBitrate / 1000}")
        }
        if (maxWidth != null && maxHeight != null) {
            queryParams.add("videoResolution=${maxWidth}x${maxHeight}")
        }
        if (audioChannels != null) {
            queryParams.add("audioChannels=$audioChannels")
        }
        queryParams.add("${PlexTokenUrls.TOKEN_PARAMETER}=$token")

        return "$base/video/:/transcode/universal/start.m3u8?${queryParams.joinToString("&")}"
    }

    /**
     * Builds download URL for a media item or part (KD5).
     */
    fun buildDownloadUrl(
        server: JellyfinServer,
        itemId: String,
        partKey: String? = null,
    ): String? {
        val token = server.accessToken ?: return null
        val base = server.url.trimEnd('/')
        val path = if (!partKey.isNullOrBlank()) {
            if (partKey.startsWith("/")) partKey else "/$partKey"
        } else {
            val rk = ServerIdCodec.decode(itemId)
            "/library/metadata/$rk"
        }
        return "$base$path?download=1&${PlexTokenUrls.TOKEN_PARAMETER}=$token"
    }

    /**
     * Builds universal transcoder stop URL for session cleanup (KD5).
     */
    fun buildTranscodeStopUrl(
        server: JellyfinServer,
        session: String,
    ): String? {
        val token = server.accessToken ?: return null
        val base = server.url.trimEnd('/')
        return "$base/video/:/transcode/universal/stop?session=$session&${PlexTokenUrls.TOKEN_PARAMETER}=$token"
    }
}
