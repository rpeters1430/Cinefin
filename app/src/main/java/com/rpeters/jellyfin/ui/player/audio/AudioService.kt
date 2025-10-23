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
import androidx.media3.session.MediaSession
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
            .setContentType(androidx.media3.common.C.CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
            }

        val callback = object : MediaSession.Callback {}
        val sessionBuilder = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setId(SESSION_ID)

        foregroundIntentProvider.sessionActivityIntent()?.let(sessionBuilder::setSessionActivity)

        mediaSession = sessionBuilder.build()

        notificationProvider = DefaultMediaNotificationProvider(this).apply {
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

    internal fun currentSession(): MediaSession? = mediaSession

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
