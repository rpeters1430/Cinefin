package com.rpeters.jellyfin.ui.player

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class VideoPlayerMetadataManagerTest {

    private lateinit var repository: IJellyfinRepository
    private lateinit var stateManager: VideoPlayerStateManager
    private lateinit var metadataManager: VideoPlayerMetadataManager

    private val itemId = UUID.randomUUID().toString()

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        stateManager = VideoPlayerStateManager()
        metadataManager = VideoPlayerMetadataManager(repository, stateManager)
    }

    private fun buildEpisodeWithChapters(chapters: List<ChapterInfo>): BaseItemDto =
        BaseItemDto(
            id = UUID.fromString(itemId),
            name = "Test Episode",
            type = BaseItemKind.EPISODE,
            chapters = chapters,
        )

    private fun buildChapter(name: String, startTicks: Long): ChapterInfo =
        ChapterInfo(
            startPositionTicks = startTicks,
            name = name,
            imageDateModified = LocalDateTime.now(),
        )

    @Test
    fun `loadSkipMarkers uses MediaSegments when available and ignores chapter heuristic`() = runTest {
        // Given
        val item = buildEpisodeWithChapters(
            listOf(
                buildChapter("Intro", 0L),
                buildChapter("Main Content", 300_000_000L) // 30s
            )
        )
        coEvery { repository.getEpisodeDetails(itemId) } returns ApiResult.Success(item)
        coEvery { repository.getMediaSegments(itemId) } returns ApiResult.Success(
            listOf(
                MediaSegmentDto(
                    id = UUID.randomUUID(),
                    itemId = UUID.fromString(itemId),
                    type = MediaSegmentType.INTRO,
                    startTicks = 50_000_000L, // 5s (deliberately different from the chapter 0s)
                    endTicks = 850_000_000L, // 85s
                ),
                MediaSegmentDto(
                    id = UUID.randomUUID(),
                    itemId = UUID.fromString(itemId),
                    type = MediaSegmentType.OUTRO,
                    startTicks = 12_000_000_000L, // 1200s
                    endTicks = 13_000_000_000L, // 1300s
                )
            )
        )

        // When
        metadataManager.loadSkipMarkers(itemId)

        // Then
        val state = stateManager.playerState.value
        assertEquals(5_000L, state.introStartMs)
        assertEquals(85_000L, state.introEndMs)
        assertEquals(1_200_000L, state.outroStartMs)
        assertEquals(1_300_000L, state.outroEndMs)
    }

    @Test
    fun `loadSkipMarkers falls back to chapter heuristic when MediaSegments is empty`() = runTest {
        // Given
        val item = buildEpisodeWithChapters(
            listOf(
                buildChapter("Intro", 0L),
                buildChapter("Credits", 300_000_000L) // 30s
            )
        )
        coEvery { repository.getEpisodeDetails(itemId) } returns ApiResult.Success(item)
        coEvery { repository.getMediaSegments(itemId) } returns ApiResult.Success(emptyList())

        // When
        metadataManager.loadSkipMarkers(itemId)

        // Then
        val state = stateManager.playerState.value
        assertEquals(0L, state.introStartMs)
        assertEquals(30_000L, state.introEndMs)
        assertEquals(30_000L, state.outroStartMs)
        assertNull(state.outroEndMs)
    }

    @Test
    fun `loadSkipMarkers falls back to chapter heuristic when MediaSegments errors`() = runTest {
        // Given
        val item = buildEpisodeWithChapters(
            listOf(
                buildChapter("Intro", 0L),
                buildChapter("Main Content", 500_000_000L) // 50s
            )
        )
        coEvery { repository.getEpisodeDetails(itemId) } returns ApiResult.Success(item)
        coEvery { repository.getMediaSegments(itemId) } returns ApiResult.Error("API failed")

        // When
        metadataManager.loadSkipMarkers(itemId)

        // Then
        val state = stateManager.playerState.value
        assertEquals(0L, state.introStartMs)
        assertEquals(50_000L, state.introEndMs)
        assertNull(state.outroStartMs)
        assertNull(state.outroEndMs)
    }

    @Test
    fun `loadSkipMarkers partial segments merges with chapter fallback`() = runTest {
        // Given
        val item = buildEpisodeWithChapters(
            listOf(
                buildChapter("Intro", 0L),
                buildChapter("Credits", 10_000_000_000L) // 1000s
            )
        )
        coEvery { repository.getEpisodeDetails(itemId) } returns ApiResult.Success(item)
        
        // MediaSegments only provides INTRO, not OUTRO
        coEvery { repository.getMediaSegments(itemId) } returns ApiResult.Success(
            listOf(
                MediaSegmentDto(
                    id = UUID.randomUUID(),
                    itemId = UUID.fromString(itemId),
                    type = MediaSegmentType.INTRO,
                    startTicks = 100_000_000L, // 10s
                    endTicks = 900_000_000L, // 90s
                )
            )
        )

        // When
        metadataManager.loadSkipMarkers(itemId)

        // Then
        val state = stateManager.playerState.value
        // Intro should come from segments API
        assertEquals(10_000L, state.introStartMs)
        assertEquals(90_000L, state.introEndMs)
        // Outro should be fallback from chapters (since segments API had no outro segment)
        assertEquals(1_000_000L, state.outroStartMs)
        assertNull(state.outroEndMs)
    }
}
