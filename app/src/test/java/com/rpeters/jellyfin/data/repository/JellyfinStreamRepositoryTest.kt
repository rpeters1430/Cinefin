package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive test suite for JellyfinStreamRepository.
 * Tests streaming URL generation, image URL generation, and error handling.
 */
class JellyfinStreamRepositoryTest {

    @MockK
    private lateinit var authRepository: JellyfinAuthRepository

    private lateinit var streamRepository: JellyfinStreamRepository

    private val testServer = JellyfinServer(
        url = "https://demo.jellyfin.org",
        userId = "test-user-id",
        accessToken = "test-access-token",
        serverName = "Test Server",
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        streamRepository = JellyfinStreamRepository(authRepository)
    }

    @Test
    fun `getStreamUrl returns correct URL for valid item`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getStreamUrl(itemId)

        // Then
        assertNotNull("Stream URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId))
        assertTrue("URL should contain access token", result.contains(testServer.accessToken!!))
        assertTrue("URL should be static stream", result.contains("static=true"))
    }

    @Test
    fun `getStreamUrl returns null when no server available`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns null

        // When
        val result = streamRepository.getStreamUrl(itemId)

        // Then
        assertNull("Stream URL should be null when no server", result)
    }

    @Test
    fun `getStreamUrl returns null when no access token`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        val serverWithoutToken = testServer.copy(accessToken = null)
        every { authRepository.getCurrentServer() } returns serverWithoutToken

        // When
        val result = streamRepository.getStreamUrl(itemId)

        // Then
        assertNull("Stream URL should be null when no access token", result)
    }

    @Test
    fun `getStreamUrl returns null for blank item ID`() {
        // Given
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getStreamUrl("")

        // Then
        assertNull("Stream URL should be null for blank item ID", result)
    }

    @Test
    fun `getStreamUrl returns null for invalid UUID format`() {
        // Given
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getStreamUrl("invalid-uuid")

        // Then
        assertNull("Stream URL should be null for invalid UUID", result)
    }

    @Test
    fun `getTranscodedStreamUrl returns correct HLS URL with parameters`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = 8000000,
            maxWidth = 1920,
            maxHeight = 1080,
            videoCodec = "h264",
            audioCodec = "aac",
            container = "mp4",
        )

        // Then
        assertNotNull("Transcoded URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId))
        assertTrue("URL should be HLS stream", result.contains("master.m3u8"))
        assertTrue("URL should contain maxBitrate", result.contains("MaxStreamingBitrate=8000000"))
        assertTrue("URL should contain maxWidth", result.contains("MaxWidth=1920"))
        assertTrue("URL should contain maxHeight", result.contains("MaxHeight=1080"))
        assertTrue("URL should contain video codec", result.contains("VideoCodec=h264"))
        assertTrue("URL should contain audio codec", result.contains("AudioCodec=aac"))
        assertTrue("URL should contain container", result.contains("Container=mp4"))
    }

    @Test
    fun `getHlsStreamUrl returns correct adaptive streaming URL`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getHlsStreamUrl(itemId)

        // Then
        assertNotNull("HLS URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId))
        assertTrue("URL should be HLS stream", result.contains("master.m3u8"))
        assertTrue("URL should contain PlaySessionId", result.contains("PlaySessionId="))
        assertTrue("URL should contain default bitrate", result.contains("MaxStreamingBitrate=140000000"))
    }

    @Test
    fun `getDashStreamUrl returns correct DASH URL`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getDashStreamUrl(itemId)

        // Then
        assertNotNull("DASH URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId))
        assertTrue("URL should be DASH stream", result.contains("stream.mpd"))
        assertTrue("URL should contain PlaySessionId", result.contains("PlaySessionId="))
    }

    @Test
    fun `getDownloadUrl returns correct download URL`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getDownloadUrl(itemId)

        // Then
        assertNotNull("Download URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId))
        assertTrue("URL should be download endpoint", result.contains("/Download"))
        assertTrue("URL should contain access token", result.contains("api_key=${testServer.accessToken}"))
    }

    @Test
    fun `getDirectStreamUrl returns correct direct stream URL`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getDirectStreamUrl(itemId, "mkv")

        // Then
        assertNotNull("Direct stream URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId))
        assertTrue("URL should contain container", result.contains("stream.mkv"))
        assertTrue("URL should be static", result.contains("static=true"))
        assertTrue("URL should contain Container param", result.contains("Container=mkv"))
    }

    @Test
    fun `getDirectStreamUrl uses default mp4 container when none specified`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getDirectStreamUrl(itemId)

        // Then
        assertNotNull("Direct stream URL should not be null", result)
        assertTrue("URL should contain default mp4 container", result!!.contains("stream.mp4"))
    }

    @Test
    fun `getImageUrl returns correct image URL`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getImageUrl(itemId, "Primary", "test-tag")

        // Then
        assertNotNull("Image URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId))
        assertTrue("URL should contain image type", result.contains("/Images/Primary"))
        assertTrue("URL should contain dimensions", result.contains("maxHeight=400"))
        assertTrue("URL should contain dimensions", result.contains("maxWidth=400"))
        assertTrue("URL should contain tag", result.contains("tag=test-tag"))
    }

    @Test
    fun `getSeriesImageUrl returns episode series poster URL`() {
        // Given
        val seriesId = UUID.randomUUID()
        val episodeItem = mockk<BaseItemDto> {
            every { type } returns BaseItemKind.EPISODE
            every { seriesId } returns seriesId
            every { id } returns UUID.randomUUID()
        }
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getSeriesImageUrl(episodeItem)

        // Then
        assertNotNull("Series image URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain series ID", result.contains(seriesId.toString()))
        assertTrue("URL should be Primary image", result.contains("/Images/Primary"))
    }

    @Test
    fun `getSeriesImageUrl returns item image URL for non-episode`() {
        // Given
        val itemId = UUID.randomUUID()
        val movieItem = mockk<BaseItemDto> {
            every { type } returns BaseItemKind.MOVIE
            every { id } returns itemId
        }
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getSeriesImageUrl(movieItem)

        // Then
        assertNotNull("Series image URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId.toString()))
        assertTrue("URL should be Primary image", result.contains("/Images/Primary"))
    }

    @Test
    fun `getBackdropUrl returns backdrop URL when backdrop tag available`() {
        // Given
        val itemId = UUID.randomUUID()
        val backdropTag = "backdrop-tag-123"
        val item = mockk<BaseItemDto> {
            every { id } returns itemId
            every { backdropImageTags } returns listOf(backdropTag)
        }
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getBackdropUrl(item)

        // Then
        assertNotNull("Backdrop URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId.toString()))
        assertTrue("URL should be Backdrop image", result.contains("/Images/Backdrop"))
        assertTrue("URL should contain backdrop tag", result.contains("tag=$backdropTag"))
        assertTrue("URL should contain backdrop dimensions", result.contains("maxHeight=400"))
        assertTrue("URL should contain backdrop dimensions", result.contains("maxWidth=800"))
    }

    @Test
    fun `getBackdropUrl falls back to primary image when no backdrop available`() {
        // Given
        val itemId = UUID.randomUUID()
        val primaryTag = "primary-tag-123"
        val item = mockk<BaseItemDto> {
            every { id } returns itemId
            every { backdropImageTags } returns null
            every { imageTags } returns mapOf(org.jellyfin.sdk.model.api.ImageType.PRIMARY to primaryTag)
        }
        every { authRepository.getCurrentServer() } returns testServer

        // When
        val result = streamRepository.getBackdropUrl(item)

        // Then
        assertNotNull("Backdrop URL should not be null", result)
        assertTrue("URL should contain server URL", result!!.contains(testServer.url))
        assertTrue("URL should contain item ID", result.contains(itemId.toString()))
        assertTrue("URL should be Primary image", result.contains("/Images/Primary"))
        assertTrue("URL should contain primary tag", result.contains("tag=$primaryTag"))
    }

    @Test
    fun `stream repository methods return null when no server available`() {
        // Given
        val itemId = UUID.randomUUID().toString()
        every { authRepository.getCurrentServer() } returns null

        // When & Then
        assertNull("Stream URL should be null", streamRepository.getStreamUrl(itemId))
        assertNull("Transcoded URL should be null", streamRepository.getTranscodedStreamUrl(itemId))
        assertNull("HLS URL should be null", streamRepository.getHlsStreamUrl(itemId))
        assertNull("DASH URL should be null", streamRepository.getDashStreamUrl(itemId))
        assertNull("Download URL should be null", streamRepository.getDownloadUrl(itemId))
        assertNull("Direct stream URL should be null", streamRepository.getDirectStreamUrl(itemId))
        assertNull("Image URL should be null", streamRepository.getImageUrl(itemId))

        val mockItem = mockk<BaseItemDto> {
            every { id } returns UUID.randomUUID()
        }
        assertNull("Series image URL should be null", streamRepository.getSeriesImageUrl(mockItem))
        assertNull("Backdrop URL should be null", streamRepository.getBackdropUrl(mockItem))
    }
}
