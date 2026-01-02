package com.rpeters.jellyfin.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.cash.turbine.test
import com.rpeters.jellyfin.ui.player.audio.AudioPlaybackState
import com.rpeters.jellyfin.ui.player.audio.AudioServiceConnection
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlaybackViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var audioServiceConnection: AudioServiceConnection

    private lateinit var viewModel: AudioPlaybackViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testPlaybackState = MutableStateFlow(AudioPlaybackState())
    private val testQueueState = MutableStateFlow<List<MediaItem>>(emptyList())

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        Dispatchers.setMain(testDispatcher)

        // Setup mock flows
        coEvery { audioServiceConnection.playbackState } returns testPlaybackState
        coEvery { audioServiceConnection.queueState } returns testQueueState
        coEvery { audioServiceConnection.ensureController() } returns mockk(relaxed = true)
        coEvery { audioServiceConnection.refreshState() } returns Unit

        viewModel = AudioPlaybackViewModel(audioServiceConnection)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init calls ensureController and refreshState`() = runTest(testDispatcher) {
        // When - ViewModel is created (in setup)
        advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { audioServiceConnection.ensureController() }
        coVerify(exactly = 1) { audioServiceConnection.refreshState() }
    }

    @Test
    fun `playbackState exposes service connection playbackState`() = runTest(testDispatcher) {
        // Given
        val expectedState = AudioPlaybackState(
            isConnected = true,
            isPlaying = true,
            shuffleEnabled = true,
            repeatMode = Player.REPEAT_MODE_ALL,
        )

        // Advance past initialization
        advanceUntilIdle()

        // When
        testPlaybackState.value = expectedState
        advanceUntilIdle()

        // Then
        viewModel.playbackState.test {
            assertEquals(expectedState, awaitItem())
        }
    }

    @Test
    fun `queue exposes service connection queueState`() = runTest(testDispatcher) {
        // Given
        val mediaItem1 = mockk<MediaItem>(relaxed = true)
        val mediaItem2 = mockk<MediaItem>(relaxed = true)
        val expectedQueue = listOf(mediaItem1, mediaItem2)

        // When
        testQueueState.value = expectedQueue

        // Then
        viewModel.queue.test {
            assertEquals(expectedQueue, awaitItem())
        }
    }

    @Test
    fun `togglePlayPause delegates to service connection`() = runTest(testDispatcher) {
        // When
        viewModel.togglePlayPause()

        // Then
        verify(exactly = 1) { audioServiceConnection.togglePlayPause() }
    }

    @Test
    fun `toggleShuffle delegates to service connection`() = runTest(testDispatcher) {
        // When
        viewModel.toggleShuffle()

        // Then
        verify(exactly = 1) { audioServiceConnection.toggleShuffle() }
    }

    @Test
    fun `toggleRepeat delegates to service connection`() = runTest(testDispatcher) {
        // When
        viewModel.toggleRepeat()

        // Then
        verify(exactly = 1) { audioServiceConnection.toggleRepeat() }
    }

    @Test
    fun `skipToNext delegates to service connection`() = runTest(testDispatcher) {
        // When
        viewModel.skipToNext()

        // Then
        verify(exactly = 1) { audioServiceConnection.skipToNext() }
    }

    @Test
    fun `skipToPrevious delegates to service connection`() = runTest(testDispatcher) {
        // When
        viewModel.skipToPrevious()

        // Then
        verify(exactly = 1) { audioServiceConnection.skipToPrevious() }
    }

    @Test
    fun `seekTo delegates to service connection with correct position`() = runTest(testDispatcher) {
        // Given
        val positionMs = 30000L

        // When
        viewModel.seekTo(positionMs)

        // Then
        verify(exactly = 1) { audioServiceConnection.seekTo(positionMs) }
    }

    @Test
    fun `seekForward delegates to service connection with default amount`() = runTest(testDispatcher) {
        // When
        viewModel.seekForward()

        // Then
        verify(exactly = 1) { audioServiceConnection.seekForward(10000L) }
    }

    @Test
    fun `seekForward delegates to service connection with custom amount`() = runTest(testDispatcher) {
        // Given
        val customAmount = 15000L

        // When
        viewModel.seekForward(customAmount)

        // Then
        verify(exactly = 1) { audioServiceConnection.seekForward(customAmount) }
    }

    @Test
    fun `seekBackward delegates to service connection with default amount`() = runTest(testDispatcher) {
        // When
        viewModel.seekBackward()

        // Then
        verify(exactly = 1) { audioServiceConnection.seekBackward(10000L) }
    }

    @Test
    fun `seekBackward delegates to service connection with custom amount`() = runTest(testDispatcher) {
        // Given
        val customAmount = 5000L

        // When
        viewModel.seekBackward(customAmount)

        // Then
        verify(exactly = 1) { audioServiceConnection.seekBackward(customAmount) }
    }

    @Test
    fun `playMediaItem delegates to service connection`() = runTest(testDispatcher) {
        // Given
        val mediaItem = mockk<MediaItem>(relaxed = true)

        // When
        viewModel.playMediaItem(mediaItem)

        // Then
        verify(exactly = 1) { audioServiceConnection.playNow(mediaItem) }
    }

    @Test
    fun `addToQueue delegates to service connection`() = runTest(testDispatcher) {
        // Given
        val mediaItem = mockk<MediaItem>(relaxed = true)

        // When
        viewModel.addToQueue(mediaItem)

        // Then
        verify(exactly = 1) { audioServiceConnection.enqueue(mediaItem) }
    }

    @Test
    fun `removeFromQueue delegates to service connection with correct index`() = runTest(testDispatcher) {
        // Given
        val index = 2

        // When
        viewModel.removeFromQueue(index)

        // Then
        verify(exactly = 1) { audioServiceConnection.removeFromQueue(index) }
    }

    @Test
    fun `clearQueue delegates to service connection`() = runTest(testDispatcher) {
        // When
        viewModel.clearQueue()

        // Then
        verify(exactly = 1) { audioServiceConnection.clearQueue() }
    }

    @Test
    fun `skipToQueueItem delegates to service connection with correct index`() = runTest(testDispatcher) {
        // Given
        val index = 3

        // When
        viewModel.skipToQueueItem(index)

        // Then
        verify(exactly = 1) { audioServiceConnection.skipToQueueItem(index) }
    }

    @Test
    fun `updatePlaybackProgress updates currentPosition`() = runTest(testDispatcher) {
        // Given
        val position = 45000L
        val duration = 180000L

        // When
        viewModel.updatePlaybackProgress(position, duration)

        // Then
        viewModel.currentPosition.test {
            assertEquals(position, awaitItem())
        }
    }

    @Test
    fun `updatePlaybackProgress updates duration`() = runTest(testDispatcher) {
        // Given
        val position = 45000L
        val duration = 180000L

        // When
        viewModel.updatePlaybackProgress(position, duration)

        // Then
        viewModel.duration.test {
            assertEquals(duration, awaitItem())
        }
    }

    @Test
    fun `currentPosition initial state is 0`() = runTest(testDispatcher) {
        // Then
        viewModel.currentPosition.test {
            assertEquals(0L, awaitItem())
        }
    }

    @Test
    fun `duration initial state is 0`() = runTest(testDispatcher) {
        // Then
        viewModel.duration.test {
            assertEquals(0L, awaitItem())
        }
    }

    @Test
    fun `multiple playback progress updates emit correctly`() = runTest(testDispatcher) {
        // When & Then
        viewModel.currentPosition.test {
            // Initial value
            assertEquals(0L, awaitItem())

            // First update
            viewModel.updatePlaybackProgress(10000L, 100000L)
            assertEquals(10000L, awaitItem())

            // Second update
            viewModel.updatePlaybackProgress(20000L, 100000L)
            assertEquals(20000L, awaitItem())

            // Third update
            viewModel.updatePlaybackProgress(30000L, 100000L)
            assertEquals(30000L, awaitItem())
        }
    }

    @Test
    fun `playbackState updates when service connection state changes`() = runTest(testDispatcher) {
        // Given
        val initialState = AudioPlaybackState(isConnected = false, isPlaying = false)
        val connectedState = AudioPlaybackState(isConnected = true, isPlaying = false)
        val playingState = AudioPlaybackState(isConnected = true, isPlaying = true)

        testPlaybackState.value = initialState

        // When & Then
        viewModel.playbackState.test {
            // Initial state
            assertEquals(initialState, awaitItem())

            // Connected
            testPlaybackState.value = connectedState
            assertEquals(connectedState, awaitItem())

            // Playing
            testPlaybackState.value = playingState
            assertEquals(playingState, awaitItem())
        }
    }

    @Test
    fun `queue updates when service connection queue changes`() = runTest(testDispatcher) {
        // Given
        val mediaItem1 = mockk<MediaItem>(relaxed = true)
        val mediaItem2 = mockk<MediaItem>(relaxed = true)
        val mediaItem3 = mockk<MediaItem>(relaxed = true)

        // When & Then
        viewModel.queue.test {
            // Initial empty queue
            assertEquals(emptyList<MediaItem>(), awaitItem())

            // Add first item
            testQueueState.value = listOf(mediaItem1)
            assertEquals(listOf(mediaItem1), awaitItem())

            // Add second item
            testQueueState.value = listOf(mediaItem1, mediaItem2)
            assertEquals(listOf(mediaItem1, mediaItem2), awaitItem())

            // Add third item
            testQueueState.value = listOf(mediaItem1, mediaItem2, mediaItem3)
            assertEquals(listOf(mediaItem1, mediaItem2, mediaItem3), awaitItem())

            // Remove item
            testQueueState.value = listOf(mediaItem1, mediaItem3)
            assertEquals(listOf(mediaItem1, mediaItem3), awaitItem())

            // Clear queue
            testQueueState.value = emptyList()
            assertEquals(emptyList<MediaItem>(), awaitItem())
        }
    }
}
