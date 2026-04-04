package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.rpeters.jellyfin.data.playback.AdaptiveBitrateMonitor
import com.rpeters.jellyfin.data.preferences.PlaybackPreferences
import com.rpeters.jellyfin.data.preferences.PlaybackPreferencesRepository
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
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
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var repository: IJellyfinRepository

    @MockK
    private lateinit var stateManager: VideoPlayerStateManager

    @MockK
    private lateinit var playbackManager: VideoPlayerPlaybackManager

    @MockK
    private lateinit var trackManager: VideoPlayerTrackManager

    @MockK
    private lateinit var castManager: VideoPlayerCastManager

    @MockK
    private lateinit var metadataManager: VideoPlayerMetadataManager

    @MockK
    private lateinit var playbackProgressManager: PlaybackProgressManager

    @MockK
    private lateinit var playbackPreferencesRepository: PlaybackPreferencesRepository

    @MockK
    private lateinit var adaptiveBitrateMonitor: AdaptiveBitrateMonitor

    private lateinit var mockExoPlayer: ExoPlayer

    private lateinit var context: Context
    private lateinit var viewModel: VideoPlayerViewModel

    private val playerStateFlow = MutableStateFlow(VideoPlayerState())

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        context = ApplicationProvider.getApplicationContext()

        // Mock mandatory flows
        every { stateManager.playerState } returns playerStateFlow
        every { playbackProgressManager.playbackProgress } returns MutableStateFlow(PlaybackProgress("", 0L))
        every { adaptiveBitrateMonitor.qualityRecommendation } returns MutableStateFlow(null)
        every { playbackPreferencesRepository.preferences } returns MutableStateFlow(PlaybackPreferences.DEFAULT)

        mockExoPlayer = mockk(relaxed = true)
        every { playbackManager.exoPlayer } returns mockExoPlayer

        viewModel = VideoPlayerViewModel(
            context = context,
            repository = repository,
            stateManager = stateManager,
            playbackManager = playbackManager,
            trackManager = trackManager,
            castManager = castManager,
            metadataManager = metadataManager,
            playbackProgressManager = playbackProgressManager,
            playbackPreferencesRepository = playbackPreferencesRepository,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.playerState.value
        assertNotNull(state)
        assertEquals("", state.itemId)
    }

    @Test
    fun `togglePlayPause delegates to exoPlayer`() = runTest {
        // Arrange
        every { mockExoPlayer.isPlaying } returns true

        // Act
        viewModel.togglePlayPause()

        // Assert
        verify { mockExoPlayer.pause() }

        // Arrange
        every { mockExoPlayer.isPlaying } returns false

        // Act
        viewModel.togglePlayPause()

        // Assert
        verify { mockExoPlayer.play() }
    }

    @Test
    fun `seekTo updates player position`() = runTest {
        // Act
        viewModel.seekTo(300_000L)

        // Assert
        verify { mockExoPlayer.seekTo(300_000L) }
    }

    @Test
    fun `changeAspectRatio updates state`() = runTest {
        // Act
        viewModel.changeAspectRatio(AspectRatioMode.FILL)

        // Assert
        verify { stateManager.updateState(any()) }
    }

    @Test
    fun `onIntent TogglePlayPause delegates correctly`() = runTest {
        // Arrange
        every { mockExoPlayer.isPlaying } returns true

        // Act
        viewModel.onIntent(VideoPlayerIntent.TogglePlayPause)

        // Assert
        verify { mockExoPlayer.pause() }
    }

    @Test
    fun `acceptQualityRecommendation persists recommended quality before restart`() = runTest {
        val recommendation = com.rpeters.jellyfin.data.playback.QualityRecommendation(
            recommendedQuality = com.rpeters.jellyfin.data.preferences.TranscodingQuality.MEDIUM,
            reason = "Buffering",
            severity = com.rpeters.jellyfin.data.playback.RecommendationSeverity.MEDIUM,
        )
        playerStateFlow.value = VideoPlayerState(
            itemId = "item-1",
            itemName = "Test Video",
            currentPosition = 12_000L,
            qualityRecommendation = recommendation,
        )
        every { mockExoPlayer.currentPosition } returns 12_000L
        coEvery { playbackPreferencesRepository.setTranscodingQuality(recommendation.recommendedQuality) } just Runs

        viewModel.onIntent(VideoPlayerIntent.AcceptQualityRecommendation)
        advanceUntilIdle()

        coVerify { playbackPreferencesRepository.setTranscodingQuality(recommendation.recommendedQuality) }
        coVerify { playbackManager.releasePlayer(reportStop = false) }
    }
}
