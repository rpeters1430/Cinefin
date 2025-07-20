package com.example.jellyfinandroid.ui.player

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions

@androidx.media3.common.util.UnstableApi
class CastOptionsProvider : OptionsProvider {
    
    companion object {
        // Jellyfin Default Cast Receiver ID - can be customized for specific Jellyfin setups
        private const val CAST_RECEIVER_APP_ID = "CC1AD845" // Default Media Receiver
    }
    
    override fun getCastOptions(context: Context): CastOptions {
        // Build notification options for Cast media controls
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(VideoPlayerActivity::class.java.name)
            .build()
        
        // Build Cast media options
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(VideoPlayerActivity::class.java.name)
            .build()
        
        // Build and return Cast options
        return CastOptions.Builder()
            .setReceiverApplicationId(CAST_RECEIVER_APP_ID)
            .setCastMediaOptions(mediaOptions)
            .setEnableReconnectionService(true)
            .setResumeSavedSession(true)
            .build()
    }
    
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}