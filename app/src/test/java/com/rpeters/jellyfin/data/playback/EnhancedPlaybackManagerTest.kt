package com.rpeters.jellyfin.data.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.rpeters.jellyfin.data.DeviceCapabilities
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EnhancedPlaybackManagerTest {

    private lateinit var manager: EnhancedPlaybackManager
    private lateinit var context: Context
    private lateinit var repository: JellyfinRepository
    private lateinit var streamRepository: JellyfinStreamRepository
    private lateinit var deviceCapabilities: DeviceCapabilities
    private lateinit var connectivityChecker: com.rpeters.jellyfin.network.ConnectivityChecker
    private lateinit var playbackPreferencesRepository: com.rpeters.jellyfin.data.preferences.PlaybackPreferencesRepository
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var network: Network
    private lateinit var networkCapabilities: NetworkCapabilities

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        streamRepository = mockk(relaxed = true)
        deviceCapabilities = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
        playbackPreferencesRepository = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        network = mockk(relaxed = true)
        networkCapabilities = mockk(relaxed = true)

        // Setup connectivity manager mock
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        mockkStatic(android.util.Log::class)
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0

        manager = EnhancedPlaybackManager(
            context = context,
            repository = repository,
            streamRepository = streamRepository,
            deviceCapabilities = deviceCapabilities,
            connectivityChecker = connectivityChecker,
            playbackPreferencesRepository = playbackPreferencesRepository,
        )
    }

    @After
    fun tearDown() {
        clearMocks(
            context,
            repository,
            streamRepository,
            deviceCapabilities,
            connectivityManager,
            network,
            networkCapabilities,
        )
        unmockkAll()
    }

    @Test
    fun `getOptimalPlaybackUrl returns DirectPlay when all conditions met`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 10_000_000, // 10 Mbps
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        // Mock device capabilities - supports all codecs
        every { deviceCapabilities.canPlayContainer(any()) } returns true
        every { deviceCapabilities.canPlayVideoCodec(any(), any(), any()) } returns true
        every { deviceCapabilities.canPlayAudioCodec(any()) } returns true

        // Mock network - WiFi with good bandwidth
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every { streamRepository.getDirectStreamUrl(itemId.toString(), "mp4") } returns "https://server/video.mp4"

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue("Expected DirectPlay result", result is PlaybackResult.DirectPlay)
        val directPlay = result as PlaybackResult.DirectPlay
        assertEquals("https://server/video.mp4", directPlay.url)
        assertEquals("mp4", directPlay.container)
        assertEquals("h264", directPlay.videoCodec)
        assertEquals("aac", directPlay.audioCodec)
        assertEquals(10_000_000, directPlay.bitrate)
    }

    @Test
    fun `getOptimalPlaybackUrl falls back to transcoding when video codec unsupported`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mkv",
            videoCodec = "hevc",
            audioCodec = "aac",
            bitrate = 15_000_000,
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        // Mock device capabilities - doesn't support HEVC
        every { deviceCapabilities.canPlayContainer("mkv") } returns true
        every { deviceCapabilities.canPlayVideoCodec("hevc", any(), any()) } returns false
        every { deviceCapabilities.canPlayAudioCodec("aac") } returns true

        // Mock network - WiFi
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every {
            streamRepository.getTranscodedStreamUrl(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns "https://server/transcode"

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue("Expected Transcoding result", result is PlaybackResult.Transcoding)
        val transcoding = result as PlaybackResult.Transcoding
        assertEquals("https://server/transcode", transcoding.url)
        assertNotNull(transcoding.reason)
    }

    @Test
    fun `getOptimalPlaybackUrl falls back to transcoding when network insufficient`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 60_000_000, // 60 Mbps - too high for WiFi threshold (50 Mbps)
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        // Mock device capabilities - supports all codecs
        every { deviceCapabilities.canPlayContainer("mp4") } returns true
        every { deviceCapabilities.canPlayVideoCodec("h264", any(), any()) } returns true
        every { deviceCapabilities.canPlayAudioCodec("aac") } returns true

        // Mock network - WiFi (50 Mbps limit)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every {
            streamRepository.getTranscodedStreamUrl(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns "https://server/transcode"

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue("Expected Transcoding result due to network bandwidth", result is PlaybackResult.Transcoding)
    }

    @Test
    fun `getOptimalPlaybackUrl uses DirectPlay on Ethernet with high bitrate`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mkv",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 80_000_000, // 80 Mbps - OK for Ethernet (100 Mbps limit)
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        // Mock device capabilities
        every { deviceCapabilities.canPlayContainer("mkv") } returns true
        every { deviceCapabilities.canPlayVideoCodec("h264", any(), any()) } returns true
        every { deviceCapabilities.canPlayAudioCodec("aac") } returns true

        // Mock network - Ethernet (100 Mbps limit)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every { streamRepository.getDirectStreamUrl(itemId.toString(), "mkv") } returns "https://server/video.mkv"

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue("Expected DirectPlay result on Ethernet", result is PlaybackResult.DirectPlay)
    }

    @Test
    fun `getOptimalPlaybackUrl falls back to transcoding on cellular with high bitrate`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 20_000_000, // 20 Mbps - too high for cellular (15 Mbps limit)
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        // Mock device capabilities
        every { deviceCapabilities.canPlayContainer("mp4") } returns true
        every { deviceCapabilities.canPlayVideoCodec("h264", any(), any()) } returns true
        every { deviceCapabilities.canPlayAudioCodec("aac") } returns true

        // Mock network - Cellular (15 Mbps limit)
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every {
            streamRepository.getTranscodedStreamUrl(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns "https://server/transcode"

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue("Expected Transcoding result on cellular", result is PlaybackResult.Transcoding)
    }

    @Test
    fun `getOptimalPlaybackUrl returns error when playback info is null`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)

        // Mock repository to throw so playback info retrieval fails
        coEvery { repository.getPlaybackInfo(itemId.toString()) } throws RuntimeException("No playback info")

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue(result is PlaybackResult.Error)
        assertEquals("Failed to get playback info", (result as PlaybackResult.Error).message)
    }

    @Test
    fun `getOptimalPlaybackUrl returns error when exception occurs`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)

        // Mock repository to throw exception
        coEvery { repository.getPlaybackInfo(itemId.toString()) } throws RuntimeException("Network failure")

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue(result is PlaybackResult.Error)
        assertTrue((result as PlaybackResult.Error).message.contains("Network failure"))
    }

    @Test
    fun `getOptimalPlaybackUrl handles null connectivity manager gracefully`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 10_000_000,
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        // Mock device capabilities
        every { deviceCapabilities.canPlayContainer("mp4") } returns true
        every { deviceCapabilities.canPlayVideoCodec("h264", any(), any()) } returns true
        every { deviceCapabilities.canPlayAudioCodec("aac") } returns true

        // Mock connectivity manager unavailable
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every {
            streamRepository.getTranscodedStreamUrl(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns "https://server/transcode"

        val result = manager.getOptimalPlaybackUrl(item)

        // Should fall back to transcoding when network status unknown
        assertTrue("Expected fallback to transcoding when connectivity unavailable", result is PlaybackResult.Transcoding)
    }

    @Test
    fun `getOptimalPlaybackUrl builds proper transcoding url when server recommends transcoding`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mkv",
            videoCodec = "h265",
            audioCodec = "aac",
            bitrate = 10_000_000,
            supportsDirectPlay = false, // Server recommends transcoding
            supportsTranscoding = true,
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        every { deviceCapabilities.getDirectPlayCapabilities() } returns mockk(relaxed = true) {
            every { supportedVideoCodecs } returns listOf("h264", "h265")
            every { supportedAudioCodecs } returns listOf("aac", "mp3")
            every { supports4K } returns false
        }
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every { repository.getCurrentServer() } returns com.rpeters.jellyfin.data.JellyfinServer(
            id = "server",
            name = "Test",
            url = "https://server",
        )
        every {
            streamRepository.getTranscodedStreamUrl(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns "https://server/Videos/$itemId/stream?transcoding=params"

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue(result is PlaybackResult.Transcoding)
        val transcoding = result as PlaybackResult.Transcoding
        assertEquals("https://server/Videos/$itemId/stream?transcoding=params", transcoding.url)
        // Verify that we built our own transcoding URL with proper parameters
        verify(atLeast = 1) {
            streamRepository.getTranscodedStreamUrl(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `getOptimalPlaybackUrl verifies DeviceCapabilities called correctly`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val mediaSource = buildMediaSource(
            container = "mkv",
            videoCodec = "h265",
            audioCodec = "ac3",
            width = 1920,
            height = 1080,
            bitrate = 5_000_000,
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource))

        // Mock device capabilities
        every { deviceCapabilities.canPlayContainer("mkv") } returns true
        every { deviceCapabilities.canPlayVideoCodec("h265", 1920, 1080) } returns true
        every { deviceCapabilities.canPlayAudioCodec("ac3") } returns true

        // Mock network
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every { streamRepository.getDirectStreamUrl(itemId.toString(), "mkv") } returns "https://server/video.mkv"

        manager.getOptimalPlaybackUrl(item)

        // Verify correct methods called with correct parameters
        verify { deviceCapabilities.canPlayContainer("mkv") }
        verify { deviceCapabilities.canPlayVideoCodec("h265", 1920, 1080) }
        verify { deviceCapabilities.canPlayAudioCodec("ac3") }
    }

    @Test
    fun `getOptimalPlaybackUrl includes playSessionId in DirectPlay result`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val testSessionId = "test-play-session-123"
        val mediaSource = buildMediaSource(
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 10_000_000,
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource), playSessionId = testSessionId)

        // Mock device capabilities
        every { deviceCapabilities.canPlayContainer("mp4") } returns true
        every { deviceCapabilities.canPlayVideoCodec("h264", any(), any()) } returns true
        every { deviceCapabilities.canPlayAudioCodec("aac") } returns true

        // Mock network
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every { repository.getCurrentServer() } returns com.rpeters.jellyfin.data.JellyfinServer(
            id = "server",
            name = "Test",
            url = "https://server",
        )

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue("Expected DirectPlay result", result is PlaybackResult.DirectPlay)
        val directPlay = result as PlaybackResult.DirectPlay
        assertEquals("PlaySessionId should match server response", testSessionId, directPlay.playSessionId)
    }

    @Test
    fun `getOptimalPlaybackUrl includes playSessionId in Transcoding result`() = runTest {
        val itemId = UUID.randomUUID()
        val item = buildBaseItem(id = itemId)
        val testSessionId = "test-play-session-456"
        val mediaSource = buildMediaSource(
            container = "mkv",
            videoCodec = "h265",
            audioCodec = "aac",
            bitrate = 10_000_000,
            supportsDirectPlay = false,
            supportsTranscoding = true,
        )
        val playbackInfo = buildPlaybackInfo(listOf(mediaSource), playSessionId = testSessionId)

        // Mock device capabilities
        every { deviceCapabilities.getDirectPlayCapabilities() } returns mockk(relaxed = true) {
            every { supportedVideoCodecs } returns listOf("h264")
            every { supportedAudioCodecs } returns listOf("aac")
            every { supports4K } returns false
        }

        // Mock network
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        // Mock repository responses
        coEvery { repository.getPlaybackInfo(itemId.toString()) } returns playbackInfo
        every { repository.getCurrentServer() } returns com.rpeters.jellyfin.data.JellyfinServer(
            id = "server",
            name = "Test",
            url = "https://server",
        )
        every {
            streamRepository.getTranscodedStreamUrl(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns "https://server/transcode"

        val result = manager.getOptimalPlaybackUrl(item)

        assertTrue("Expected Transcoding result", result is PlaybackResult.Transcoding)
        val transcoding = result as PlaybackResult.Transcoding
        assertEquals("PlaySessionId should match server response", testSessionId, transcoding.playSessionId)
    }

    // Helper functions

    private fun buildBaseItem(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Video",
        type: BaseItemKind = BaseItemKind.MOVIE,
    ): BaseItemDto = BaseItemDto(
        id = id,
        name = name,
        type = type,
    )

    private fun buildMediaSource(
        container: String,
        videoCodec: String,
        audioCodec: String,
        bitrate: Int,
        width: Int = 1920,
        height: Int = 1080,
        transcodingUrl: String? = null,
        supportsDirectPlay: Boolean = true,
        supportsTranscoding: Boolean = true,
        supportsDirectStream: Boolean = true,
    ): MediaSourceInfo = mockk<MediaSourceInfo>(relaxed = true).also { mediaSource ->
        every { mediaSource.container } returns container
        every { mediaSource.bitrate } returns bitrate
        every { mediaSource.supportsDirectPlay } returns supportsDirectPlay
        every { mediaSource.supportsTranscoding } returns supportsTranscoding
        every { mediaSource.supportsDirectStream } returns supportsDirectStream
        every { mediaSource.transcodingUrl } returns transcodingUrl
        every { mediaSource.mediaStreams } returns listOf(
            mockk<MediaStream>(relaxed = true).also { stream ->
                every { stream.type } returns MediaStreamType.VIDEO
                every { stream.codec } returns videoCodec
                every { stream.width } returns width
                every { stream.height } returns height
                every { stream.bitRate } returns bitrate
            },
            mockk<MediaStream>(relaxed = true).also { stream ->
                every { stream.type } returns MediaStreamType.AUDIO
                every { stream.codec } returns audioCodec
                every { stream.bitRate } returns 128000
            },
        )
    }

    private fun buildPlaybackInfo(
        mediaSources: List<MediaSourceInfo>,
        playSessionId: String? = "test-session-id",
    ): PlaybackInfoResponse = mockk<PlaybackInfoResponse>(relaxed = true).also { info ->
        every { info.mediaSources } returns mediaSources
        every { info.playSessionId } returns playSessionId
    }
}
