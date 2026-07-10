package com.rpeters.jellyfin.ui.player.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import org.json.JSONArray
import org.json.JSONObject
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

        val persistedState = AudioSessionStateStore(this).restore()

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(CMD_STOP_PLAYBACK)
                            .build(),
                    )
                    .setMediaButtonPreferences(buildMediaButtonPreferences(player))
                    .build()
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent,
            ): Boolean {
                val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                    ?: return super.onMediaButtonEvent(session, controllerInfo, intent)
                if (event.action != KeyEvent.ACTION_DOWN) return true
                return when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        handleTransportCommand(TransportCommand.Play)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        handleTransportCommand(TransportCommand.Pause)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        handleTransportCommand(TransportCommand.TogglePlayPause)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        handleTransportCommand(TransportCommand.SkipNext)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        handleTransportCommand(TransportCommand.SkipPrevious)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        handleTransportCommand(TransportCommand.Stop)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        handleTransportCommand(TransportCommand.SeekForward)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        handleTransportCommand(TransportCommand.SeekBackward)
                        true
                    }
                    else -> super.onMediaButtonEvent(session, controllerInfo, intent)
                }
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
        persistedState?.let(::restoreSessionState)
        syncSessionUiState()

        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    syncSessionUiState()
                    AudioSessionStateStore(this@AudioService).persist(player)
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Android 17 Hardening: Ensure we clean up when task is removed
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        AudioSessionStateStore(this).persist(player)
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
                handleTransportCommand(TransportCommand.Stop)
            }
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun handleTransportCommand(command: TransportCommand) {
        when (command) {
            TransportCommand.Play -> player.play()
            TransportCommand.Pause -> player.pause()
            TransportCommand.TogglePlayPause -> if (player.isPlaying) player.pause() else player.play()
            TransportCommand.SkipNext -> player.seekToNextMediaItem()
            TransportCommand.SkipPrevious -> player.seekToPreviousMediaItem()
            TransportCommand.SeekForward -> {
                val boundedPosition = if (player.duration == C.TIME_UNSET) {
                    player.currentPosition + SEEK_INTERVAL_MS
                } else {
                    (player.currentPosition + SEEK_INTERVAL_MS).coerceAtMost(player.duration)
                }
                player.seekTo(boundedPosition)
            }
            TransportCommand.SeekBackward -> player.seekTo((player.currentPosition - SEEK_INTERVAL_MS).coerceAtLeast(0))
            TransportCommand.Stop -> {
                player.pause()
                player.stop()
                player.clearMediaItems()
            }
        }
        syncSessionUiState()
    }

    private fun syncSessionUiState() {
        mediaSession?.setMediaButtonPreferences(buildMediaButtonPreferences(player))
        mediaSession?.setCustomLayout(buildMediaButtonPreferences(player))
        updateQueueMetadata()
    }

    private fun updateQueueMetadata() {
        val currentItem = player.currentMediaItem
        val queueSize = player.mediaItemCount
        val extras = Bundle().apply {
            putInt(EXTRA_QUEUE_SIZE, queueSize)
            putInt(EXTRA_QUEUE_INDEX, player.currentMediaItemIndex)
        }
        val playlistMetadata = MediaMetadata.Builder()
            .setTitle(currentItem?.mediaMetadata?.title ?: currentItem?.mediaMetadata?.displayTitle)
            .setArtist(currentItem?.mediaMetadata?.artist)
            .setAlbumTitle(currentItem?.mediaMetadata?.albumTitle)
            .setExtras(extras)
            .build()
        player.playlistMetadata = playlistMetadata
    }

    private fun restoreSessionState(state: PersistedAudioSessionState) {
        if (state.queue.isNotEmpty()) {
            player.setMediaItems(
                state.queue,
                state.currentIndex.coerceIn(0, state.queue.lastIndex),
                state.currentPositionMs.coerceAtLeast(0L),
            )
            player.repeatMode = state.repeatMode
            player.shuffleModeEnabled = state.shuffleEnabled
            player.playWhenReady = state.playWhenReady
            player.prepare()
        }
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

        val rewindButton = CommandButton.Builder(CommandButton.ICON_REWIND)
            .setDisplayName("Back 10s")
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .build()

        val forwardButton = CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("Forward 10s")
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .build()

        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName("Stop")
            .setSessionCommand(CMD_STOP_PLAYBACK)
            .build()

        return listOf(previousButton, rewindButton, playPauseButton, forwardButton, nextButton, stopButton)
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
        const val EXTRA_QUEUE_SIZE = "com.rpeters.jellyfin.audio.QUEUE_SIZE"
        const val EXTRA_QUEUE_INDEX = "com.rpeters.jellyfin.audio.QUEUE_INDEX"

        private const val SEEK_INTERVAL_MS = 10_000L
    }
}

interface AudioServiceForegroundIntentProvider {
    fun sessionActivityIntent(): PendingIntent?
}

private enum class TransportCommand {
    Play,
    Pause,
    TogglePlayPause,
    SkipNext,
    SkipPrevious,
    SeekForward,
    SeekBackward,
    Stop,
}

private data class PersistedAudioSessionState(
    val queue: List<MediaItem>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val playWhenReady: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

private class AudioSessionStateStore(
    private val service: AudioService,
) {
    private val prefs = service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun persist(player: Player) {
        val queuePayload = JSONArray().apply {
            repeat(player.mediaItemCount) { index ->
                put(mediaItemToJson(player.getMediaItemAt(index)))
            }
        }
        prefs.edit()
            .putString(KEY_QUEUE_JSON, queuePayload.toString())
            .putInt(KEY_INDEX, player.currentMediaItemIndex)
            .putLong(KEY_POSITION_MS, player.currentPosition.coerceAtLeast(0L))
            .putBoolean(KEY_PLAY_WHEN_READY, player.playWhenReady)
            .putBoolean(KEY_SHUFFLE_ENABLED, player.shuffleModeEnabled)
            .putInt(KEY_REPEAT_MODE, player.repeatMode)
            .apply()
    }

    fun restore(): PersistedAudioSessionState? {
        val queueJson = prefs.getString(KEY_QUEUE_JSON, null).orEmpty()
        if (queueJson.isBlank()) return null
        return runCatching {
            val queueArray = JSONArray(queueJson)
            val queue = buildList {
                repeat(queueArray.length()) { index ->
                    add(jsonToMediaItem(queueArray.getJSONObject(index)))
                }
            }
            PersistedAudioSessionState(
                queue = queue,
                currentIndex = prefs.getInt(KEY_INDEX, 0),
                currentPositionMs = prefs.getLong(KEY_POSITION_MS, 0L),
                playWhenReady = prefs.getBoolean(KEY_PLAY_WHEN_READY, false),
                shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false),
                repeatMode = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF),
            )
        }.getOrNull()
    }

    private fun mediaItemToJson(item: MediaItem): JSONObject {
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        return JSONObject().apply {
            put("mediaId", item.mediaId)
            put("uri", item.localConfiguration?.uri?.toString().orEmpty())
            put("title", metadata.title?.toString().orEmpty())
            put("artist", metadata.artist?.toString().orEmpty())
            put("albumTitle", metadata.albumTitle?.toString().orEmpty())
            put("artworkUri", metadata.artworkUri?.toString().orEmpty())
            put("streamUrl", extras?.getString(AudioService.EXTRA_STREAM_URL).orEmpty())
        }
    }

    private fun jsonToMediaItem(json: JSONObject): MediaItem {
        val extras = Bundle().apply {
            putString(AudioService.EXTRA_STREAM_URL, json.optString("streamUrl"))
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(json.optString("title"))
            .setArtist(json.optString("artist"))
            .setAlbumTitle(json.optString("albumTitle"))
            .setArtworkUri(json.optString("artworkUri").takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(json.optString("mediaId"))
            .setUri(json.optString("uri"))
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        private const val PREFS_NAME = "audio_service_state"
        private const val KEY_QUEUE_JSON = "queue_json"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_PLAY_WHEN_READY = "play_when_ready"
        private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode"
    }
}
