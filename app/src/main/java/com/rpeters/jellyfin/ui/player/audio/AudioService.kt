package com.rpeters.jellyfin.ui.player.audio

import android.app.PendingIntent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibrarySession
import androidx.media3.session.MediaNotification
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class AudioService : MediaLibraryService() {

    @Inject
    lateinit var foregroundIntentProvider: AudioServiceForegroundIntentProvider

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var notificationProvider: DefaultMediaNotificationProvider? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
            }

        val callback = JellyfinMediaLibrarySessionCallback()
        val sessionBuilder = MediaLibrarySession.Builder(this, player, callback)
            .setId(SESSION_ID)

        foregroundIntentProvider.sessionActivityIntent()?.let(sessionBuilder::setSessionActivity)

        mediaLibrarySession = sessionBuilder.build()

        notificationProvider = DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(com.rpeters.jellyfin.R.drawable.ic_launcher_foreground)
            setNotificationListener(object : MediaNotification.Provider.Listener {
                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    if (dismissedByUser) {
                        stopSelf()
                    }
                }

                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: android.app.Notification,
                    ongoing: Boolean,
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    }
                }
            })
        }

        notificationProvider?.let { provider ->
            setMediaNotificationProvider(provider)
        }
    }

    override fun onGetSession(controllerInfo: MediaLibraryService.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationProvider = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player.release()
    }

    private fun resolveMediaItem(item: MediaItem): MediaItem {
        val extras = item.mediaMetadata.extras ?: Bundle.EMPTY
        val streamUrl = extras.getString(EXTRA_STREAM_URL) ?: item.localConfiguration?.uri?.toString()
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

    private inner class JellyfinMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaLibrarySession,
            controller: MediaLibraryService.ControllerInfo,
        ): MediaLibrarySession.ConnectionResult {
            val sessionCommands = SessionCommands.Builder()
                .addAll(SessionCommands.DEFAULT_SESSION_COMMANDS)
                .add(SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_MEDIA_URI))
                .build()

            val playerCommands = Player.Commands.Builder()
                .addAll(Player.Commands.DEFAULT)
                .add(Player.COMMAND_SET_SHUFFLE_MODE_ENABLED)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .build()

            return MediaLibrarySession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onSetMediaItems(
            session: MediaLibrarySession,
            controller: MediaLibraryService.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): MediaLibrarySession.MediaItemsWithStartPosition {
            val resolved = mediaItems.map(::resolveMediaItem)
            return MediaLibrarySession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            session: MediaLibrarySession,
            controller: MediaLibraryService.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): MutableList<MediaItem> {
            return mediaItems.map(::resolveMediaItem).toMutableList()
        }
    }

    internal fun currentSession(): MediaLibrarySession? = mediaLibrarySession

    internal fun currentNotificationProvider(): DefaultMediaNotificationProvider? = notificationProvider

    companion object {
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
