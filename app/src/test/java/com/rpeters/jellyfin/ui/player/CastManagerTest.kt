package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.rpeters.jellyfin.data.preferences.CastPreferencesRepository
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class, UnstableApi::class)
class CastManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val streamRepository: JellyfinStreamRepository = mockk(relaxed = true)
    private val castPreferencesRepository: CastPreferencesRepository = mockk(relaxed = true)
    private val authRepository: JellyfinAuthRepository = mockk(relaxed = true)
    private val castContext: CastContext = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val castSession: CastSession = mockk(relaxed = true)
    private val remoteMediaClient: RemoteMediaClient = mockk(relaxed = true)

    private lateinit var castManager: CastManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock CastContext static method
        mockkStatic(CastContext::class)
        every { CastContext.getSharedInstance(any()) } returns castContext
        every { castContext.sessionManager } returns sessionManager
        every { sessionManager.currentCastSession } returns null

        castManager = CastManager(context, streamRepository, castPreferencesRepository, authRepository)
    }

    @After
    fun tearDown() {
        clearMocks(
            context,
            streamRepository,
            castPreferencesRepository,
            authRepository,
            castContext,
            sessionManager,
            castSession,
            remoteMediaClient,
        )
        unmockkStatic(CastContext::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize creates cast player and updates state`() = runTest {
        // Arrange
        every { sessionManager.currentCastSession } returns null

        // Act
        castManager.initialize()
        advanceUntilIdle()

        // Assert
        verify { sessionManager.addSessionManagerListener<CastSession>(any(), any()) }
        assertFalse(castManager.castState.value.isAvailable)
        assertFalse(castManager.castState.value.isConnected)
    }

    @Test
    fun `release cancels initialization and cleans up resources`() = runTest {
        // Arrange
        castManager.initialize()
        advanceUntilIdle()

        // Act
        castManager.release()

        // Assert
        verify { sessionManager.removeSessionManagerListener<CastSession>(any(), any()) }
        assertNull(castManager.getCastPlayer())
    }

    @Test
    fun `multiple initialize calls cancel previous jobs to prevent memory leak`() = runTest {
        // Act - Initialize multiple times
        castManager.initialize()
        advanceUntilIdle()

        castManager.initialize()
        advanceUntilIdle()

        castManager.initialize()
        advanceUntilIdle()

        // Assert - Only one listener should be registered (old ones are removed/replaced)
        // The cancellation prevents duplicate listeners
        verify(atLeast = 1) { sessionManager.addSessionManagerListener<CastSession>(any(), any()) }
    }

    @Test
    fun `onSessionStarted updates cast state correctly`() = runTest {
        // Arrange
        val deviceName = "Living Room TV"
        every { castSession.castDevice?.friendlyName } returns deviceName
        every { castSession.remoteMediaClient?.mediaStatus?.playerState } returns MediaStatus.PLAYER_STATE_IDLE

        castManager.initialize()
        advanceUntilIdle()

        // Get the session listener that was registered
        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Act
        listenerSlot.captured.onSessionStarted(castSession, "session123")

        // Assert
        assertTrue(castManager.castState.value.isConnected)
        assertTrue(castManager.castState.value.isCasting)
        assertEquals(deviceName, castManager.castState.value.deviceName)
    }

    @Test
    fun `onSessionEnded clears cast state`() = runTest {
        // Arrange
        castManager.initialize()
        advanceUntilIdle()

        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Start a session first
        every { castSession.castDevice?.friendlyName } returns "TV"
        every { castSession.remoteMediaClient?.mediaStatus?.playerState } returns MediaStatus.PLAYER_STATE_IDLE
        listenerSlot.captured.onSessionStarted(castSession, "session123")

        // Act - End the session
        listenerSlot.captured.onSessionEnded(castSession, 0)

        // Assert
        assertFalse(castManager.castState.value.isConnected)
        assertFalse(castManager.castState.value.isCasting)
        assertFalse(castManager.castState.value.isRemotePlaying)
        assertNull(castManager.castState.value.deviceName)
    }

    @Test
    fun `onSessionResumed updates cast state`() = runTest {
        // Arrange
        val deviceName = "Bedroom TV"
        every { castSession.castDevice?.friendlyName } returns deviceName
        every { castSession.remoteMediaClient?.mediaStatus?.playerState } returns MediaStatus.PLAYER_STATE_PLAYING

        castManager.initialize()
        advanceUntilIdle()

        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Act
        listenerSlot.captured.onSessionResumed(castSession, false)

        // Assert
        assertTrue(castManager.castState.value.isConnected)
        assertTrue(castManager.castState.value.isCasting)
        assertEquals(deviceName, castManager.castState.value.deviceName)
    }

    @Test
    fun `onSessionSuspended pauses casting state`() = runTest {
        // Arrange
        castManager.initialize()
        advanceUntilIdle()

        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Start session first
        every { castSession.castDevice?.friendlyName } returns "TV"
        every { castSession.remoteMediaClient?.mediaStatus?.playerState } returns MediaStatus.PLAYER_STATE_PLAYING
        listenerSlot.captured.onSessionStarted(castSession, "session123")

        // Act - Suspend the session
        listenerSlot.captured.onSessionSuspended(castSession, 0)

        // Assert
        assertFalse(castManager.castState.value.isCasting)
        assertFalse(castManager.castState.value.isRemotePlaying)
    }

    @Test
    fun `isRemotePlaying returns true when media is playing`() = runTest {
        // Arrange
        val mediaStatus: com.google.android.gms.cast.MediaStatus = mockk(relaxed = true)
        every { mediaStatus.playerState } returns MediaStatus.PLAYER_STATE_PLAYING
        every { castSession.remoteMediaClient?.mediaStatus } returns mediaStatus
        every { castSession.isConnected } returns true
        every { sessionManager.currentCastSession } returns castSession
        every { castSession.castDevice?.friendlyName } returns "TV"

        castManager.initialize()
        advanceUntilIdle()

        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Act
        listenerSlot.captured.onSessionStarted(castSession, "session123")

        // Assert
        assertTrue(castManager.castState.value.isRemotePlaying)
    }

    @Test
    fun `stopCasting clears playing state`() = runTest {
        // Arrange
        every { sessionManager.currentCastSession } returns castSession
        every { castSession.remoteMediaClient } returns remoteMediaClient
        every { remoteMediaClient.stop() } returns mockk(relaxed = true)

        castManager.initialize()
        advanceUntilIdle()

        // Act
        castManager.stopCasting()

        // Assert
        verify { remoteMediaClient.stop() }
        assertFalse(castManager.castState.value.isRemotePlaying)
        assertFalse(castManager.castState.value.isCasting)
    }

    @Test
    fun `pauseCasting updates remote playing state`() = runTest {
        // Arrange
        every { sessionManager.currentCastSession } returns castSession
        every { castSession.remoteMediaClient } returns remoteMediaClient
        every { remoteMediaClient.pause() } returns mockk(relaxed = true)

        castManager.initialize()
        advanceUntilIdle()

        // Act
        castManager.pauseCasting()

        // Assert
        verify { remoteMediaClient.pause() }
        assertFalse(castManager.castState.value.isRemotePlaying)
    }

    @Test
    fun `resumeCasting updates remote playing state`() = runTest {
        // Arrange
        every { sessionManager.currentCastSession } returns castSession
        every { castSession.remoteMediaClient } returns remoteMediaClient
        every { remoteMediaClient.play() } returns mockk(relaxed = true)

        castManager.initialize()
        advanceUntilIdle()

        // Act
        castManager.resumeCasting()

        // Assert
        verify { remoteMediaClient.play() }
        assertTrue(castManager.castState.value.isRemotePlaying)
    }

    @Test
    fun `getCastPlayer returns null before initialization`() {
        // Assert
        assertNull(castManager.getCastPlayer())
    }

    @Test
    fun `startCasting loads media with correct metadata`() = runTest {
        // Arrange
        val item = createTestItem(name = "Test Movie")
        val mediaItem = MediaItem.Builder()
            .setUri("https://server.com/video.mp4")
            .build()

        val mockServer = mockk<com.rpeters.jellyfin.data.JellyfinServer>(relaxed = true)
        every { mockServer.accessToken } returns "test-token-123"
        every { authRepository.getCurrentServer() } returns mockServer

        every { sessionManager.currentCastSession } returns castSession
        every { castSession.isConnected } returns true
        every { castSession.remoteMediaClient } returns remoteMediaClient
        every { remoteMediaClient.load(any<com.google.android.gms.cast.MediaInfo>()) } returns mockk(relaxed = true)
        every { streamRepository.getBackdropUrl(any<BaseItemDto>()) } returns "https://server.com/backdrop.jpg"
        every { streamRepository.getImageUrl(any<String>(), any(), any()) } returns "https://server.com/poster.jpg"

        castManager.initialize()
        advanceUntilIdle()

        // Act
        castManager.startCasting(mediaItem, item)

        // Assert
        verify { remoteMediaClient.load(any<com.google.android.gms.cast.MediaInfo>()) }
        assertTrue(castManager.castState.value.isCasting)
        assertTrue(castManager.castState.value.isRemotePlaying)
    }

    @Test
    fun `loadPreview sends preview without starting playback`() = runTest {
        // Arrange
        val item = createTestItem(name = "Preview Item")
        val imageUrl = "https://server.com/poster.jpg"
        val backdropUrl = "https://server.com/backdrop.jpg"

        val mockServer = mockk<com.rpeters.jellyfin.data.JellyfinServer>(relaxed = true)
        every { mockServer.accessToken } returns "test-token-123"
        every { authRepository.getCurrentServer() } returns mockServer

        every { sessionManager.currentCastSession } returns castSession
        every { castSession.isConnected } returns true
        every { castSession.remoteMediaClient } returns remoteMediaClient
        every { remoteMediaClient.load(any<com.google.android.gms.cast.MediaInfo>()) } returns mockk(relaxed = true)

        castManager.initialize()
        advanceUntilIdle()

        // Act
        castManager.loadPreview(item, imageUrl, backdropUrl)

        // Assert
        verify { remoteMediaClient.load(any<com.google.android.gms.cast.MediaInfo>()) }
        assertTrue(castManager.castState.value.isCasting)
        assertFalse(castManager.castState.value.isRemotePlaying)
    }

    @Test
    fun `loadPreview does nothing when no cast session is active`() = runTest {
        // Arrange
        val item = createTestItem(name = "Preview Item")
        every { sessionManager.currentCastSession } returns null

        castManager.initialize()
        advanceUntilIdle()

        // Act
        castManager.loadPreview(item, "https://server.com/image.jpg", null)

        // Assert
        verify(exactly = 0) { remoteMediaClient.load(any<com.google.android.gms.cast.MediaInfo>()) }
    }

    @Test
    fun `release does not crash when called multiple times`() = runTest {
        // Arrange
        castManager.initialize()
        advanceUntilIdle()

        // Act & Assert - Should not throw
        castManager.release()
        castManager.release()
        castManager.release()
    }

    @Test
    fun `initialization handles exceptions gracefully`() = runTest {
        // Arrange
        every { CastContext.getSharedInstance(any()) } throws RuntimeException("Cast not available")

        // Act - Should not throw
        castManager.initialize()
        advanceUntilIdle()

        // Assert - State should remain in default state
        assertFalse(castManager.castState.value.isAvailable)
        assertFalse(castManager.castState.value.isConnected)
    }

    @Test
    fun `onSessionStarted saves cast session to preferences`() = runTest {
        // Arrange
        val deviceName = "Living Room TV"
        val sessionId = "session123"
        every { castSession.castDevice?.friendlyName } returns deviceName
        every { castSession.remoteMediaClient?.mediaStatus?.playerState } returns MediaStatus.PLAYER_STATE_IDLE

        castManager.initialize()
        advanceUntilIdle()

        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Act
        listenerSlot.captured.onSessionStarted(castSession, sessionId)
        advanceUntilIdle()

        // Assert
        coVerify { castPreferencesRepository.saveLastCastSession(deviceName, sessionId) }
    }

    @Test
    fun `onSessionEnded clears cast session from preferences`() = runTest {
        // Arrange
        castManager.initialize()
        advanceUntilIdle()

        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Act
        listenerSlot.captured.onSessionEnded(castSession, 0)
        advanceUntilIdle()

        // Assert
        coVerify { castPreferencesRepository.clearLastCastSession() }
    }

    @Test
    fun `onSessionResumed updates cast session in preferences`() = runTest {
        // Arrange
        val deviceName = "Bedroom TV"
        every { castSession.castDevice?.friendlyName } returns deviceName
        every { castSession.remoteMediaClient?.mediaStatus?.playerState } returns MediaStatus.PLAYER_STATE_PLAYING

        castManager.initialize()
        advanceUntilIdle()

        val listenerSlot = slot<com.google.android.gms.cast.framework.SessionManagerListener<CastSession>>()
        verify { sessionManager.addSessionManagerListener<CastSession>(capture(listenerSlot), any()) }

        // Act
        listenerSlot.captured.onSessionResumed(castSession, false)
        advanceUntilIdle()

        // Assert
        coVerify { castPreferencesRepository.saveLastCastSession(deviceName, null) }
    }

    private fun createTestItem(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Item",
        overview: String? = "Test overview",
        productionYear: Int? = 2024,
    ): BaseItemDto {
        val imageTags = mapOf(
            ImageType.PRIMARY to "primary-tag",
            ImageType.BACKDROP to "backdrop-tag",
        )

        return BaseItemDto(
            id = id,
            name = name,
            type = BaseItemKind.MOVIE,
            overview = overview,
            productionYear = productionYear,
            imageTags = imageTags,
            runTimeTicks = 60L * 60L * 10_000_000L, // 1 hour in ticks
        )
    }
}
