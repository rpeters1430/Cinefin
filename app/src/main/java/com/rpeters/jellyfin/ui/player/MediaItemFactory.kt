package com.rpeters.jellyfin.ui.player

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Factory for creating MediaItem instances with support for side-loaded subtitles
 * and proper MIME type hints for HLS/DASH content.
 */
@UnstableApi
object MediaItemFactory {

    /**
     * Build a MediaItem with optional side-loaded subtitle tracks and MIME type hint
     */
    fun build(
        videoUrl: String,
        title: String?,
        sideLoadedSubs: List<SubtitleSpec> = emptyList(),
        mimeTypeHint: String? = null,
    ): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build(),
            )

        // If you know it's HLS or DASH, set the correct MIME type
        mimeTypeHint?.let { builder.setMimeType(it) }

        // Add side-loaded subtitle configurations
        if (sideLoadedSubs.isNotEmpty()) {
            val subtitleConfigurations = sideLoadedSubs.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                    .setMimeType(sub.mimeType)
                    .setLanguage(sub.language)
                    .setLabel(sub.label ?: sub.language?.uppercase() ?: "Subtitles")
                    .setSelectionFlags(if (sub.isForced) C.SELECTION_FLAG_FORCED else 0)
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigurations)
        }

        return builder.build()
    }

    /**
     * Infer MIME type from URL for proper Media3 pipeline selection
     */
    fun inferMimeType(url: String): String = when {
        url.endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        url.endsWith(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
        else -> MimeTypes.VIDEO_MP4 // safe default
    }
}

/**
 * Data class representing a subtitle track specification
 */
data class SubtitleSpec(
    val url: String,
    val mimeType: String, // MimeTypes.TEXT_VTT, APPLICATION_SUBRIP, TEXT_SSA, APPLICATION_TTML
    val language: String?,
    val label: String? = null,
    val isForced: Boolean = false,
) {
    companion object {
        /**
         * Map common subtitle file extensions to Media3 MIME types
         */
        fun mimeTypeFromExtension(extension: String): String = when (extension.lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            "ttml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT // default to WebVTT
        }

        /**
         * Create SubtitleSpec from URL with automatic MIME type detection
         */
        fun fromUrl(
            url: String,
            language: String?,
            label: String? = null,
            isForced: Boolean = false,
        ): SubtitleSpec {
            val extension = url.substringAfterLast('.', "")
            val mimeType = mimeTypeFromExtension(extension)

            return SubtitleSpec(
                url = url,
                mimeType = mimeType,
                language = language,
                label = label,
                isForced = isForced,
            )
        }
    }
}
