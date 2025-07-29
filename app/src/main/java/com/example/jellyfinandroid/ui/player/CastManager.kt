package com.example.jellyfinandroid.ui.player

import com.example.jellyfinandroid.BuildConfig
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
data class CastState(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isCasting: Boolean = false,
    val castPlayer: CastPlayer? = null
)

@UnstableApi
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context
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
                isCasting = true
            )
        }
        
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session ended")
            }
            updateCastState(
                isConnected = false,
                deviceName = null,
                isCasting = false
            )
        }
        
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (BuildConfig.DEBUG) {
                Log.d("CastManager", "Cast session resumed")
            }
            updateCastState(
                isConnected = true,
                deviceName = session.castDevice?.friendlyName,
                isCasting = true
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
                castPlayer = castPlayer
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
        castPlayer: CastPlayer? = _castState.value.castPlayer
    ) {
        _castState.value = CastState(
            isAvailable = isAvailable,
            isConnected = isConnected,
            deviceName = deviceName,
            isCasting = isCasting,
            castPlayer = castPlayer
        )
    }
    
    private fun buildImageUrl(itemId: String, imageTag: String): String {
        // This would need to be updated to use the actual Jellyfin server URL
        // For now, return a placeholder
        return "https://example.com/jellyfin/Items/$itemId/Images/Primary?tag=$imageTag"
    }
}