package com.rpeters.jellyfin.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rpeters.jellyfin.utils.SecureLogger

/**
 * Broadcast receiver for handling Picture-in-Picture control actions.
 * Receives intents when user interacts with PiP controls (play/pause, skip, etc.)
 */
class PipActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        SecureLogger.d(TAG, "PiP action received: $action")

        when (action) {
            ACTION_PLAY_PAUSE -> {
                // Broadcast to active player
                sendPlayerCommand(context, COMMAND_PLAY_PAUSE)
            }
            ACTION_SKIP_FORWARD -> {
                sendPlayerCommand(context, COMMAND_SKIP_FORWARD)
            }
            ACTION_SKIP_BACKWARD -> {
                sendPlayerCommand(context, COMMAND_SKIP_BACKWARD)
            }
            else -> {
                SecureLogger.w(TAG, "Unknown PiP action: $action")
            }
        }
    }

    private fun sendPlayerCommand(context: Context, command: String) {
        val commandIntent = Intent(ACTION_PLAYER_COMMAND).apply {
            putExtra(EXTRA_COMMAND, command)
            setPackage(context.packageName)
        }
        context.sendBroadcast(commandIntent)
    }

    companion object {
        private const val TAG = "PipActionReceiver"

        // Action constants for PiP controls
        const val ACTION_PLAY_PAUSE = "com.rpeters.jellyfin.PIP_PLAY_PAUSE"
        const val ACTION_SKIP_FORWARD = "com.rpeters.jellyfin.PIP_SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.rpeters.jellyfin.PIP_SKIP_BACKWARD"

        // Internal commands
        const val ACTION_PLAYER_COMMAND = "com.rpeters.jellyfin.PLAYER_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val COMMAND_PLAY_PAUSE = "play_pause"
        const val COMMAND_SKIP_FORWARD = "skip_forward"
        const val COMMAND_SKIP_BACKWARD = "skip_backward"

        // Request codes for PendingIntents
        const val REQUEST_PLAY_PAUSE = 1001
        const val REQUEST_SKIP_FORWARD = 1002
        const val REQUEST_SKIP_BACKWARD = 1003
    }
}
