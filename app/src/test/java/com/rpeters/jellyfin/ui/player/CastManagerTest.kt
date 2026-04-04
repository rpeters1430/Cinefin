package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import com.rpeters.jellyfin.ui.player.cast.CastDiscoveryController
import com.rpeters.jellyfin.ui.player.cast.CastMediaLoadBuilder
import com.rpeters.jellyfin.ui.player.cast.CastPlaybackController
import com.rpeters.jellyfin.ui.player.cast.CastSessionController
import com.rpeters.jellyfin.ui.player.cast.CastState
import com.rpeters.jellyfin.ui.player.cast.CastStateStore
import com.rpeters.jellyfin.ui.player.dlna.DlnaDevice
import com.rpeters.jellyfin.ui.player.dlna.DlnaPlaybackController
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class, UnstableApi::class)
class CastManagerTest {

    @MockK
    lateinit var context: Context

    @MockK
    lateinit var stateStore: CastStateStore

    @MockK(relaxUnitFun = true)
    lateinit var discoveryController: CastDiscoveryController

    @MockK(relaxUnitFun = true)
    lateinit var sessionController: CastSessionController

    @MockK(relaxUnitFun = true)
    lateinit var playbackController: CastPlaybackController

    @MockK(relaxUnitFun = true)
    lateinit var mediaLoadBuilder: CastMediaLoadBuilder

    @MockK(relaxUnitFun = true)
    lateinit var dlnaPlaybackController: DlnaPlaybackController

    @MockK(relaxUnitFun = true)
    lateinit var remoteConfigRepository: RemoteConfigRepository

    private lateinit var castManager: CastManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val castStateFlow = MutableStateFlow(CastState())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        every { stateStore.castState } returns castStateFlow
        every { stateStore.update(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val updater = invocation.args[0] as (CastState) -> CastState
            castStateFlow.value = updater(castStateFlow.value)
        }
        every { stateStore.setError(any()) } answers {
            castStateFlow.value = castStateFlow.value.copy(error = invocation.args[0] as String?)
        }
        every { stateStore.clearError() } answers {
            castStateFlow.value = castStateFlow.value.copy(error = null)
        }

        castManager = CastManager(
            context,
            stateStore,
            discoveryController,
            sessionController,
            playbackController,
            mediaLoadBuilder,
            dlnaPlaybackController,
            remoteConfigRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `castState exposes state from stateStore`() {
        assertEquals(castStateFlow.value, castManager.castState.value)
    }

    @Test
    fun `stopCasting delegates to playbackController`() {
        castManager.stopCasting()
        verify { playbackController.stop(any()) }
    }

    @Test
    fun `pauseCasting delegates to playbackController`() {
        castManager.pauseCasting()
        verify { playbackController.pause(any()) }
    }

    @Test
    fun `resumeCasting delegates to playbackController`() {
        castManager.resumeCasting()
        verify { playbackController.resume(any()) }
    }

    @Test
    fun `seekTo delegates to playbackController`() {
        val position = 1000L
        castManager.seekTo(position)
        verify { playbackController.seekTo(any(), position) }
    }

    @Test
    fun `startCasting invokes error callback when DLNA load fails`() {
        val dlnaDevice = DlnaDevice(
            friendlyName = "Living Room TV",
            udn = "udn-1",
            locationUrl = "http://example/device.xml",
            avTransportControlUrl = "http://example/control",
        )
        val playbackData = CastMediaLoadBuilder.PlaybackData(
            url = "http://example/video.mp4",
            mimeType = "video/mp4",
            playSessionId = "session",
            mediaSourceId = "source",
            urlType = "transcode",
        )
        val item = io.mockk.mockk<org.jellyfin.sdk.model.api.BaseItemDto>(relaxed = true)
        val mediaItem = io.mockk.mockk<androidx.media3.common.MediaItem>(relaxed = true)
        var errorReceived = false

        every { discoveryController.findDlnaDevice("DLNA: Living Room TV") } returns dlnaDevice
        every { item.id } returns UUID.randomUUID()
        every { item.name } returns "Test Video"
        coEvery {
            mediaLoadBuilder.resolvePlaybackData(
                itemId = any(),
                castContext = any(),
                forceTranscode = true,
            )
        } returns playbackData
        coEvery {
            dlnaPlaybackController.loadAndPlay(
                device = dlnaDevice,
                streamUrl = playbackData.url,
                title = "Test Video",
            )
        } returns false

        assertTrue(castManager.connectToDevice("DLNA: Living Room TV"))
        castManager.startCasting(
            mediaItem = mediaItem,
            item = item,
            onError = { errorReceived = true },
        )

        verify(timeout = 1000) { stateStore.update(any()) }
        assertTrue(errorReceived)
    }
}
