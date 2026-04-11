package com.rpeters.jellyfin.ui.player.audio

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

@UnstableApi
class AudioNotificationProvider(
    context: Context,
) : DefaultMediaNotificationProvider(context) {

    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
        return metadata.title ?: metadata.displayTitle
    }

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
        val artist = metadata.artist?.toString().orEmpty().trim()
        val album = metadata.albumTitle?.toString().orEmpty().trim()
        return when {
            artist.isNotEmpty() && album.isNotEmpty() -> "$artist \u2022 $album"
            artist.isNotEmpty() -> artist
            album.isNotEmpty() -> album
            else -> metadata.subtitle
        }
    }
}
