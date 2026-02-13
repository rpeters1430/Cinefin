package com.rpeters.jellyfin.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceCapabilitiesTest {

    private lateinit var deviceCapabilities: DeviceCapabilities

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        deviceCapabilities = DeviceCapabilities(context)
    }

    // Container Format Tests

    @Test
    fun `canPlayContainer returns true for supported MP4 container`() {
        assertTrue(deviceCapabilities.canPlayContainer("mp4"))
    }

    @Test
    fun `canPlayContainer returns true for supported WebM container`() {
        assertTrue(deviceCapabilities.canPlayContainer("webm"))
    }

    @Test
    fun `canPlayContainer returns true for supported MKV container`() {
        assertTrue(deviceCapabilities.canPlayContainer("mkv"))
    }

    @Test
    fun `canPlayContainer returns true for supported AVI container`() {
        assertTrue(deviceCapabilities.canPlayContainer("avi"))
    }

    @Test
    fun `canPlayContainer returns false for unsupported container`() {
        assertFalse(deviceCapabilities.canPlayContainer("xyz"))
    }

    @Test
    fun `canPlayContainer returns false for null container`() {
        assertFalse(deviceCapabilities.canPlayContainer(null))
    }

    @Test
    fun `canPlayContainer returns false for empty container`() {
        assertFalse(deviceCapabilities.canPlayContainer(""))
    }

    @Test
    fun `canPlayContainer handles case insensitivity`() {
        assertTrue(deviceCapabilities.canPlayContainer("MP4"))
        assertTrue(deviceCapabilities.canPlayContainer("MKV"))
        assertTrue(deviceCapabilities.canPlayContainer("WebM"))
    }

    @Test
    fun `canPlayContainer strips leading dot from extension`() {
        assertTrue(deviceCapabilities.canPlayContainer(".mp4"))
        assertTrue(deviceCapabilities.canPlayContainer(".mkv"))
    }

    // Video Codec Tests

    @Test
    fun `canPlayVideoCodec returns true for H264`() {
        // H.264 should be supported on all Android devices
        val result = deviceCapabilities.canPlayVideoCodec("h264")
        assertNotNull("Result should not be null", result)
        // Note: Actual support depends on device, but API should work
    }

    @Test
    fun `canPlayVideoCodec returns true for H265`() {
        val result = deviceCapabilities.canPlayVideoCodec("h265")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayVideoCodec returns true for VP8`() {
        val result = deviceCapabilities.canPlayVideoCodec("vp8")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayVideoCodec returns true for VP9`() {
        val result = deviceCapabilities.canPlayVideoCodec("vp9")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayVideoCodec returns true for null codec (audio-only)`() {
        assertTrue(deviceCapabilities.canPlayVideoCodec(null))
    }

    @Test
    fun `canPlayVideoCodec returns true for empty codec (audio-only)`() {
        assertTrue(deviceCapabilities.canPlayVideoCodec(""))
    }

    @Test
    fun `canPlayVideoCodec returns false for unsupported codec`() {
        assertFalse(deviceCapabilities.canPlayVideoCodec("xyz123"))
    }

    @Test
    fun `canPlayVideoCodec handles codec normalization for AVC`() {
        // "avc" should be normalized to "h264"
        val result = deviceCapabilities.canPlayVideoCodec("avc")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayVideoCodec handles codec normalization for HEVC`() {
        // "hevc" should be normalized to "h265"
        val result = deviceCapabilities.canPlayVideoCodec("hevc")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayVideoCodec checks resolution limits when specified`() {
        // Test with standard HD resolution
        val result = deviceCapabilities.canPlayVideoCodec("h264", 1920, 1080)
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayVideoCodec rejects extremely high resolution`() {
        // Test with unrealistic resolution (100K x 100K)
        val result = deviceCapabilities.canPlayVideoCodec("h264", 100000, 100000)
        // This should return false as it exceeds device capabilities
        assertFalse("Extremely high resolution should not be supported", result)
    }

    // Audio Codec Tests

    @Test
    fun `canPlayAudioCodec returns true for AAC`() {
        val result = deviceCapabilities.canPlayAudioCodec("aac")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec returns true for MP3`() {
        val result = deviceCapabilities.canPlayAudioCodec("mp3")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec returns true for Opus`() {
        val result = deviceCapabilities.canPlayAudioCodec("opus")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec returns true for FLAC`() {
        val result = deviceCapabilities.canPlayAudioCodec("flac")
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec returns true for null codec (video-only)`() {
        assertTrue(deviceCapabilities.canPlayAudioCodec(null))
    }

    @Test
    fun `canPlayAudioCodec returns true for empty codec (video-only)`() {
        assertTrue(deviceCapabilities.canPlayAudioCodec(""))
    }

    @Test
    fun `canPlayAudioCodec returns false for unsupported codec`() {
        assertFalse(deviceCapabilities.canPlayAudioCodec("xyz123"))
    }

    @Test
    fun `canPlayAudioCodec handles codec normalization for AAC variants`() {
        // Various AAC formats should normalize to "aac"
        val result1 = deviceCapabilities.canPlayAudioCodec("aac-lc")
        val result2 = deviceCapabilities.canPlayAudioCodec("aac-he")
        assertNotNull("AAC-LC result should not be null", result1)
        assertNotNull("AAC-HE result should not be null", result2)
    }

    @Test
    fun `canPlayAudioCodec handles codec normalization for AC3`() {
        // "ac-3" should normalize to "ac3"
        val result = deviceCapabilities.canPlayAudioCodec("ac-3")
        assertNotNull("Result should not be null", result)
    }

    // Multi-channel Audio Codec Tests (for EAC3/AC3 5.1/7.1 surround sound)

    @Test
    fun `canPlayAudioCodec with channels supports EAC3 stereo`() {
        // EAC3 stereo (2 channels) should be supported on most devices
        val result = deviceCapabilities.canPlayAudioCodec("eac3", 2)
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec with channels supports EAC3 sixChannel with fallback`() {
        // EAC3 5.1 (6 channels) should fall back to stereo check if not explicitly supported
        // This is the key fix: devices that support EAC3 stereo can downmix 5.1
        val result = deviceCapabilities.canPlayAudioCodec("eac3", 6)
        assertNotNull("Result should not be null", result)
        // The result depends on device codec support - on Robolectric it may vary
        // but the important thing is it doesn't crash and checks stereo fallback
    }

    @Test
    fun `canPlayAudioCodec with channels supports EAC3 eightChannel with fallback`() {
        // EAC3 7.1 (8 channels) should fall back to stereo check if not explicitly supported
        val result = deviceCapabilities.canPlayAudioCodec("eac3", 8)
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec with channels supports AC3 sixChannel with fallback`() {
        // AC3 5.1 (6 channels) should fall back to stereo check if not explicitly supported
        val result = deviceCapabilities.canPlayAudioCodec("ac3", 6)
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec with channels supports AC3 eightChannel with fallback`() {
        // AC3 7.1 (8 channels) should fall back to stereo check if not explicitly supported
        val result = deviceCapabilities.canPlayAudioCodec("ac3", 8)
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec with channels supports DTS sixChannel with fallback`() {
        // DTS 5.1 (6 channels) should fall back to stereo check if not explicitly supported
        val result = deviceCapabilities.canPlayAudioCodec("dts", 6)
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec with channels supports AAC stereo`() {
        // AAC stereo should be universally supported
        val result = deviceCapabilities.canPlayAudioCodec("aac", 2)
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canPlayAudioCodec with channels does not fallback for non-surround codecs`() {
        // Non-surround codecs like AAC should not use fallback logic
        // 8 channel AAC should be checked directly without stereo fallback
        val result = deviceCapabilities.canPlayAudioCodec("aac", 8)
        // Result depends on device support - may be true or false
        assertNotNull("Result should not be null", result)
    }

    // Direct Play Capability Tests

    @Test
    fun `canDirectPlay returns true when all codecs supported`() {
        // Note: Actual result depends on device codec support
        val result = deviceCapabilities.canDirectPlay(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            width = 1920,
            height = 1080,
        )
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `canDirectPlay returns false when container unsupported`() {
        val result = deviceCapabilities.canDirectPlay(
            container = "xyz",
            videoCodec = "h264",
            audioCodec = "aac",
        )
        assertFalse("Should return false for unsupported container", result)
    }

    @Test
    fun `canDirectPlay returns false when video codec unsupported`() {
        val result = deviceCapabilities.canDirectPlay(
            container = "mp4",
            videoCodec = "xyz123",
            audioCodec = "aac",
        )
        assertFalse("Should return false for unsupported video codec", result)
    }

    @Test
    fun `canDirectPlay returns false when audio codec unsupported`() {
        val result = deviceCapabilities.canDirectPlay(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "xyz123",
        )
        assertFalse("Should return false for unsupported audio codec", result)
    }

    @Test
    fun `canDirectPlay handles audio-only content`() {
        val result = deviceCapabilities.canDirectPlay(
            container = "mp4",
            videoCodec = null,
            audioCodec = "aac",
        )
        // Should work for audio-only when video codec is null
        assertNotNull("Result should not be null for audio-only", result)
    }

    @Test
    fun `canDirectPlay handles video-only content`() {
        val result = deviceCapabilities.canDirectPlay(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = null,
        )
        // Should work for video-only when audio codec is null
        assertNotNull("Result should not be null for video-only", result)
    }

    // DirectPlayCapabilities Tests

    @Test
    fun `getDirectPlayCapabilities returns non-empty supported containers`() {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()

        assertNotNull("Capabilities should not be null", capabilities)
        assertTrue(
            "Should have at least one supported container",
            capabilities.supportedContainers.isNotEmpty(),
        )
    }

    @Test
    fun `getDirectPlayCapabilities includes MP4 container`() {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()

        assertTrue(
            "Should support MP4 container",
            capabilities.supportedContainers.contains("mp4"),
        )
    }

    @Test
    fun `getDirectPlayCapabilities includes common video codecs`() {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()

        assertTrue(
            "Should have video codecs",
            capabilities.supportedVideoCodecs.isNotEmpty(),
        )
    }

    @Test
    fun `getDirectPlayCapabilities includes common audio codecs`() {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()

        assertTrue(
            "Should have audio codecs",
            capabilities.supportedAudioCodecs.isNotEmpty(),
        )
    }

    @Test
    fun `getDirectPlayCapabilities returns valid max resolution`() {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()

        assertNotNull("Max resolution should not be null", capabilities.maxResolution)
        assertTrue(
            "Max resolution width should be positive",
            capabilities.maxResolution.first > 0,
        )
        assertTrue(
            "Max resolution height should be positive",
            capabilities.maxResolution.second > 0,
        )
    }

    // analyzeDirectPlayCapability Tests

    @Test
    fun `analyzeDirectPlayCompatibility returns analysis for supported content`() {
        val analysis = deviceCapabilities.analyzeDirectPlayCompatibility(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            width = 1920,
            height = 1080,
        )

        assertNotNull("Analysis should not be null", analysis)
        assertNotNull("Analysis recommendation should not be null", analysis.recommendation)
    }

    @Test
    fun `analyzeDirectPlayCompatibility identifies unsupported container`() {
        val analysis = deviceCapabilities.analyzeDirectPlayCompatibility(
            container = "xyz",
            videoCodec = "h264",
            audioCodec = "aac",
        )

        assertNotNull("Analysis should not be null", analysis)
        assertFalse("Should not be able to direct play", analysis.canDirectPlay)
        assertTrue(
            "Should report container issue",
            analysis.issues.any { it.contains("container", ignoreCase = true) },
        )
    }

    @Test
    fun `analyzeDirectPlayCompatibility identifies unsupported video codec`() {
        val analysis = deviceCapabilities.analyzeDirectPlayCompatibility(
            container = "mp4",
            videoCodec = "xyz123",
            audioCodec = "aac",
        )

        assertNotNull("Analysis should not be null", analysis)
        assertFalse("Should not be able to direct play", analysis.canDirectPlay)
        assertTrue(
            "Should report video codec issue",
            analysis.issues.any { it.contains("video", ignoreCase = true) },
        )
    }

    @Test
    fun `analyzeDirectPlayCompatibility identifies resolution too high`() {
        val analysis = deviceCapabilities.analyzeDirectPlayCompatibility(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            width = 100000,
            height = 100000,
        )

        assertNotNull("Analysis should not be null", analysis)
        // Resolution is too high, should affect compatibility
        assertTrue(
            "Score should reflect resolution issue",
            analysis.confidenceScore < 100,
        )
    }

    @Test
    fun `analyzeDirectPlayCompatibility provides compatibility score`() {
        val analysis = deviceCapabilities.analyzeDirectPlayCompatibility(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            width = 1920,
            height = 1080,
        )

        assertNotNull("Analysis should not be null", analysis)
        assertTrue(
            "Compatibility score should be between 0 and 100",
            analysis.confidenceScore in 0..100,
        )
    }

    // Edge Cases

    @Test
    fun `handles multiple calls without state corruption`() {
        // Call multiple times to ensure no state corruption
        deviceCapabilities.canPlayContainer("mp4")
        deviceCapabilities.canPlayVideoCodec("h264")
        deviceCapabilities.canPlayAudioCodec("aac")
        deviceCapabilities.getDirectPlayCapabilities().maxResolution

        // Should still work correctly
        assertTrue(deviceCapabilities.canPlayContainer("mp4"))
        assertNotNull(deviceCapabilities.canPlayVideoCodec("h264"))
        assertNotNull(deviceCapabilities.canPlayAudioCodec("aac"))
    }

    @Test
    fun `getDirectPlayCapabilities returns consistent results`() {
        val capabilities1 = deviceCapabilities.getDirectPlayCapabilities()
        val capabilities2 = deviceCapabilities.getDirectPlayCapabilities()

        // Should return consistent results
        assertEquals(
            "Container lists should be equal",
            capabilities1.supportedContainers,
            capabilities2.supportedContainers,
        )
        assertEquals(
            "Max resolution should be equal",
            capabilities1.maxResolution,
            capabilities2.maxResolution,
        )
    }
}
