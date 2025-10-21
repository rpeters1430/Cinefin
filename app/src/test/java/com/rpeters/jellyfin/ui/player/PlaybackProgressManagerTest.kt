package com.rpeters.jellyfin.ui.player

import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressManagerTest {

    private val repository: JellyfinUserRepository = mockk()
    private lateinit var manager: PlaybackProgressManager

    @Before
    fun setUp() {
        manager = PlaybackProgressManager(repository)
    }

    @After
    fun tearDown() {
        clearMocks(repository)
    }

    @Test
    fun `updateProgress reports playback on interval`() = runTest {
        val itemId = "item123"
        val sessionId = "session"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)

        manager.startTracking(itemId, this, sessionId)
        advanceUntilIdle()

        manager.updateProgress(positionMs = 6_000L, durationMs = 20_000L)
        advanceUntilIdle()

        val expectedTicks = 6_000L * 10_000L
        coVerify { repository.reportPlaybackStart(itemId, sessionId, expectedTicks, null, false, false, true) }
        coVerify { repository.reportPlaybackProgress(itemId, sessionId, expectedTicks, null, false, false, true) }

        advanceTimeBy(10_000L)
        advanceUntilIdle()
        coVerify(exactly = 2) { repository.reportPlaybackProgress(itemId, sessionId, any(), any(), any(), any(), any()) }

        manager.stopTracking()
        advanceUntilIdle()
        coVerify { repository.reportPlaybackStopped(itemId, sessionId, any(), null, false) }
    }

    @Test
    fun `reportProgress error does not update last sync`() = runTest {
        val itemId = "itemError"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Error("boom")
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)

        manager.startTracking(itemId, this, "session")
        advanceUntilIdle()

        manager.updateProgress(positionMs = 6_000L, durationMs = 20_000L)
        advanceUntilIdle()

        assertEquals(0L, manager.playbackProgress.value.lastSyncTime)
        manager.stopTracking()
        advanceUntilIdle()
    }

    @Test
    fun `markAsWatched updates state on success`() = runTest {
        val itemId = "watched"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.markAsWatched(itemId) } returns ApiResult.Success(true)

        manager.startTracking(itemId, this, "session")
        advanceUntilIdle()

        manager.markAsWatched()
        advanceUntilIdle()

        assertTrue(manager.playbackProgress.value.isWatched)
        manager.stopTracking()
        advanceUntilIdle()
    }

    @Test
    fun `markAsUnwatched updates state on success`() = runTest {
        val itemId = "unwatched"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.markAsWatched(itemId) } returns ApiResult.Success(true)
        coEvery { repository.markAsUnwatched(itemId) } returns ApiResult.Success(true)

        manager.startTracking(itemId, this, "session")
        advanceUntilIdle()

        manager.markAsWatched()
        advanceUntilIdle()
        assertTrue(manager.playbackProgress.value.isWatched)

        manager.markAsUnwatched()
        advanceUntilIdle()
        assertFalse(manager.playbackProgress.value.isWatched)

        manager.stopTracking()
        advanceUntilIdle()
    }

    private fun userData(
        positionTicks: Long = 0L,
        played: Boolean = false,
        playedPercentage: Double? = null,
    ): UserItemDataDto = UserItemDataDto(
        playbackPositionTicks = positionTicks,
        playCount = 0,
        isFavorite = false,
        played = played,
        key = "key",
        itemId = UUID.randomUUID(),
        playedPercentage = playedPercentage,
    )
}
