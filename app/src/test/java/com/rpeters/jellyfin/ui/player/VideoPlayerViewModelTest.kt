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

private const val TEST_ITEM_ID = "item-1"
private const val TEST_ITEM_NAME = "Test Video"

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

        // VideoPlayerStateManager is mocked, so by default updateState() is a no-op that never
        // touches playerStateFlow. Make it behave like the real thing (apply the reducer lambda
        // to playerStateFlow) so tests can assert on the actual resulting state, not just that
        // updateState() was called with *some* lambda.
        val updateStateSlot = slot<(VideoPlayerState) -> VideoPlayerState>()
        every { stateManager.updateState(capture(updateStateSlot)) } answers {
            playerStateFlow.value = updateStateSlot.captured(playerStateFlow.value)
        }

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
    fun `ConfirmResumePlayback with nothing pending just hides the dialog`() = runTest {
        playerStateFlow.value = VideoPlayerState(
            itemId = TEST_ITEM_ID,
            resumeDialogPositionMs = 42_000L,
            showResumeDialog = true,
        )

        viewModel.onIntent(VideoPlayerIntent.ConfirmResumePlayback)
        advanceUntilIdle()

        assertFalse(playerStateFlow.value.showResumeDialog)
        assertEquals(0L, playerStateFlow.value.resumeDialogPositionMs)
    }

    @Test
    fun `DismissResumeDialog with nothing pending just hides the dialog`() = runTest {
        playerStateFlow.value = VideoPlayerState(
            itemId = TEST_ITEM_ID,
            resumeDialogPositionMs = 42_000L,
            showResumeDialog = true,
        )

        viewModel.onIntent(VideoPlayerIntent.DismissResumeDialog)
        advanceUntilIdle()

        assertFalse(playerStateFlow.value.showResumeDialog)
        assertEquals(0L, playerStateFlow.value.resumeDialogPositionMs)
    }

    @Test
    fun `Ask mode defers playback start until Resume is confirmed, then starts at the saved position`() = runTest {
        every { playbackPreferencesRepository.preferences } returns MutableStateFlow(
            PlaybackPreferences.DEFAULT.copy(resumePlaybackMode = com.rpeters.jellyfin.data.preferences.ResumePlaybackMode.ASK),
        )
        val askViewModel = VideoPlayerViewModel(
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
        coEvery { playbackProgressManager.getResumePosition(any()) } returns 42_000L
        coEvery { metadataManager.loadSkipMarkers(any()) } returns null
        coEvery { metadataManager.extractSubtitleSpecs(any(), any()) } returns emptyList()
        every { metadataManager.extractSubtitleTracks(any(), any()) } returns emptyList()
        every { playbackManager.isMuted() } returns false
        every { playbackManager.currentPlaySessionId } returns null
        every { playbackManager.currentMediaSourceId } returns null

        askViewModel.onIntent(VideoPlayerIntent.Initialize(itemId = TEST_ITEM_ID, itemName = TEST_ITEM_NAME))
        advanceUntilIdle()

        // Playback must not start yet -- it's deferred behind the resume dialog. Starting it now
        // (this was the P1 bug) would autoplay from 0 behind the modal and race the dialog's
        // eventual seek against a player/media item that doesn't exist yet.
        coVerify(exactly = 0) {
            playbackManager.startPlayback(
                itemId = any(), itemName = any(), startPosition = any(), metadata = any(),
                sideLoadedSubs = any(), forceOffline = any(), audioIndex = any(),
                subtitleIndex = any(), mediaSourceIdHint = any(), scope = any(),
            )
        }
        assertTrue(playerStateFlow.value.showResumeDialog)
        assertEquals(42_000L, playerStateFlow.value.resumeDialogPositionMs)

        askViewModel.onIntent(VideoPlayerIntent.ConfirmResumePlayback)
        advanceUntilIdle()

        assertFalse(playerStateFlow.value.showResumeDialog)
        coVerify {
            playbackManager.startPlayback(
                itemId = TEST_ITEM_ID, itemName = TEST_ITEM_NAME, startPosition = 42_000L, metadata = any(),
                sideLoadedSubs = any(), forceOffline = any(), audioIndex = any(),
                subtitleIndex = any(), mediaSourceIdHint = any(), scope = any(),
            )
        }
    }

    @Test
    fun `auto-skip intro does nothing when the preference is disabled`() = runTest {
        // Class-level viewModel was built with PlaybackPreferences.DEFAULT (autoSkipIntroAndCredits = false).
        playerStateFlow.value = VideoPlayerState(
            itemId = TEST_ITEM_ID,
            introStartMs = 0L,
            introEndMs = 30_000L,
            currentPosition = 5_000L,
        )
        advanceUntilIdle()

        verify(exactly = 0) { mockExoPlayer.seekTo(30_000L) }
    }

    @Test
    fun `auto-skip intro seeks past the intro once when enabled`() = runTest {
        every { playbackPreferencesRepository.preferences } returns MutableStateFlow(
            PlaybackPreferences.DEFAULT.copy(autoSkipIntroAndCredits = true),
        )
        val autoSkipViewModel = VideoPlayerViewModel(
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
        advanceUntilIdle()

        playerStateFlow.value = VideoPlayerState(
            itemId = TEST_ITEM_ID,
            introStartMs = 0L,
            introEndMs = 30_000L,
            currentPosition = 5_000L,
        )
        advanceUntilIdle()

        verify { mockExoPlayer.seekTo(30_000L) }
        assertNotNull(autoSkipViewModel.playerState)
    }

    @Test
    fun `acceptQualityRecommendation persists recommended quality before restart`() = runTest {
        val recommendation = com.rpeters.jellyfin.data.playback.QualityRecommendation(
            recommendedQuality = com.rpeters.jellyfin.data.preferences.TranscodingQuality.MEDIUM,
            reason = "Buffering",
            severity = com.rpeters.jellyfin.data.playback.RecommendationSeverity.MEDIUM,
        )
        playerStateFlow.value = VideoPlayerState(
            itemId = TEST_ITEM_ID,
            itemName = TEST_ITEM_NAME,
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
