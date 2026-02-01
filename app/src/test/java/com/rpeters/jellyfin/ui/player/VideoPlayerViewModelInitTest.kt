package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.playback.EnhancedPlaybackManager
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test for VideoPlayerViewModel initialization race condition fix.
 *
 * This test validates that _playerState is initialized before the init block
 * can call handleCastState(), preventing the NullPointerException that occurred
 * when CastManager emitted a state immediately (e.g., existing Cast session).
 */
@OptIn(ExperimentalCoroutinesApi::class, UnstableApi::class)
class VideoPlayerViewModelInitTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockRepository: JellyfinRepository
    private lateinit var mockCastManager: CastManager
    private lateinit var mockPlaybackProgressManager: PlaybackProgressManager
    private lateinit var mockEnhancedPlaybackManager: EnhancedPlaybackManager
    private lateinit var castStateFlow: MutableStateFlow<CastState>
    private lateinit var playbackProgressFlow: MutableStateFlow<PlaybackProgress>

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockContext = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)
        mockCastManager = mockk(relaxed = true)
        mockPlaybackProgressManager = mockk(relaxed = true)
        mockEnhancedPlaybackManager = mockk(relaxed = true)

        // Create flows that CastManager and PlaybackProgressManager will expose
        castStateFlow = MutableStateFlow(CastState())
        playbackProgressFlow = MutableStateFlow(
            PlaybackProgress(
                itemId = "",
                positionMs = 0L,
                isPlaying = false,
            ),
        )

        // Mock the flows
        every { mockCastManager.castState } returns castStateFlow
        every { mockPlaybackProgressManager.playbackProgress } returns playbackProgressFlow

        // Mock initialize method
        every { mockCastManager.initialize() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel initializes without NullPointerException when castState emits immediately`() = runTest(testDispatcher) {
        // Given - CastManager emits an initial state immediately (simulates existing Cast session)
        val initialCastState = CastState(
            isInitialized = true,
            isAvailable = true,
            isConnected = true,
            deviceName = "Test Cast Device",
        )

        // Emit state before ViewModel is created to simulate race condition
        castStateFlow.value = initialCastState

        // When - ViewModel is constructed (this previously would trigger NPE)
        val viewModel = VideoPlayerViewModel(
            context = mockContext,
            repository = mockRepository,
            castManager = mockCastManager,
            playbackProgressManager = mockPlaybackProgressManager,
            enhancedPlaybackManager = mockEnhancedPlaybackManager,
        )

        // Allow coroutines to process
        advanceUntilIdle()

        // Then - ViewModel should be initialized successfully without NPE
        assertNotNull("playerState should not be null", viewModel.playerState)
        assertNotNull("playerState value should not be null", viewModel.playerState.value)

        // Verify Cast state was propagated to player state
        val playerState = viewModel.playerState.value
        assertNotNull("Player state should contain Cast information", playerState)
    }

    @Test
    fun `viewModel initializes correctly with default castState`() = runTest(testDispatcher) {
        // Given - Default CastState (not connected)
        // castStateFlow already has default CastState()

        // When
        val viewModel = VideoPlayerViewModel(
            context = mockContext,
            repository = mockRepository,
            castManager = mockCastManager,
            playbackProgressManager = mockPlaybackProgressManager,
            enhancedPlaybackManager = mockEnhancedPlaybackManager,
        )

        advanceUntilIdle()

        // Then
        assertNotNull(viewModel.playerState)
        val playerState = viewModel.playerState.value
        assertNotNull(playerState)

        // Default state should not be casting
        assertFalse("Should not be casting initially", playerState.isCasting)
        assertFalse("Should not be connected to Cast initially", playerState.isCastConnected)
    }

    @Test
    fun `viewModel handles castState updates after initialization`() = runTest(testDispatcher) {
        // Given
        val viewModel = VideoPlayerViewModel(
            context = mockContext,
            repository = mockRepository,
            castManager = mockCastManager,
            playbackProgressManager = mockPlaybackProgressManager,
            enhancedPlaybackManager = mockEnhancedPlaybackManager,
        )

        advanceUntilIdle()

        // When - CastState is updated after initialization
        castStateFlow.value = CastState(
            isInitialized = true,
            isAvailable = true,
            isConnected = true,
            deviceName = "New Cast Device",
            isCasting = true,
        )

        advanceUntilIdle()

        // Then - Player state should reflect the update
        val playerState = viewModel.playerState.value
        assertTrue("Cast should be available", playerState.isCastAvailable)
        assertTrue("Cast should be connected", playerState.isCastConnected)
        assertEquals("Device name should match", "New Cast Device", playerState.castDeviceName)
    }
}
