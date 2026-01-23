package com.rpeters.jellyfin.data.model

import android.util.Log
import com.rpeters.jellyfin.data.DeviceCapabilities
import org.jellyfin.sdk.model.api.*

/**
 * Creates a device profile optimized for direct play with dynamic capability detection.
 * This profile adapts to the actual device hardware capabilities for optimal streaming decisions.
 */
object JellyfinDeviceProfile {

    fun createAndroidDeviceProfile(): DeviceProfile {
        return createAndroidDeviceProfile(maxWidth = 1920, maxHeight = 1080)
    }

    fun createAndroidDeviceProfile(maxWidth: Int = 1920, maxHeight: Int = 1080): DeviceProfile {
        Log.d("JellyfinDeviceProfile", "Creating device profile with maxWidth=$maxWidth, maxHeight=$maxHeight")

        // 1. Define the "Permissive Audio" list
        // We list commonly software-decodable audio codecs to reduce unnecessary transcoding.
        // This tells the server: "Don't transcode just because the audio is FLAC or OPUS."
        val permissiveAudioCodecs = "aac,mp3,ac3,eac3,flac,vorbis,opus,pcm,alac"

        // 2. Define the "Subtitle Fix"
        // "External" means: "Send me the subtitle file separately. I will render it."
        // Without this, the server burns the text into the video (Transcoding).
        val mySubtitleProfiles = listOf(
            SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ass", method = SubtitleDeliveryMethod.EXTERNAL), // ExoPlayer handles basic ASS/SSA
            SubtitleProfile(format = "ssa", method = SubtitleDeliveryMethod.EXTERNAL),
        )

        // 3. Enhanced direct play profiles with better codec support
        val myDirectPlayProfiles = listOf(
            DirectPlayProfile(
                container = "mkv",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,h265,hevc,vp9,av1", // Include modern codecs
                audioCodec = permissiveAudioCodecs,
            ),
            DirectPlayProfile(
                container = "mp4,m4v",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,h265,hevc,vp9,av1", // Include modern codecs
                audioCodec = permissiveAudioCodecs,
            ),
            DirectPlayProfile(
                container = "webm",
                type = DlnaProfileType.VIDEO,
                videoCodec = "vp8,vp9,av1",
                audioCodec = permissiveAudioCodecs,
            ),
            DirectPlayProfile(
                container = "avi",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,xvid,divx",
                audioCodec = permissiveAudioCodecs,
            ),
            DirectPlayProfile(
                container = "mov",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264",
                audioCodec = permissiveAudioCodecs,
            ),

            // Audio-only containers
            DirectPlayProfile(
                container = "flac",
                type = DlnaProfileType.AUDIO,
                audioCodec = "flac",
            ),
            DirectPlayProfile(
                container = "mp3",
                type = DlnaProfileType.AUDIO,
                audioCodec = "mp3",
            ),
            DirectPlayProfile(
                container = "ogg",
                type = DlnaProfileType.AUDIO,
                audioCodec = "vorbis,opus",
            ),
            DirectPlayProfile(
                container = "aac",
                type = DlnaProfileType.AUDIO,
                audioCodec = "aac",
            ),
            DirectPlayProfile(
                container = "m4a",
                type = DlnaProfileType.AUDIO,
                audioCodec = "aac",
            ),
        )

        // Adaptive bitrate based on device capabilities
        val maxBitrate = when {
            maxWidth >= 3840 && maxHeight >= 2160 -> 400_000_000 // 400 Mbps for 4K devices
            maxWidth >= 1920 && maxHeight >= 1080 -> 200_000_000 // 200 Mbps for 1080p devices
            else -> 100_000_000 // 100 Mbps for lower resolution devices
        }

        return DeviceProfile(
            name = "Jellyfin Android Client",
            maxStreamingBitrate = maxBitrate,
            maxStaticBitrate = maxBitrate,
            musicStreamingTranscodingBitrate = 192_000, // 192 kbps for music transcoding

            // 4. Use our enhanced direct play profiles with permissive audio
            directPlayProfiles = myDirectPlayProfiles,

            // Transcoding profiles for fallback when direct play isn't possible
            transcodingProfiles = listOf(
                // H.265/HEVC transcoding profile (preferred for better quality/compression)
                TranscodingProfile(
                    container = "mp4",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h265,hevc",
                    audioCodec = "opus,aac",
                    context = EncodingContext.STREAMING,
                    protocol = MediaStreamProtocol.HTTP,
                    enableMpegtsM2TsMode = false,
                    minSegments = 2,
                    segmentLength = 6,
                    conditions = listOf(
                        // Explicit max resolution for transcoding output to prevent 416p defaults
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "$maxHeight",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "$maxWidth",
                            isRequired = false,
                        ),
                    ),
                ),
                // H.264 transcoding profile (fallback for compatibility)
                TranscodingProfile(
                    container = "mp4",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264",
                    audioCodec = "aac,opus",
                    context = EncodingContext.STREAMING,
                    protocol = MediaStreamProtocol.HLS,
                    enableMpegtsM2TsMode = false,
                    minSegments = 2,
                    segmentLength = 6,
                    conditions = listOf(
                        // Explicit max resolution for transcoding output to prevent 416p defaults
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "$maxHeight",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "$maxWidth",
                            isRequired = false,
                        ),
                    ),
                ),
                TranscodingProfile(
                    container = "mp3",
                    type = DlnaProfileType.AUDIO,
                    videoCodec = "",
                    audioCodec = "mp3",
                    context = EncodingContext.STREAMING,
                    protocol = MediaStreamProtocol.HTTP,
                    conditions = emptyList(),
                ),
            ),

            // Container profiles define which containers are supported
            containerProfiles = listOf(
                ContainerProfile(
                    type = DlnaProfileType.VIDEO,
                    container = "mkv,mp4,m4v,webm,avi,mov",
                    conditions = emptyList(),
                ),
                ContainerProfile(
                    type = DlnaProfileType.AUDIO,
                    container = "flac,mp3,ogg,aac,m4a",
                    conditions = emptyList(),
                ),
            ),

            // Enhanced codec profiles with dynamic resolution limits
            codecProfiles = listOf(
                // H.264 profile - report device can decode up to device max resolution
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264",
                    applyConditions = emptyList(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "$maxHeight",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "$maxWidth",
                            isRequired = false,
                        ),
                    ),
                ),
                // H.265/HEVC profile - report device can decode up to device max resolution
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h265,hevc",
                    applyConditions = emptyList(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "$maxHeight",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "$maxWidth",
                            isRequired = false,
                        ),
                    ),
                ),
                // VP9 profile - report device can decode up to device max resolution
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "vp9",
                    applyConditions = emptyList(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "$maxHeight",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "$maxWidth",
                            isRequired = false,
                        ),
                    ),
                ),
                // AV1 profile - report device can decode up to device max resolution
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "av1",
                    applyConditions = emptyList(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "$maxHeight",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.GREATER_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "1",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "$maxWidth",
                            isRequired = false,
                        ),
                    ),
                ),
            ),

            // 5. Use our external-only subtitle profiles to prevent burning
            subtitleProfiles = mySubtitleProfiles,
        )
    }
}
