package com.rpeters.jellyfin.ui.player.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAudioServiceForegroundIntentProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioServiceForegroundIntentProvider {

    override fun sessionActivityIntent(): PendingIntent? {
        val intent = Intent(context, com.rpeters.jellyfin.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(REQUEST_CODE, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    companion object {
        private const val REQUEST_CODE = 91
    }
}
