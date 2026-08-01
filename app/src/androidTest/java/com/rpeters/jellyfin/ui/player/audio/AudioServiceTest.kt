package com.rpeters.jellyfin.ui.player.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

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
        context.startService(Intent(context, AudioService::class.java))
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
        context.stopService(Intent(context, AudioService::class.java))
    }

    @Test
    fun availableCommands_includePlaybackAndShuffle() {
        context.startService(Intent(context, AudioService::class.java))
        val controller = createController()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val commands = controller.availableCommands
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_FORWARD))
        assertTrue(commands.contains(Player.COMMAND_SEEK_BACK))

        controller.shuffleModeEnabled = true
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(controller.shuffleModeEnabled)

        controller.release()
        context.stopService(Intent(context, AudioService::class.java))
    }

    @Test
    fun mediaButtonEvents_togglePlaybackThroughSessionPipeline_whenAppBackgrounded() {
        context.startService(Intent(context, AudioService::class.java))
        val controller = createController()
        controller.setMediaItem(testMediaItem("toggle"))
        controller.prepare()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        dispatchMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(controller.playWhenReady)

        dispatchMediaButton(KeyEvent.KEYCODE_MEDIA_PAUSE)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(!controller.playWhenReady)

        controller.release()
        context.stopService(Intent(context, AudioService::class.java))
    }

    private fun dispatchMediaButton(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent $keyCode")
            .close()
    }

    @Test
    fun queueAndSessionState_restoresAfterServiceRecreation_processDeathRegression() {
        context.startService(Intent(context, AudioService::class.java))
        val controller = createController()
        controller.setMediaItems(listOf(testMediaItem("one"), testMediaItem("two")), 1, 4_000L)
        controller.shuffleModeEnabled = true
        controller.repeatMode = Player.REPEAT_MODE_ALL
        controller.playWhenReady = false
        controller.prepare()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        controller.release()
        context.stopService(Intent(context, AudioService::class.java))

        context.startService(Intent(context, AudioService::class.java))
        val recreatedController = createController()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(2, recreatedController.mediaItemCount)
        assertEquals(1, recreatedController.currentMediaItemIndex)
        assertTrue(recreatedController.currentPosition >= 3_000L)
        assertTrue(recreatedController.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, recreatedController.repeatMode)
        assertTrue(!recreatedController.playWhenReady)

        recreatedController.release()
        context.stopService(Intent(context, AudioService::class.java))
    }

    @Test
    fun queueAndTransportCommands_availableAfterControllerRebind_deviceLockRegression() {
        context.startService(Intent(context, AudioService::class.java))
        val firstController = createController()
        firstController.setMediaItems(listOf(testMediaItem("one"), testMediaItem("two")))
        firstController.prepare()
        firstController.release()

        val reboundController = createController()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertEquals(2, reboundController.mediaItemCount)
        assertTrue(reboundController.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(reboundController.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))

        reboundController.release()
        context.stopService(Intent(context, AudioService::class.java))
    }

    private fun createController(): MediaController {
        val token = SessionToken(context, ComponentName(context, AudioService::class.java))
        return MediaController.Builder(context, token).buildAsync().get(5, TimeUnit.SECONDS)
    }

    private fun testMediaItem(id: String): MediaItem {
        val extras = Bundle().apply {
            putString(AudioService.EXTRA_STREAM_URL, "https://example.com/$id.mp3")
            putString(AudioService.EXTRA_ITEM_NAME, "Test Song $id")
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Song $id")
                    .setArtist("Artist")
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}
