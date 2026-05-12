package com.rpeters.jellyfin.ui.player

import android.content.Context
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.network.ConnectivityChecker
import com.rpeters.jellyfin.data.offline.OfflineDownloadManager
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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

    private val context: Context = mockk(relaxed = true)
    private val connectivityChecker: ConnectivityChecker = mockk(relaxed = true)
    private val repository: JellyfinUserRepository = mockk()
    private val offlineDownloadManager: OfflineDownloadManager = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var manager: PlaybackProgressManager

    @Before
    fun setUp() {
        manager = PlaybackProgressManager(context, connectivityChecker, repository, offlineDownloadManager)
    }

    @After
    fun tearDown() {
        clearMocks(repository)
    }

    @Test
    fun `updateProgress reports playback on interval`() = runTest(testDispatcher) {
        val itemId = "item123"
        val sessionId = "session"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)

        manager.startTracking(itemId, this, sessionId)
        runCurrent()

        manager.updateProgress(positionMs = 6_000L, durationMs = 20_000L)
        runCurrent()

        val expectedTicks = 6_000L * 10_000L
        coVerify { repository.reportPlaybackStart(itemId, sessionId, expectedTicks, null, org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY, false, false, true) }
        coVerify { repository.reportPlaybackProgress(itemId, sessionId, expectedTicks, null, org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY, false, false, true) }

        advanceTimeBy(10_000L)
        runCurrent()
        coVerify(exactly = 2) { repository.reportPlaybackProgress(itemId, sessionId, any(), any(), any(), any(), any()) }

        manager.stopTracking()
        runCurrent()
        coVerify { repository.reportPlaybackStopped(itemId, sessionId, any(), null, false) }
    }

    @Test
    fun `reportProgress error does not update last sync`() = runTest(testDispatcher) {
        val itemId = "itemError"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Error("boom")
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)

        manager.startTracking(itemId, this, "session")
        runCurrent()

        manager.updateProgress(positionMs = 6_000L, durationMs = 20_000L)
        runCurrent()

        assertEquals(0L, manager.playbackProgress.value.lastSyncTime)
        manager.stopTracking()
        runCurrent()
    }

    @Test
    fun `markAsWatched updates state on success`() = runTest(testDispatcher) {
        val itemId = "watched"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.markAsWatched(itemId) } returns ApiResult.Success(true)

        manager.startTracking(itemId, this, "session")
        runCurrent()

        manager.markAsWatched()
        runCurrent()

        assertTrue(manager.playbackProgress.value.isWatched)
        manager.stopTracking()
        runCurrent()
    }

    @Test
    fun `markAsUnwatched updates state on success`() = runTest(testDispatcher) {
        val itemId = "unwatched"
        coEvery { repository.getItemUserData(itemId) } returns ApiResult.Success(userData())
        coEvery { repository.reportPlaybackStart(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackProgress(any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.reportPlaybackStopped(any(), any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        coEvery { repository.markAsWatched(itemId) } returns ApiResult.Success(true)
        coEvery { repository.markAsUnwatched(itemId) } returns ApiResult.Success(true)

        manager.startTracking(itemId, this, "session")
        runCurrent()

        manager.markAsWatched()
        runCurrent()
        assertTrue(manager.playbackProgress.value.isWatched)

        manager.markAsUnwatched()
        runCurrent()
        assertFalse(manager.playbackProgress.value.isWatched)

        manager.stopTracking()
        runCurrent()
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
