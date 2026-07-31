package com.rpeters.jellyfin.ui.components.immersive

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaInfoBadgesTest {

    @Test
    fun `fromCodecName maps known codec names case-insensitively`() {
        assertEquals(VideoCodecType.HEVC, VideoCodecType.fromCodecName("HEVC"))
        assertEquals(VideoCodecType.HEVC, VideoCodecType.fromCodecName("hevc"))
        assertEquals(VideoCodecType.AVC, VideoCodecType.fromCodecName("AVC"))
        assertEquals(VideoCodecType.AV1, VideoCodecType.fromCodecName("AV1"))
        assertEquals(VideoCodecType.VP9, VideoCodecType.fromCodecName("VP9"))
    }

    @Test
    fun `fromCodecName falls back to OTHER for unknown codecs`() {
        assertEquals(VideoCodecType.OTHER, VideoCodecType.fromCodecName("MPEG2"))
        assertEquals(VideoCodecType.OTHER, VideoCodecType.fromCodecName(""))
    }
}
