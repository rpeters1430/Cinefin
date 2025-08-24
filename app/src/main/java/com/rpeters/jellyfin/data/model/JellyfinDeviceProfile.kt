package com.rpeters.jellyfin.data.model

import org.jellyfin.sdk.model.api.*

/**
 * Creates a device profile optimized for direct play with MKV/H.264/Vorbis support.
 * This profile enables true direct play by advertising support for common codecs
 * including Vorbis audio which requires the FFmpeg decoder extension.
 */
object JellyfinDeviceProfile {
    
    fun createAndroidDeviceProfile(): DeviceProfile {
        return DeviceProfile(
            name = "Jellyfin Android Client",
            maxStreamingBitrate = 400_000_000, // 400 Mbps for high-quality direct play
            maxStaticBitrate = 400_000_000,
            musicStreamingTranscodingBitrate = 192_000, // 192 kbps for music transcoding
            
            // Enable direct play for supported containers and codecs
            directPlayProfiles = listOf(
                // Video containers with H.264 and various audio codecs including Vorbis
                DirectPlayProfile(
                    container = "mkv",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264,h265,hevc",
                    audioCodec = "vorbis,aac,ac3,eac3,mp3,opus,flac,pcm"
                ),
                DirectPlayProfile(
                    container = "mp4,m4v",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264,h265,hevc",
                    audioCodec = "aac,ac3,eac3,mp3,opus,flac,pcm"
                ),
                DirectPlayProfile(
                    container = "webm",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "vp8,vp9,av1",
                    audioCodec = "vorbis,opus"
                ),
                DirectPlayProfile(
                    container = "avi",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264,xvid,divx",
                    audioCodec = "aac,mp3,ac3,pcm"
                ),
                
                // Audio-only containers
                DirectPlayProfile(
                    container = "flac",
                    type = DlnaProfileType.AUDIO,
                    audioCodec = "flac"
                ),
                DirectPlayProfile(
                    container = "mp3",
                    type = DlnaProfileType.AUDIO,
                    audioCodec = "mp3"
                ),
                DirectPlayProfile(
                    container = "ogg",
                    type = DlnaProfileType.AUDIO,
                    audioCodec = "vorbis,opus"
                ),
                DirectPlayProfile(
                    container = "aac",
                    type = DlnaProfileType.AUDIO,
                    audioCodec = "aac"
                ),
                DirectPlayProfile(
                    container = "m4a",
                    type = DlnaProfileType.AUDIO,
                    audioCodec = "aac"
                )
            ),
            
            // Transcoding profiles for fallback when direct play isn't possible
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "mp4",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264",
                    audioCodec = "aac",
                    context = EncodingContext.STREAMING,
                    protocol = MediaStreamProtocol.HLS,
                    enableMpegtsM2TsMode = false,
                    minSegments = 2,
                    segmentLength = 6,
                    conditions = emptyList()
                ),
                TranscodingProfile(
                    container = "mp3",
                    type = DlnaProfileType.AUDIO,
                    videoCodec = "",
                    audioCodec = "mp3",
                    context = EncodingContext.STREAMING,
                    protocol = MediaStreamProtocol.HTTP,
                    conditions = emptyList()
                )
            ),
            
            // Container profiles define which containers are supported
            containerProfiles = listOf(
                ContainerProfile(
                    type = DlnaProfileType.VIDEO,
                    container = "mkv,mp4,m4v,webm,avi,mov",
                    conditions = emptyList()
                ),
                ContainerProfile(
                    type = DlnaProfileType.AUDIO,
                    container = "flac,mp3,ogg,aac,m4a",
                    conditions = emptyList()
                )
            ),
            
            // Codec profiles can specify additional constraints
            codecProfiles = listOf(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264",
                    applyConditions = emptyList(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.VIDEO_LEVEL,
                            value = "52", // Support up to H.264 Level 5.2
                            isRequired = false
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "4096", // Support up to 4K width
                            isRequired = false
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "2304", // Support up to 4K height
                            isRequired = false
                        )
                    )
                ),
                CodecProfile(
                    type = CodecType.AUDIO,
                    codec = "vorbis",
                    applyConditions = emptyList(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.AUDIO_CHANNELS,
                            value = "8", // Support up to 7.1 surround
                            isRequired = false
                        )
                    )
                )
            ),
            
            // Subtitle profiles
            subtitleProfiles = listOf(
                SubtitleProfile(
                    format = "srt",
                    method = SubtitleDeliveryMethod.EXTERNAL
                ),
                SubtitleProfile(
                    format = "ass",
                    method = SubtitleDeliveryMethod.EXTERNAL
                ),
                SubtitleProfile(
                    format = "ssa",
                    method = SubtitleDeliveryMethod.EXTERNAL
                ),
                SubtitleProfile(
                    format = "vtt",
                    method = SubtitleDeliveryMethod.EXTERNAL
                )
            )
        )
    }
}