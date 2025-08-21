package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.rpeters.jellyfin.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata

@UnstableApi
data class CastState(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isCasting: Boolean = false,
    val castPlayer: CastPlayer? = null,
)

@UnstableApi
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session started: $sessionId")
            }
            updateCastState(
                isConnected = true,
                deviceName = session.castDevice?.friendlyName,
                isCasting = true,
            )
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session ended")
            }
            updateCastState(
                isConnected = false,
                deviceName = null,
                isCasting = false,
            )
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session resumed")
            }
            updateCastState(
                isConnected = true,
                deviceName = session.castDevice?.friendlyName,
                isCasting = true,
            )
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session suspended")
            }
            updateCastState(isCasting = false)
        }

        override fun onSessionStarting(session: CastSession) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session starting")
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.e("CastManager", "Cast session start failed: $error")
        }

        override fun onSessionEnding(session: CastSession) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session ending")
            }
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session resuming: $sessionId")
            }
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.e("CastManager", "Cast session resume failed: $error")
        }
    }

    private val sessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session available")
            }
            updateCastState(isAvailable = true)
        }

        override fun onCastSessionUnavailable() {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session unavailable")
            }
            updateCastState(isAvailable = false)
        }
    }

    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context).apply {
                sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
            }

            castContext?.let { context ->
                castPlayer = CastPlayer(context).apply {
                    setSessionAvailabilityListener(sessionAvailabilityListener)
                }
            }

            updateCastState(
                isAvailable = castContext?.sessionManager?.currentCastSession != null,
                castPlayer = castPlayer,
            )

            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast manager initialized successfully")
            }
        } catch (e: Exception) {
            Log.e("CastManager", "Failed to initialize Cast", e)
        }
    }

    fun startCasting(mediaItem: MediaItem, item: BaseItemDto) {
        try {
            val castSession = castContext?.sessionManager?.currentCastSession
            if (castSession?.isConnected == true) {
                // Build Cast media metadata
                val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(CastMediaMetadata.KEY_TITLE, item.name ?: "Unknown Title")
                    putString(CastMediaMetadata.KEY_SUBTITLE, item.overview ?: "")

                    // Add artwork if available - simplified for compatibility
                    val imageUrl = buildImageUrl(item.id.toString(), "primary")
                    addImage(WebImage(imageUrl.toUri()))
                }

                // Build media info
                val mediaInfo = MediaInfo.Builder(mediaItem.localConfiguration?.uri.toString())
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("video/mp4") // Default content type
                    .setMetadata(metadata)
                    .build()

                // Load media on Cast device
                val request = MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .build()

                castSession.remoteMediaClient?.load(request)

                if (BuildConfig.DEBUG) {
                    Log.d("CastManager", "Started casting: ${item.name}")
                }
            } else {
                Log.w("CastManager", "No active Cast session")
            }
        } catch (e: Exception) {
            Log.e("CastManager", "Failed to start casting", e)
        }
    }

    /**
     * Loads a non-playing preview (artwork + metadata) for the given item onto the Cast device.
     * Shows the largest available image (backdrop preferred over poster) without starting playback.
     */
    fun loadPreview(item: BaseItemDto, imageUrl: String?, backdropUrl: String?) {
        try {
            val castSession = castContext?.sessionManager?.currentCastSession
            if (castSession?.isConnected != true) {
                if (BuildConfig.DEBUG) {
                    Log.d("CastManager", "loadPreview: No active Cast session")
                }
                return
            }

            val primaryImage = imageUrl
            val backdropImage = backdropUrl
            val largestImageUrl = backdropImage ?: primaryImage
            if (largestImageUrl.isNullOrBlank()) {
                if (BuildConfig.DEBUG) {
                    Log.d("CastManager", "loadPreview: No image available for preview")
                }
                return
            }

            // Derive a basic content type from extension (default to image/jpeg)
            val lowered = largestImageUrl.lowercase()
            val contentType = when {
                lowered.endsWith(".png") -> "image/png"
                lowered.endsWith(".webp") -> "image/webp"
                lowered.endsWith(".gif") -> "image/gif"
                else -> "image/jpeg"
            }

            val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(CastMediaMetadata.KEY_TITLE, item.name ?: "")
                // Prefer shorter subtitle: Year + Runtime or Series/Episode info if TV
                val subtitle = buildString {
                    item.productionYear?.let { append(it) }
                    val runtime = item.runTimeTicks?.let { ticks ->
                        // Ticks to minutes (10,000,000 ticks per second)
                        val totalSeconds = ticks / 10_000_000
                        val minutes = totalSeconds / 60
                        if (minutes > 0) "${minutes}m" else null
                    }
                    if (!runtime.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(runtime)
                    }
                    if (isEmpty()) {
                        // Fallback to overview first sentence
                        item.overview?.split('.')?.firstOrNull()?.take(80)?.let { append(it.trim()) }
                    }
                }
                if (subtitle.isNotBlank()) {
                    putString(CastMediaMetadata.KEY_SUBTITLE, subtitle)
                }

                // Attach images (backdrop first for better display)
                backdropImage?.let { addImage(WebImage(it.toUri())) }
                primaryImage?.let { addImage(WebImage(it.toUri())) }
            }

            val mediaInfo = MediaInfo.Builder(largestImageUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_NONE)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build()

            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(false) // preview only
                .build()

            castSession.remoteMediaClient?.load(request)

            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "loadPreview: Sent preview for ${item.name}")
            }
        } catch (e: Exception) {
            Log.e("CastManager", "loadPreview: Failed to send preview", e)
        }
    }

    fun stopCasting() {
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Stopped casting")
            }
        } catch (e: Exception) {
            Log.e("CastManager", "Failed to stop casting", e)
        }
    }

    fun getCastPlayer(): Player? {
        return castPlayer
    }

    fun release() {
        try {
            castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
            castPlayer?.setSessionAvailabilityListener(null)
            castPlayer?.release()
            castPlayer = null

            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast manager released")
            }
        } catch (e: Exception) {
            Log.e("CastManager", "Error releasing Cast manager", e)
        }
    }

    private fun updateCastState(
        isAvailable: Boolean = _castState.value.isAvailable,
        isConnected: Boolean = _castState.value.isConnected,
        deviceName: String? = _castState.value.deviceName,
        isCasting: Boolean = _castState.value.isCasting,
        castPlayer: CastPlayer? = _castState.value.castPlayer,
    ) {
        _castState.value = CastState(
            isAvailable = isAvailable,
            isConnected = isConnected,
            deviceName = deviceName,
            isCasting = isCasting,
            castPlayer = castPlayer,
        )
    }

    private fun buildImageUrl(itemId: String, imageTag: String): String {
        // This would need to be updated to use the actual Jellyfin server URL
        // For now, return a placeholder
        return "https://example.com/jellyfin/Items/$itemId/Images/Primary?tag=$imageTag"
    }
}
