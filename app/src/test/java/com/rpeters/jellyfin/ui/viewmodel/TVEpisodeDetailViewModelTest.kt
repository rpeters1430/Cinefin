package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TVEpisodeDetailViewModelTest {
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val viewModel by lazy { TVEpisodeDetailViewModel(enhancedPlaybackUtils) }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadEpisodeAnalysis stores analysis result`() = runTest {
        val episode = createEpisode()
        val analysis = createPlaybackAnalysis()
        coEvery { enhancedPlaybackUtils.analyzePlaybackCapabilities(episode) } returns analysis

        viewModel.loadEpisodeAnalysis(episode)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(analysis, viewModel.state.value.playbackAnalysis)
    }

    @Test
    fun `loadEpisodeAnalysis logs errors and recovers across exceptions`() = runTest {
        val episode = createEpisode()
        val messageSlot = slot<String>()
        val exceptionSlot = slot<Exception>()
        val capturedMessages = mutableListOf<String>()
        val capturedExceptions = mutableListOf<Exception>()
        every {
            android.util.Log.e(
                eq("TVEpisodeDetailVM"),
                capture(messageSlot),
                capture(exceptionSlot),
            )
        } answers {
            capturedMessages.add(messageSlot.captured)
            capturedExceptions.add(exceptionSlot.captured)
            0
        }

        listOf(
            IllegalStateException("boom"),
            RuntimeException("kaboom"),
        ).forEach { throwable ->
            coEvery { enhancedPlaybackUtils.analyzePlaybackCapabilities(episode) } throws throwable

            viewModel.loadEpisodeAnalysis(episode)
            dispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.state.value.playbackAnalysis)
        }

        verify(exactly = 2) { android.util.Log.e(eq("TVEpisodeDetailVM"), any(), any()) }
        capturedMessages.forEach { message ->
            assertTrue(message.contains(episode.id.toString()))
        }
        assertEquals(listOf("boom", "kaboom"), capturedExceptions.map { it.message })
    }

    private fun createEpisode(): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        type = org.jellyfin.sdk.model.api.BaseItemKind.EPISODE,
    )

    private fun createPlaybackAnalysis(): PlaybackCapabilityAnalysis =
        PlaybackCapabilityAnalysis(
            canPlay = true,
            preferredMethod = com.rpeters.jellyfin.ui.utils.PlaybackMethod.DIRECT_PLAY,
            expectedQuality = "1080p",
            details = "Direct Play",
            codecs = "H264/AAC",
            container = "mp4",
            estimatedBandwidth = 10_000_000,
        )
}
