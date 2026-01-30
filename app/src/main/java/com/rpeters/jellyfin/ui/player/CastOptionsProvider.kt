package com.rpeters.jellyfin.ui.player

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

@androidx.media3.common.util.UnstableApi
class CastOptionsProvider : OptionsProvider {

    companion object {
        // Official Jellyfin Cast Receiver IDs:
        // - F007D354: Jellyfin Stable Receiver (production use)
        // - 6F511C87: Jellyfin Unstable/Beta Receiver (testing)
        // - CC1AD845: Google Default Media Receiver (fallback)
        //
        // Using Google Default Media Receiver for simplicity and compatibility
        // Sends direct stream URLs with auth tokens instead of custom protocol
        const val DEFAULT_CAST_RECEIVER_APP_ID: String = "CC1AD845" // Google Default Media Receiver

        @JvmStatic
        fun getReceiverApplicationId(): String = DEFAULT_CAST_RECEIVER_APP_ID
    }

    override fun getCastOptions(context: Context): CastOptions {
        // Build notification options for Cast media controls
        val notificationOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(VideoPlayerActivity::class.java.name)
            .build()

        // Build Cast media options
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            // .setExpandedControllerActivityClassName(VideoPlayerActivity::class.java.name)
            .build()

        // Build and return Cast options
        // Note: Disabled auto-resume to prevent unwanted automatic casting
        // Cast will only start when user explicitly clicks the Cast button
        return CastOptions.Builder()
            .setReceiverApplicationId(getReceiverApplicationId())
            .setCastMediaOptions(mediaOptions)
            .setEnableReconnectionService(false) // Disable auto-reconnection
            .setResumeSavedSession(false) // Disable auto-resume of previous sessions
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
