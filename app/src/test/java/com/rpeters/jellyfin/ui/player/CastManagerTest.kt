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
import io.mockk.MockKAnnotations
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
import org.junit.Before
import org.junit.Test

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
    lateinit var remoteConfigRepository: RemoteConfigRepository

    private lateinit var castManager: CastManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val castStateFlow = MutableStateFlow(CastState())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        every { stateStore.castState } returns castStateFlow

        castManager = CastManager(
            context,
            stateStore,
            discoveryController,
            sessionController,
            playbackController,
            mediaLoadBuilder,
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
}
