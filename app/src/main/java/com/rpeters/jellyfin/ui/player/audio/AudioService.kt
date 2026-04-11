package com.rpeters.jellyfin.ui.player.audio

import android.app.PendingIntent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class AudioService : androidx.media3.session.MediaSessionService() {

    @Inject
    lateinit var foregroundIntentProvider: AudioServiceForegroundIntentProvider

    private var mediaSession: MediaSession? = null
    private var notificationProvider: DefaultMediaNotificationProvider? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
            }

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(CMD_STOP_PLAYBACK)
                            .build(),
                    )
                    .setMediaButtonPreferences(buildMediaButtonPreferences(player))
                    .build()
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
            ) = Futures.immediateFuture(mediaItems.map(::resolveMediaItem))

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ) = Futures.immediateFuture(handleCustomCommand(customCommand))
        }
        val sessionBuilder = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setId(SESSION_ID)

        foregroundIntentProvider.sessionActivityIntent()?.let(sessionBuilder::setSessionActivity)

        mediaSession = sessionBuilder.build()
        mediaSession?.setMediaButtonPreferences(buildMediaButtonPreferences(player))

        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    mediaSession?.setMediaButtonPreferences(buildMediaButtonPreferences(player))
                }
            },
        )

        notificationProvider = AudioNotificationProvider(this).apply {
            // Use a valid monochrome small icon resource
            setSmallIcon(com.rpeters.jellyfin.R.drawable.ic_launcher_monochrome)
        }

        notificationProvider?.let { provider ->
            setMediaNotificationProvider(provider)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationProvider = null
        mediaSession?.release()
        mediaSession = null
        player.release()
    }

    private fun resolveMediaItem(item: MediaItem): MediaItem {
        val extras = item.mediaMetadata.extras ?: Bundle.EMPTY
        val streamUrl = extras.getString(EXTRA_STREAM_URL)
        val artworkUri = extras.getString(EXTRA_ARTWORK_URI)
        val metadataBuilder = item.mediaMetadata.buildUpon()

        if (!artworkUri.isNullOrBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(artworkUri))
        }

        val builder = item.buildUpon()
            .setMediaMetadata(metadataBuilder.build())

        if (!streamUrl.isNullOrBlank()) {
            builder.setUri(Uri.parse(streamUrl))
        }

        return builder.build()
    }

    private fun handleCustomCommand(customCommand: SessionCommand): SessionResult {
        when (customCommand.customAction) {
            ACTION_STOP_PLAYBACK -> {
                player.pause()
                player.stop()
                player.clearMediaItems()
                mediaSession?.setMediaButtonPreferences(buildMediaButtonPreferences(player))
            }
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun buildMediaButtonPreferences(player: Player): List<CommandButton> {
        val previousButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Previous")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

        val playPauseButton = CommandButton.Builder(
            if (player.isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY,
        )
            .setDisplayName(if (player.isPlaying) "Pause" else "Play")
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build()

        val nextButton = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Next")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName("Stop")
            .setSessionCommand(CMD_STOP_PLAYBACK)
            .build()

        return listOf(previousButton, playPauseButton, nextButton, stopButton)
    }

    internal fun currentSession(): MediaSession? = mediaSession

    internal fun currentNotificationProvider(): DefaultMediaNotificationProvider? = notificationProvider

    companion object {
        private const val ACTION_STOP_PLAYBACK = "com.rpeters.jellyfin.audio.STOP_PLAYBACK"

        private val CMD_STOP_PLAYBACK = SessionCommand(ACTION_STOP_PLAYBACK, Bundle.EMPTY)

        const val SESSION_ID = "JellyfinAudioSession"

        const val EXTRA_STREAM_URL = "com.rpeters.jellyfin.audio.STREAM_URL"
        const val EXTRA_ARTWORK_URI = "com.rpeters.jellyfin.audio.ARTWORK_URI"
        const val EXTRA_ITEM_ID = "com.rpeters.jellyfin.audio.ITEM_ID"
        const val EXTRA_ITEM_NAME = "com.rpeters.jellyfin.audio.ITEM_NAME"
        const val EXTRA_ALBUM_NAME = "com.rpeters.jellyfin.audio.ALBUM_NAME"
        const val EXTRA_ARTIST_NAME = "com.rpeters.jellyfin.audio.ARTIST_NAME"
        const val EXTRA_DURATION = "com.rpeters.jellyfin.audio.DURATION"
    }
}

interface AudioServiceForegroundIntentProvider {
    fun sessionActivityIntent(): PendingIntent?
}
