package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.rpeters.jellyfin.data.offline.OfflinePlaybackManager
import com.rpeters.jellyfin.data.playback.AdaptiveBitrateMonitor
import com.rpeters.jellyfin.data.playback.EnhancedPlaybackManager
import com.rpeters.jellyfin.data.playback.PlaybackResult
import com.rpeters.jellyfin.data.preferences.PlaybackPreferences
import com.rpeters.jellyfin.data.preferences.PlaybackPreferencesRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.AnalyticsHelper
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class, UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class VideoPlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @MockK
    private lateinit var repository: JellyfinRepository

    @MockK
    private lateinit var castManager: CastManager

    @MockK
    private lateinit var playbackProgressManager: PlaybackProgressManager

    @MockK
    private lateinit var enhancedPlaybackManager: EnhancedPlaybackManager

    @MockK
    private lateinit var adaptiveBitrateMonitor: AdaptiveBitrateMonitor

    @MockK
    private lateinit var analytics: AnalyticsHelper

    @MockK
    private lateinit var okHttpClient: OkHttpClient

    @MockK
    private lateinit var playbackPreferencesRepository: PlaybackPreferencesRepository

    @MockK
    private lateinit var offlinePlaybackManager: OfflinePlaybackManager

    private lateinit var mockExoPlayer: ExoPlayer

    private lateinit var context: Context
    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        context = ApplicationProvider.getApplicationContext()

        // Mock mandatory flows
        every { castManager.castState } returns MutableStateFlow(CastState())
        every { playbackProgressManager.playbackProgress } returns MutableStateFlow(PlaybackProgress("", 0L))
        every { adaptiveBitrateMonitor.qualityRecommendation } returns MutableStateFlow(null)
        every { playbackPreferencesRepository.preferences } returns MutableStateFlow(PlaybackPreferences.DEFAULT)
        every { offlinePlaybackManager.isOfflinePlaybackAvailable(any()) } returns false

        // Mock ExoPlayer and its Builder
        mockExoPlayer = mockk(relaxed = true)
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns mockExoPlayer
        
        // Mock common player properties
        every { mockExoPlayer.playWhenReady } returns true
        every { mockExoPlayer.isPlaying } returns true

        // Mock common repository methods
        coEvery { repository.getEpisodeDetails(any()) } returns ApiResult.Error("Not an episode")
        coEvery { repository.getMovieDetails(any()) } returns ApiResult.Success(mockk(relaxed = true))
        coEvery { repository.getPlaybackInfo(any()) } returns mockk(relaxed = true)
        coEvery { playbackProgressManager.getResumePosition(any()) } returns 0L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any()) } returns PlaybackResult.DirectPlay(
            url = "http://test.com/video.mp4",
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 5000000,
            reason = "Direct play supported",
            playSessionId = "session123"
        )

        viewModel = VideoPlayerViewModel(
            context = context,
            repository = repository,
            castManager = castManager,
            playbackProgressManager = playbackProgressManager,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            analytics = analytics,
            okHttpClient = okHttpClient,
            playbackPreferencesRepository = playbackPreferencesRepository,
            offlinePlaybackManager = offlinePlaybackManager,
        )
    }

    @After
    fun tearDown() {
        unmockkConstructor(ExoPlayer.Builder::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.playerState.value
        assertNotNull(state)
        assertEquals("", state.itemId)
        assertNull(state.error)
    }

    @Test
    fun `initializePlayer sets item info and loads metadata`() = runTest {
        // Arrange
        val itemId = UUID.randomUUID().toString()
        val itemName = "Test Movie"
        val item = BaseItemDto(
            id = UUID.fromString(itemId),
            name = itemName,
            type = BaseItemKind.MOVIE,
            runTimeTicks = 36_000_000_000L // 1 hour = 3600s = 36,000,000,000 ticks
        )

        coEvery { repository.getMovieDetails(itemId) } returns ApiResult.Success(item)
        
        // Act
        viewModel.initializePlayer(itemId, itemName, 0L)
        advanceUntilIdle()

        // Assert
        val state = viewModel.playerState.value
        assertNull("Error should be null: ${state.error}", state.error)
        assertEquals(itemId, state.itemId)
        assertEquals(itemName, state.itemName)
        assertEquals(3600_000L, state.duration) // 3600_000 ms = 1 hour
    }

    @Test
    fun `initializePlayer with resume position uses it when enabled`() = runTest {
        // Arrange
        val itemId = UUID.randomUUID().toString()
        val itemName = "Test Movie"
        val resumePosition = 500_000L // 500s

        coEvery { playbackProgressManager.getResumePosition(itemId) } returns resumePosition
        every { mockExoPlayer.currentPosition } returns resumePosition

        // Act
        viewModel.initializePlayer(itemId, itemName, 0L)
        advanceUntilIdle()

        // Assert
        assertNull(viewModel.playerState.value.error)
        assertNotNull(viewModel.exoPlayer)
        assertEquals(resumePosition, viewModel.exoPlayer?.currentPosition)
    }

    @Test
    fun `togglePlayPause toggles state`() = runTest {
        // Arrange
        val itemId = UUID.randomUUID().toString()
        viewModel.initializePlayer(itemId, "Test", 0L)
        advanceUntilIdle()

        assertNull(viewModel.playerState.value.error)
        assertNotNull(viewModel.exoPlayer)

        // Initial state: playing
        every { mockExoPlayer.isPlaying } returns true
        every { mockExoPlayer.playWhenReady } returns true

        // Act - Pause
        viewModel.togglePlayPause()
        advanceUntilIdle()

        // Assert
        verify { mockExoPlayer.pause() }
        
        // Mock state change after pause
        every { mockExoPlayer.isPlaying } returns false
        every { mockExoPlayer.playWhenReady } returns false
        
        // Act - Resume
        viewModel.togglePlayPause() // This will now call play() because isPlaying is false
        advanceUntilIdle()
        
        // Assert
        verify { mockExoPlayer.play() }
    }

    @Test
    fun `seekTo updates player position`() = runTest {
        // Arrange
        val itemId = UUID.randomUUID().toString()
        viewModel.initializePlayer(itemId, "Test", 0L)
        advanceUntilIdle()

        // Act
        viewModel.seekTo(300_000L)
        advanceUntilIdle()

        // Assert
        verify { mockExoPlayer.seekTo(300_000L) }
    }

    @Test
    fun `releasePlayerImmediate stops tracking and releases player`() = runTest {
        // Arrange
        val itemId = UUID.randomUUID().toString()
        viewModel.initializePlayer(itemId, "Test", 0L)
        advanceUntilIdle()

        assertNotNull(viewModel.exoPlayer)

        // Act
        viewModel.releasePlayerImmediate()
        advanceUntilIdle()

        // Assert
        assertNull(viewModel.exoPlayer)
        verify { mockExoPlayer.release() }
    }

    @Test
    fun `changeAspectRatio updates state`() = runTest {
        // Arrange
        assertEquals(AspectRatioMode.AUTO, viewModel.playerState.value.selectedAspectRatio)

        // Act
        viewModel.changeAspectRatio(AspectRatioMode.FILL)
        
        // Assert
        assertEquals(AspectRatioMode.FILL, viewModel.playerState.value.selectedAspectRatio)
    }
}
