package com.rpeters.jellyfin.ui.player

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PipActionReceiverTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `play pause action sends player command broadcast`() {
        val receiver = PipActionReceiver()
        shadowOf(application).clearBroadcastIntents()

        receiver.onReceive(application, Intent(PipActionReceiver.ACTION_PLAY_PAUSE))

        val broadcasts = shadowOf(application).broadcastIntents
        assertEquals(1, broadcasts.size)
        val broadcast = broadcasts.first()
        assertEquals(PipActionReceiver.ACTION_PLAYER_COMMAND, broadcast.action)
        assertEquals(
            PipActionReceiver.COMMAND_PLAY_PAUSE,
            broadcast.getStringExtra(PipActionReceiver.EXTRA_COMMAND),
        )
    }

    @Test
    fun `skip forward action sends player command broadcast`() {
        val receiver = PipActionReceiver()
        shadowOf(application).clearBroadcastIntents()

        receiver.onReceive(application, Intent(PipActionReceiver.ACTION_SKIP_FORWARD))

        val broadcasts = shadowOf(application).broadcastIntents
        assertEquals(1, broadcasts.size)
        val broadcast = broadcasts.first()
        assertEquals(PipActionReceiver.ACTION_PLAYER_COMMAND, broadcast.action)
        assertEquals(
            PipActionReceiver.COMMAND_SKIP_FORWARD,
            broadcast.getStringExtra(PipActionReceiver.EXTRA_COMMAND),
        )
    }

    @Test
    fun `skip backward action sends player command broadcast`() {
        val receiver = PipActionReceiver()
        shadowOf(application).clearBroadcastIntents()

        receiver.onReceive(application, Intent(PipActionReceiver.ACTION_SKIP_BACKWARD))

        val broadcasts = shadowOf(application).broadcastIntents
        assertEquals(1, broadcasts.size)
        val broadcast = broadcasts.first()
        assertEquals(PipActionReceiver.ACTION_PLAYER_COMMAND, broadcast.action)
        assertEquals(
            PipActionReceiver.COMMAND_SKIP_BACKWARD,
            broadcast.getStringExtra(PipActionReceiver.EXTRA_COMMAND),
        )
    }

    @Test
    fun `unknown action does not send broadcast`() {
        val receiver = PipActionReceiver()
        shadowOf(application).clearBroadcastIntents()

        receiver.onReceive(application, Intent("com.rpeters.jellyfin.UNKNOWN_ACTION"))

        val broadcasts = shadowOf(application).broadcastIntents
        assertNull(broadcasts.firstOrNull())
    }
}
