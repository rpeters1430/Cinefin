package com.rpeters.jellyfin.data

import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCapabilities @Inject constructor() {

    companion object {
        private const val TAG = "DeviceCapabilities"

        // Supported container formats by Android natively
        val SUPPORTED_CONTAINERS = setOf(
            "mp4", "m4v", "3gp", "3gpp", "3g2", "3gpp2",
            "webm", "mkv", "avi", "mov", "flv", "asf", "wmv",
        )

        // Supported video codecs
        val SUPPORTED_VIDEO_CODECS = setOf(
            "h264", "h265", "hevc", "mpeg4", "mpeg2", "h263",
            "vp8", "vp9", "av1", "mpeg1",
        )

        // Supported audio codecs
        val SUPPORTED_AUDIO_CODECS = setOf(
            "aac", "mp3", "ac3", "eac3", "dts", "flac", "vorbis",
            "opus", "pcm", "amr", "amrnb", "amrwb",
        )
    }

    private var supportedVideoCodecs: Set<String>? = null
    private var supportedAudioCodecs: Set<String>? = null
    private var maxResolution: Pair<Int, Int>? = null

    /**
     * Check if the device can directly play a given media format
     */
    fun canDirectPlay(
        container: String?,
        videoCodec: String?,
        audioCodec: String?,
        width: Int = 0,
        height: Int = 0,
    ): Boolean {
        return canPlayContainer(container) &&
            canPlayVideoCodec(videoCodec, width, height) &&
            canPlayAudioCodec(audioCodec)
    }

    /**
     * Check if container format is supported
     */
    fun canPlayContainer(container: String?): Boolean {
        if (container.isNullOrBlank()) return false
        val normalizedContainer = container.lowercase().removePrefix(".")
        return SUPPORTED_CONTAINERS.contains(normalizedContainer)
    }

    /**
     * Check if video codec is supported at given resolution
     */
    fun canPlayVideoCodec(codec: String?, width: Int = 0, height: Int = 0): Boolean {
        if (codec.isNullOrBlank()) return true // Audio-only content

        val normalizedCodec = normalizeVideoCodec(codec.lowercase())
        if (!SUPPORTED_VIDEO_CODECS.contains(normalizedCodec)) return false

        // Check resolution limits if specified
        if (width > 0 && height > 0) {
            val maxRes = getMaxSupportedResolution()
            if (width > maxRes.first || height > maxRes.second) {
                Log.d(TAG, "Resolution ${width}x$height exceeds device maximum ${maxRes.first}x${maxRes.second}")
                return false
            }
        }

        return isCodecSupported(normalizedCodec, true)
    }

    /**
     * Check if audio codec is supported
     */
    fun canPlayAudioCodec(codec: String?): Boolean {
        if (codec.isNullOrBlank()) return true // Video-only content

        val normalizedCodec = normalizeAudioCodec(codec.lowercase())
        if (!SUPPORTED_AUDIO_CODECS.contains(normalizedCodec)) return false

        return isCodecSupported(normalizedCodec, false)
    }

    /**
     * Get optimal direct play parameters
     */
    fun getDirectPlayCapabilities(): DirectPlayCapabilities {
        return DirectPlayCapabilities(
            supportedContainers = SUPPORTED_CONTAINERS.toList(),
            supportedVideoCodecs = getSupportedVideoCodecs(),
            supportedAudioCodecs = getSupportedAudioCodecs(),
            maxResolution = getMaxSupportedResolution(),
            supportsHdr = supportsHdr(),
            supports4K = supports4K(),
        )
    }

    private fun getSupportedVideoCodecs(): List<String> {
        if (supportedVideoCodecs == null) {
            supportedVideoCodecs = detectSupportedCodecs(true)
        }
        return supportedVideoCodecs!!.toList()
    }

    private fun getSupportedAudioCodecs(): List<String> {
        if (supportedAudioCodecs == null) {
            supportedAudioCodecs = detectSupportedCodecs(false)
        }
        return supportedAudioCodecs!!.toList()
    }

    private fun detectSupportedCodecs(isVideo: Boolean): Set<String> {
        val supportedCodecs = mutableSetOf<String>()

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue // We only care about decoders

                for (type in codecInfo.supportedTypes) {
                    val codec = mimeTypeToCodec(type, isVideo)
                    if (codec != null) {
                        supportedCodecs.add(codec)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect supported codecs", e)
            // Fallback to common codecs
            if (isVideo) {
                supportedCodecs.addAll(setOf("h264", "mpeg4", "h263", "vp8"))
            } else {
                supportedCodecs.addAll(setOf("aac", "mp3", "vorbis"))
            }
        }

        Log.d(TAG, "${if (isVideo) "Video" else "Audio"} codecs supported: $supportedCodecs")
        return supportedCodecs
    }

    private fun mimeTypeToCodec(mimeType: String, isVideo: Boolean): String? {
        return when (mimeType.lowercase()) {
            // Video codecs
            "video/avc" -> if (isVideo) "h264" else null
            "video/h264" -> if (isVideo) "h264" else null
            "video/hevc" -> if (isVideo) "h265" else null
            "video/h265" -> if (isVideo) "h265" else null
            "video/mp4v-es" -> if (isVideo) "mpeg4" else null
            "video/3gpp" -> if (isVideo) "h263" else null
            "video/x-vnd.on2.vp8" -> if (isVideo) "vp8" else null
            "video/x-vnd.on2.vp9" -> if (isVideo) "vp9" else null
            "video/av01" -> if (isVideo) "av1" else null
            // Audio codecs
            "audio/mp4a-latm" -> if (!isVideo) "aac" else null
            "audio/mpeg" -> if (!isVideo) "mp3" else null
            "audio/mpeg-l1" -> if (!isVideo) "mp3" else null
            "audio/mpeg-l2" -> if (!isVideo) "mp3" else null
            "audio/ac3" -> if (!isVideo) "ac3" else null
            "audio/eac3" -> if (!isVideo) "eac3" else null
            "audio/vorbis" -> if (!isVideo) "vorbis" else null
            "audio/opus" -> if (!isVideo) "opus" else null
            "audio/flac" -> if (!isVideo) "flac" else null
            "audio/raw" -> if (!isVideo) "pcm" else null
            else -> null
        }
    }

    private fun normalizeVideoCodec(codec: String): String {
        return when (codec.lowercase()) {
            "avc", "x264" -> "h264"
            "hevc", "x265" -> "h265"
            "vp08" -> "vp8"
            "vp09" -> "vp9"
            else -> codec
        }
    }

    private fun normalizeAudioCodec(codec: String): String {
        return when (codec.lowercase()) {
            "aac-lc", "aac-he", "aac-he-v2" -> "aac"
            "mp3float" -> "mp3"
            "ac-3" -> "ac3"
            "e-ac-3" -> "eac3"
            else -> codec
        }
    }

    private fun isCodecSupported(codec: String, isVideo: Boolean): Boolean {
        // For Android 5.0+ we can rely on MediaCodecList
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val mimeType = codecToMimeType(codec, isVideo) ?: return false
                val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                codecList.findDecoderForFormat(android.media.MediaFormat.createVideoFormat(mimeType, 1920, 1080)) != null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check codec support for $codec", e)
                true // Assume supported if we can't check
            }
        } else {
            true // Assume supported on older versions
        }
    }

    private fun codecToMimeType(codec: String, isVideo: Boolean): String? {
        return when (codec.lowercase()) {
            "h264" -> if (isVideo) "video/avc" else null
            "h265", "hevc" -> if (isVideo) "video/hevc" else null
            "mpeg4" -> if (isVideo) "video/mp4v-es" else null
            "h263" -> if (isVideo) "video/3gpp" else null
            "vp8" -> if (isVideo) "video/x-vnd.on2.vp8" else null
            "vp9" -> if (isVideo) "video/x-vnd.on2.vp9" else null
            "av1" -> if (isVideo) "video/av01" else null
            "aac" -> if (!isVideo) "audio/mp4a-latm" else null
            "mp3" -> if (!isVideo) "audio/mpeg" else null
            "ac3" -> if (!isVideo) "audio/ac3" else null
            "eac3" -> if (!isVideo) "audio/eac3" else null
            "vorbis" -> if (!isVideo) "audio/vorbis" else null
            "opus" -> if (!isVideo) "audio/opus" else null
            "flac" -> if (!isVideo) "audio/flac" else null
            "pcm" -> if (!isVideo) "audio/raw" else null
            else -> null
        }
    }

    private fun getMaxSupportedResolution(): Pair<Int, Int> {
        if (maxResolution == null) {
            maxResolution = try {
                val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                var maxWidth = 1920
                var maxHeight = 1080

                for (codecInfo in codecList.codecInfos) {
                    if (codecInfo.isEncoder) continue

                    for (type in codecInfo.supportedTypes) {
                        if (type.startsWith("video/")) {
                            try {
                                val capabilities = codecInfo.getCapabilitiesForType(type)
                                val videoCapabilities = capabilities.videoCapabilities
                                if (videoCapabilities != null) {
                                    maxWidth = maxOf(maxWidth, videoCapabilities.supportedWidths.upper)
                                    maxHeight = maxOf(maxHeight, videoCapabilities.supportedHeights.upper)
                                }
                            } catch (e: Exception) {
                                // Ignore codec-specific errors
                            }
                        }
                    }
                }

                Pair(maxWidth, maxHeight)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to detect max resolution", e)
                Pair(1920, 1080) // Fallback to 1080p
            }
        }
        return maxResolution!!
    }

    private fun supportsHdr(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    private fun supports4K(): Boolean {
        val maxRes = getMaxSupportedResolution()
        return maxRes.first >= 3840 && maxRes.second >= 2160
    }
}

data class DirectPlayCapabilities(
    val supportedContainers: List<String>,
    val supportedVideoCodecs: List<String>,
    val supportedAudioCodecs: List<String>,
    val maxResolution: Pair<Int, Int>,
    val supportsHdr: Boolean,
    val supports4K: Boolean,
)
