package com.rpeters.jellyfin.data.model

import android.util.Log
import org.jellyfin.sdk.model.api.*

/**
 * Creates a device profile optimized for direct play with MKV/H.264/Vorbis support.
 * This profile enables true direct play by advertising support for common codecs
 * including Vorbis audio which requires the FFmpeg decoder extension.
 */
object JellyfinDeviceProfile {

    fun createAndroidDeviceProfile(): DeviceProfile {
        return createAndroidDeviceProfile(maxWidth = 1920, maxHeight = 1080)
    }

    fun createAndroidDeviceProfile(maxWidth: Int = 1920, maxHeight: Int = 1080): DeviceProfile {
        Log.d("JellyfinDeviceProfile", "Creating device profile with maxWidth=$maxWidth, maxHeight=$maxHeight")
        
        // 1. Define the "Permissive Audio" list
        // We list almost everything because ExoPlayer (software) can handle these easily.
        // This tells the server: "Don't transcode just because the audio is FLAC or OPUS."
        val permissiveAudioCodecs = "aac,mp3,ac3,eac3,flac,vorbis,opus,pcm,alac,dtshd,dts,truehd"
        
        // 2. Define the "Subtitle Fix"
        // "External" means: "Send me the subtitle file separately. I will render it."
        // Without this, the server burns the text into the video (Transcoding).
        val mySubtitleProfiles = listOf(
            SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ass", method = SubtitleDeliveryMethod.EXTERNAL), // ExoPlayer handles basic ASS/SSA
            SubtitleProfile(format = "ssa", method = SubtitleDeliveryMethod.EXTERNAL)
        )
        
        // 3. Combine with our existing smart Video detection
        // We take the detected video capabilities, but OVERWRITE the audio
        // to be our permissive list.
        val myDirectPlayProfiles = listOf(
            DirectPlayProfile(
                container = "mkv",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,h265,hevc",
                audioCodec = permissiveAudioCodecs, // <--- The magic fix
            ),
            DirectPlayProfile(
                container = "mp4,m4v",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,h265,hevc",
                audioCodec = permissiveAudioCodecs, // <--- The magic fix
            ),
            DirectPlayProfile(
                container = "webm",
                type = DlnaProfileType.VIDEO,
                videoCodec = "vp8,vp9,av1",
                audioCodec = permissiveAudioCodecs, // <--- The magic fix
            ),
            DirectPlayProfile(
                container = "avi",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,xvid,divx",
                audioCodec = permissiveAudioCodecs, // <--- The magic fix
            ),
            DirectPlayProfile(
                container = "mov",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264",
                audioCodec = permissiveAudioCodecs, // <--- The magic fix
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
        
        return DeviceProfile(
            name = "Jellyfin Android Client",
            maxStreamingBitrate = 400_000_000, // 400 Mbps for high-quality direct play
            maxStaticBitrate = 400_000_000,
            musicStreamingTranscodingBitrate = 192_000, // 192 kbps for music transcoding

            // 4. Use our enhanced direct play profiles with permissive audio
            directPlayProfiles = myDirectPlayProfiles,

            // Transcoding profiles for fallback when direct play isn't possible
            // NOTE: Conditions removed to let URL parameters (MaxWidth/MaxHeight) control output resolution.
            // ProfileConditions define when to USE a profile, not output capabilities.
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
                    conditions = emptyList(), // Let URL parameters control resolution
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
                    conditions = emptyList(), // Let URL parameters control resolution
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

            // Codec profiles to report device decoding capabilities
            // These profiles tell the server what the device can decode, not what to transcode to
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
                            value = "$maxHeight", // Use device's max height
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
                            value = "$maxWidth", // Use device's max width
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
                            value = "$maxHeight", // Use device's max height
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
                            value = "$maxWidth", // Use device's max width
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
                            value = "$maxHeight", // Use device's max height
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
                            value = "$maxWidth", // Use device's max width
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
