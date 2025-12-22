package com.rpeters.jellyfin.ui.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.preferences.CastPreferencesRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.utils.AppResources
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata

@UnstableApi
data class CastState(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isCasting: Boolean = false,
    val isRemotePlaying: Boolean = false,
    val castPlayer: CastPlayer? = null,
)

@UnstableApi
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamRepository: JellyfinStreamRepository,
    private val castPreferencesRepository: CastPreferencesRepository,
) {

    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null

    // Single coroutine scope for this singleton, tied to the manager's lifecycle
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Store initialization job to prevent scope leak
    private var initializationJob: kotlinx.coroutines.Job? = null

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session started: $sessionId")
            }
            val deviceName = session.castDevice?.friendlyName
            updateCastState(
                isConnected = true,
                deviceName = deviceName,
                isCasting = true,
                isRemotePlaying = isRemotePlaying(),
            )

            // Persist cast session for auto-reconnect
            managerScope.launch {
                castPreferencesRepository.saveLastCastSession(deviceName, sessionId)
            }
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session ended")
            }
            updateCastState(
                isConnected = false,
                deviceName = null,
                isCasting = false,
                isRemotePlaying = false,
            )

            // Clear persisted cast session on explicit disconnect
            managerScope.launch {
                castPreferencesRepository.clearLastCastSession()
            }
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session resumed")
            }
            val deviceName = session.castDevice?.friendlyName
            updateCastState(
                isConnected = true,
                deviceName = deviceName,
                isCasting = true,
                isRemotePlaying = isRemotePlaying(),
            )

            // Update persisted cast session
            managerScope.launch {
                castPreferencesRepository.saveLastCastSession(deviceName, null)
            }
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session suspended")
            }
            updateCastState(
                isCasting = false,
                isRemotePlaying = false,
            )
        }

        override fun onSessionStarting(session: CastSession) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session starting")
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            SecureLogger.e("CastManager", "Cast session start failed: $error")
        }

        override fun onSessionEnding(session: CastSession) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session ending")
            }
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session resuming: $sessionId")
            }
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            SecureLogger.e("CastManager", "Cast session resume failed: $error")
        }
    }

    private val sessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session available")
            }
            updateCastState(
                isAvailable = true,
                isRemotePlaying = isRemotePlaying(),
            )
        }

        override fun onCastSessionUnavailable() {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session unavailable")
            }
            updateCastState(
                isAvailable = false,
                isRemotePlaying = false,
            )
        }
    }

    @Suppress("DEPRECATION")
    fun initialize() {
        // Cancel any existing initialization job to prevent duplicate listeners
        initializationJob?.cancel()

        // Cast framework requires main thread access for CastContext.getSharedInstance()
        // Use the managed scope to prevent scope leak
        initializationJob = managerScope.launch {
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
                    isRemotePlaying = isRemotePlaying(),
                    castPlayer = castPlayer,
                )

                if (BuildConfig.DEBUG) {
                    SecureLogger.d("CastManager", "Cast manager initialized successfully")
                }
            } catch (e: Exception) {
                SecureLogger.e("CastManager", "Failed to initialize Cast", e)
            }
        }
    }

    /**
     * Determine if the current Cast session is actively playing media.
     */
    private fun isRemotePlaying(): Boolean {
        val playerState = castContext
            ?.sessionManager
            ?.currentCastSession
            ?.remoteMediaClient
            ?.mediaStatus
            ?.playerState

        return when (playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> true
            else -> false
        }
    }

    /**
     * Discover available Cast devices on the local network.
     * Returns a list of friendly device names.
     */
    fun discoverDevices(): List<String> {
        val router = MediaRouter.getInstance(context)
        val selector = castContext?.mergedSelector
        return router.routes
            .filter { route -> selector?.matchesControlFilters(route.controlFilters) == true }
            .map { it.name }
    }

    /**
     * Connect to the specified Cast device by name.
     * Returns true if a matching device was found and selected.
     */
    fun connectToDevice(deviceName: String): Boolean {
        val router = MediaRouter.getInstance(context)
        val route = router.routes.firstOrNull { it.name == deviceName }
        return if (route != null) {
            router.selectRoute(route)
            true
        } else {
            false
        }
    }

    /**
     * Attempt to auto-reconnect to the last used Cast device.
     * This should be called during app initialization if auto-reconnect is enabled.
     * Only attempts reconnection if the last session is recent (within 24 hours).
     */
    suspend fun attemptAutoReconnect() {
        try {
            val preferences = castPreferencesRepository.castPreferencesFlow
                .firstOrNull() ?: return

            if (!preferences.autoReconnect || !preferences.rememberLastDevice) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.d("CastManager", "Auto-reconnect disabled")
                }
                return
            }

            val deviceName = preferences.lastDeviceName
            val timestamp = preferences.lastCastTimestamp

            if (deviceName == null || timestamp == null) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.d("CastManager", "No previous cast session to reconnect")
                }
                return
            }

            // Check if the session is recent enough to attempt reconnection
            val sessionAge = System.currentTimeMillis() - timestamp
            if (sessionAge > CastPreferencesRepository.MAX_SESSION_AGE_MS) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.d("CastManager", "Previous cast session too old, skipping auto-reconnect")
                }
                castPreferencesRepository.clearLastCastSession()
                return
            }

            // Attempt to reconnect
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Attempting auto-reconnect to: $deviceName")
            }

            val connected = connectToDevice(deviceName)
            if (!connected) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.d("CastManager", "Failed to find device for auto-reconnect: $deviceName")
                }
            }
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "Error during auto-reconnect", e)
        }
    }

    /**
     * Infer proper content type from URL for Cast receiver compatibility
     */
    private fun inferContentType(uri: String): String = when {
        uri.endsWith(".m3u8", ignoreCase = true) -> "application/x-mpegURL" // HLS
        uri.endsWith(".mpd", ignoreCase = true) -> "application/dash+xml" // DASH
        else -> "video/mp4" // default
    }

    /**
     * Convert SubtitleSpec to Cast MediaTrack
     */
    private fun SubtitleSpec.toCastTrack(id: Long): com.google.android.gms.cast.MediaTrack {
        val builder = com.google.android.gms.cast.MediaTrack.Builder(
            id,
            com.google.android.gms.cast.MediaTrack.TYPE_TEXT,
        )
            .setContentId(this.url)
            .setLanguage(this.language)
            .setName(this.label ?: this.language?.uppercase() ?: "Subtitles")

        // Map MIME type to Cast track subtype
        val subtype = when (this.mimeType) {
            androidx.media3.common.MimeTypes.TEXT_VTT -> com.google.android.gms.cast.MediaTrack.SUBTYPE_SUBTITLES
            androidx.media3.common.MimeTypes.APPLICATION_SUBRIP -> com.google.android.gms.cast.MediaTrack.SUBTYPE_SUBTITLES
            androidx.media3.common.MimeTypes.TEXT_SSA,
            androidx.media3.common.MimeTypes.APPLICATION_TTML,
            -> com.google.android.gms.cast.MediaTrack.SUBTYPE_CAPTIONS
            else -> com.google.android.gms.cast.MediaTrack.SUBTYPE_UNKNOWN
        }
        builder.setSubtype(subtype)
        return builder.build()
    }

    fun startCasting(mediaItem: MediaItem, item: BaseItemDto, sideLoadedSubs: List<SubtitleSpec> = emptyList()) {
        try {
            val castSession = castContext?.sessionManager?.currentCastSession
            if (castSession?.isConnected == true) {
                val mediaUrl = mediaItem.localConfiguration?.uri.toString()
                val contentType = inferContentType(mediaUrl)

                // Build Cast media metadata
                val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(
                        CastMediaMetadata.KEY_TITLE,
                        item.name ?: AppResources.getString(R.string.unknown),
                    )
                    putString(CastMediaMetadata.KEY_SUBTITLE, item.overview ?: "")
                    attachCastArtwork(item)
                }

                // Build subtitle tracks for Cast
                val tracks = sideLoadedSubs.mapIndexed { idx, sub ->
                    sub.toCastTrack(idx + 1L)
                }

                // Build media info with proper content type and tracks
                val mediaInfo = MediaInfo.Builder(mediaUrl)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(contentType)
                    .setMetadata(metadata)
                    .setMediaTracks(tracks)
                    .build()

                // Optional: Set default text track style
                val textTrackStyle = com.google.android.gms.cast.TextTrackStyle().apply {
                    foregroundColor = 0xFFFFFFFF.toInt()
                    backgroundColor = 0x00000000
                    edgeType = com.google.android.gms.cast.TextTrackStyle.EDGE_TYPE_OUTLINE
                    edgeColor = 0xFF000000.toInt()
                    fontScale = 1.0f
                }

                // Load media on Cast device
                val request = MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    // Uncomment to enable default text styling:
                    // .setTextTrackStyle(textTrackStyle)
                    .build()

                castSession.remoteMediaClient?.load(request)

                if (BuildConfig.DEBUG) {
                    SecureLogger.d(
                        "CastManager",
                        "Started casting: ${item.name} ($contentType) with ${tracks.size} subtitle tracks",
                    )
                }

                updateCastState(isCasting = true, isRemotePlaying = true)
            } else {
                SecureLogger.w("CastManager", "No active Cast session")
            }
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "Failed to start casting", e)
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
                    SecureLogger.d("CastManager", "loadPreview: No active Cast session")
                }
                return
            }

            val primaryImage = imageUrl
            val backdropImage = backdropUrl
            val largestImageUrl = backdropImage ?: primaryImage
            if (largestImageUrl.isNullOrBlank()) {
                if (BuildConfig.DEBUG) {
                    SecureLogger.d("CastManager", "loadPreview: No image available for preview")
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
                SecureLogger.d("CastManager", "loadPreview: Sent preview for ${item.name}")
            }

            updateCastState(isCasting = true, isRemotePlaying = false)
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "loadPreview: Failed to send preview", e)
        }
    }

    fun stopCasting() {
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Stopped casting")
            }
            updateCastState(isRemotePlaying = false, isCasting = false)
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "Failed to stop casting", e)
        }
    }

    fun pauseCasting() {
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.pause()
            updateCastState(isRemotePlaying = false)
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "Failed to pause casting", e)
        }
    }

    fun resumeCasting() {
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.play()
            updateCastState(isRemotePlaying = true)
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "Failed to resume casting", e)
        }
    }

    fun getCastPlayer(): Player? {
        return castPlayer
    }

    @Suppress("DEPRECATION")
    fun release() {
        try {
            // Cancel initialization job to prevent scope leak
            initializationJob?.cancel()
            initializationJob = null

            // Cancel the manager scope to clean up all coroutines
            managerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()

            castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
            castPlayer?.setSessionAvailabilityListener(null)
            castPlayer?.release()
            castPlayer = null

            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast manager released")
            }
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "Error releasing Cast manager", e)
        }
    }

    private fun updateCastState(
        isAvailable: Boolean = _castState.value.isAvailable,
        isConnected: Boolean = _castState.value.isConnected,
        deviceName: String? = _castState.value.deviceName,
        isCasting: Boolean = _castState.value.isCasting,
        isRemotePlaying: Boolean = _castState.value.isRemotePlaying,
        castPlayer: CastPlayer? = _castState.value.castPlayer,
    ) {
        _castState.value = CastState(
            isAvailable = isAvailable,
            isConnected = isConnected,
            deviceName = deviceName,
            isCasting = isCasting,
            isRemotePlaying = isRemotePlaying,
            castPlayer = castPlayer,
        )
    }

    private fun CastMediaMetadata.attachCastArtwork(item: BaseItemDto) {
        val backdropUrl = buildImageUrl(item, ImageType.BACKDROP)
        val primaryUrl = buildImageUrl(item, ImageType.PRIMARY)
        val added = mutableSetOf<String>()

        backdropUrl?.let { url ->
            if (added.add(url)) {
                addImageIfAvailable(url, ImageType.BACKDROP)
            }
        }
        primaryUrl?.let { url ->
            if (added.add(url)) {
                addImageIfAvailable(url, ImageType.PRIMARY)
            }
        }
    }

    private fun CastMediaMetadata.addImageIfAvailable(url: String?, imageType: ImageType) {
        if (url.isNullOrBlank()) {
            return
        }

        runCatching {
            addImage(WebImage(url.toUri()))
        }.onFailure { throwable ->
            SecureLogger.w(
                "CastManager",
                "Failed to attach ${imageType.name.lowercase(Locale.ROOT)} image to cast metadata",
                throwable,
            )
        }
    }

    private fun buildImageUrl(item: BaseItemDto, imageType: ImageType): String? {
        val itemId = item.id?.toString()
        if (itemId.isNullOrBlank()) {
            SecureLogger.w(
                "CastManager",
                "buildImageUrl: Item missing ID; cannot load ${imageType.name.lowercase(Locale.ROOT)} image",
            )
            return null
        }

        val url = when (imageType) {
            ImageType.BACKDROP -> streamRepository.getBackdropUrl(item)
            else -> {
                val tag = item.imageTags?.get(imageType)
                    ?: if (imageType == ImageType.PRIMARY) {
                        item.imageTags?.get(ImageType.PRIMARY)
                    } else {
                        null
                    }
                streamRepository.getImageUrl(itemId, imageType.toRequestType(), tag)
            }
        }

        if (url.isNullOrBlank()) {
            SecureLogger.w(
                "CastManager",
                "buildImageUrl: Unable to resolve ${imageType.name.lowercase(Locale.ROOT)} image for item $itemId",
            )
            return null
        }

        return url
    }

    private fun ImageType.toRequestType(): String {
        return name.lowercase(Locale.ROOT).replaceFirstChar { character ->
            if (character.isLowerCase()) {
                character.titlecase(Locale.ROOT)
            } else {
                character.toString()
            }
        }
    }
}
