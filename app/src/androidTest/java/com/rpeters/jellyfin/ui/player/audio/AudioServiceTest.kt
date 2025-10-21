package com.rpeters.jellyfin.ui.player.audio

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ServiceScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioServiceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun setMediaItem_resolvesStreamUrlFromExtras() {
        ServiceScenario.launch(AudioService::class.java).use {
            val controller = createController()
            val streamUrl = "https://example.com/audio.mp3"
            val extras = Bundle().apply {
                putString(AudioService.EXTRA_STREAM_URL, streamUrl)
                putString(AudioService.EXTRA_ITEM_NAME, "Test Song")
            }

            val metadata = MediaMetadata.Builder()
                .setTitle("Test Song")
                .setArtist("Example Artist")
                .setExtras(extras)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId("test-song")
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val resolvedItem = controller.currentMediaItem
            requireNotNull(resolvedItem)

            assertEquals(streamUrl, resolvedItem.mediaMetadata.extras?.getString(AudioService.EXTRA_STREAM_URL))
            assertEquals(streamUrl, resolvedItem.localConfiguration?.uri?.toString())

            controller.release()
        }
    }

    @Test
    fun availableCommands_includePlaybackAndShuffle() {
        ServiceScenario.launch(AudioService::class.java).use {
            val controller = createController()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val commands = controller.availableCommands
            assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
            assertTrue(commands.contains(Player.COMMAND_SET_SHUFFLE_MODE_ENABLED))

            controller.setShuffleModeEnabled(true)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(controller.shuffleModeEnabled)

            controller.release()
        }
    }

    private fun createController(): MediaController {
        val token = SessionToken(context, ComponentName(context, AudioService::class.java))
        return MediaController.Builder(context, token).buildAsync().get(5, TimeUnit.SECONDS)
    }
}

