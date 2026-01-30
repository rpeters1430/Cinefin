package com.rpeters.jellyfin.ui.player

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.preferences.CastPreferencesRepository
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.utils.AppResources
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata

@UnstableApi
data class CastState(
    val isInitialized: Boolean = false,
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isCasting: Boolean = false,
    val isRemotePlaying: Boolean = false,
    val error: String? = null,
    // Playback position tracking
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val volume: Float = 1.0f,
)

@UnstableApi
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamRepository: JellyfinStreamRepository,
    private val castPreferencesRepository: CastPreferencesRepository,
    private val authRepository: JellyfinAuthRepository,
    private val repository: JellyfinRepository,
) {

    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private var castContext: CastContext? = null
    private var sessionListenerAdded = false
    private val initializationLock = Any()
    private var initializationDeferred: CompletableDeferred<Boolean>? = null
    private var routeCallbackAdded = false
    private val routeCallback = object : MediaRouter.Callback() {}
    private var pendingSeekPosition: Long = -1L

    // Single coroutine scope for this singleton, tied to the manager's lifecycle
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val remoteMediaClientCallback = object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
            val status = client.mediaStatus ?: return

            if (pendingSeekPosition > 0L) {
                // Wait for BUFFERING, PLAYING or PAUSED state to ensure media is ready
                // BUFFERING state indicates the receiver has started loading the media
                when (status.playerState) {
                    MediaStatus.PLAYER_STATE_PLAYING,
                    MediaStatus.PLAYER_STATE_PAUSED,
                    MediaStatus.PLAYER_STATE_BUFFERING,
                    -> {
                        if (BuildConfig.DEBUG) {
                            SecureLogger.d("CastManager", "Media ready (state=${status.playerState}), seeking to pending position: ${pendingSeekPosition}ms")
                        }

                        val seekOptions = MediaSeekOptions.Builder()
                            .setPosition(pendingSeekPosition)
                            .setResumeState(MediaSeekOptions.RESUME_STATE_PLAY)
                            .build()

                        client.seek(seekOptions)
                        pendingSeekPosition = -1L
                    }
                    MediaStatus.PLAYER_STATE_IDLE -> {
                        // If idle with an error, clear pending seek
                        if (status.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                            if (BuildConfig.DEBUG) {
                                SecureLogger.d("CastManager", "Media idle with error, clearing pending seek")
                            }
                            pendingSeekPosition = -1L
                        }
                    }
                }
            }
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session started: $sessionId")
            }
            // Register callback for seek-on-load
            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)

            val deviceName = session.castDevice?.friendlyName
            _castState.update { state ->
                state.copy(
                    isConnected = true,
                    deviceName = deviceName,
                    isCasting = true,
                    isRemotePlaying = isRemotePlaying(),
                    error = null,
                )
            }

            // Persist cast session for auto-reconnect
            managerScope.launch {
                castPreferencesRepository.saveLastCastSession(deviceName, sessionId)
            }
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session ended")
            }
            session.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
            _castState.update { state ->
                state.copy(
                    isConnected = false,
                    deviceName = null,
                    isCasting = false,
                    isRemotePlaying = false,
                    error = null,
                )
            }

            // Clear persisted cast session on explicit disconnect
            managerScope.launch {
                castPreferencesRepository.clearLastCastSession()
            }
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session resumed")
            }
            // Register callback for seek-on-load
            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback)

            val deviceName = session.castDevice?.friendlyName
            _castState.update { state ->
                state.copy(
                    isConnected = true,
                    deviceName = deviceName,
                    isCasting = true,
                    isRemotePlaying = isRemotePlaying(),
                    error = null,
                )
            }

            // Update persisted cast session
            managerScope.launch {
                castPreferencesRepository.saveLastCastSession(deviceName, null)
            }
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session suspended")
            }
            session.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
            _castState.update { state ->
                state.copy(
                    isCasting = false,
                    isRemotePlaying = false,
                )
            }
        }

        override fun onSessionStarting(session: CastSession) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast session starting")
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            SecureLogger.e("CastManager", "Cast session start failed: $error")
            _castState.update { state ->
                state.copy(error = "Failed to start Cast session (error code: $error)")
            }
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
            // Don't show error to user - resume failures are expected when sessions expire
            // Just clear the saved session so we don't keep trying
            managerScope.launch {
                castPreferencesRepository.clearLastCastSession()
            }
            // Clear error state to avoid showing persistent error messages
            _castState.update { state ->
                state.copy(error = null)
            }
        }
    }

    fun initialize() {
        managerScope.launch {
            awaitInitialization()
        }
    }

    suspend fun awaitInitialization(): Boolean {
        if (castState.value.isInitialized) {
            return castState.value.isAvailable
        }

        val deferred = synchronized(initializationLock) {
            val updatedState = castState.value
            if (updatedState.isInitialized) {
                null
            } else {
                val existing = initializationDeferred
                if (existing != null && !existing.isCompleted) {
                    existing
                } else {
                    val created = CompletableDeferred<Boolean>()
                    initializationDeferred = created
                    startInitialization(created)
                    created
                }
            }
        }

        return deferred?.await() ?: castState.value.isAvailable
    }

    private fun startInitialization(deferred: CompletableDeferred<Boolean>) {
        try {
            // Use modern CastContext API with executor to avoid deprecation warning
            val contextTask = CastContext.getSharedInstance(
                context.applicationContext,
                Executors.newSingleThreadExecutor(),
            )
            contextTask.addOnSuccessListener { ctx ->
                castContext = ctx
                if (!sessionListenerAdded) {
                    ctx.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                    sessionListenerAdded = true
                }

                // Check if there's an existing session (e.g., from a previous app launch)
                val existingSession = ctx.sessionManager.currentCastSession
                val hasExistingSession = existingSession?.isConnected == true

                _castState.update { state ->
                    state.copy(
                        isInitialized = true,
                        isAvailable = true, // Cast Framework is available
                        isConnected = hasExistingSession,
                        deviceName = existingSession?.castDevice?.friendlyName,
                        isCasting = hasExistingSession,
                        isRemotePlaying = isRemotePlaying(),
                        error = null,
                    )
                }

                if (BuildConfig.DEBUG) {
                    SecureLogger.d("CastManager", "Cast manager initialized successfully (existing session: $hasExistingSession)")
                }
                deferred.complete(true)
            }
            contextTask.addOnFailureListener { e ->
                SecureLogger.e("CastManager", "Failed to initialize Cast context", e)
                _castState.update { state ->
                    state.copy(
                        isInitialized = true, // Mark as initialized even on failure so we don't keep retrying
                        isAvailable = false,
                        error = "Cast not available: ${e.message}",
                    )
                }
                deferred.complete(false)
            }
        } catch (e: CancellationException) {
            throw e
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
     * Returns empty list if Cast is not initialized yet.
     */
    fun discoverDevices(): List<String> {
        val ctx = castContext
        if (ctx == null) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "discoverDevices: Cast not initialized yet")
            }
            return emptyList()
        }

        val router = MediaRouter.getInstance(context)
        val selector = ctx.mergedSelector
        if (selector != null && !routeCallbackAdded) {
            router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            routeCallbackAdded = true
        }
        val devices = router.routes
            .filter { route -> selector?.matchesControlFilters(route.controlFilters) == true }
            .map { it.name }

        if (BuildConfig.DEBUG) {
            SecureLogger.d("CastManager", "discoverDevices: Found ${devices.size} devices")
        }
        return devices
    }

    /**
     * Connect to the specified Cast device by name.
     * Returns true if a matching device was found and selected.
     * Returns false if Cast is not initialized or device not found.
     */
    fun connectToDevice(deviceName: String): Boolean {
        if (castContext == null) {
            SecureLogger.w("CastManager", "connectToDevice: Cast not initialized yet, cannot connect to '$deviceName'")
            _castState.update { state ->
                state.copy(error = "Cast is still initializing, please try again")
            }
            return false
        }

        val router = MediaRouter.getInstance(context)
        val route = router.routes.firstOrNull { it.name == deviceName }
        return if (route != null) {
            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "connectToDevice: Selecting route '$deviceName'")
            }
            router.selectRoute(route)
            true
        } else {
            SecureLogger.w("CastManager", "connectToDevice: Device '$deviceName' not found")
            _castState.update { state ->
                state.copy(error = "Device '$deviceName' not found")
            }
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
            if (!awaitInitialization()) {
                return
            }
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
        } catch (e: CancellationException) {
            throw e
        }
    }

    private data class CastPlaybackData(
        val url: String,
        val mimeType: String,
        val playSessionId: String?,
    )

    /**
     * Get playback data for Cast using Jellyfin's PlaybackInfo API.
     * The server will decide whether to direct play or transcode based on the Cast device capabilities.
     */
    private suspend fun resolveCastPlaybackData(itemId: String): CastPlaybackData? {
        // FIX: Thread-Safe Snapshot of Device Info
        // We MUST access Cast SDK on Main thread, then move back to IO for network
        val deviceInfo = withContext(Dispatchers.Main) {
            val session = castContext?.sessionManager?.currentCastSession
            val device = session?.castDevice
            if (device != null) {
                Pair(device.modelName ?: "", device.friendlyName ?: "")
            } else {
                null
            }
        }

        return try {
            val (modelName, friendlyName) = deviceInfo ?: Pair("", "")

            val lowerModel = modelName.lowercase(Locale.ROOT)
            val lowerName = friendlyName.lowercase(Locale.ROOT)

            // Detect SHIELD or Android TV to allow Direct Play
            val isShieldOrAndroidTV = lowerModel.contains("shield") ||
                lowerName.contains("shield") ||
                lowerModel.contains("android tv") ||
                lowerModel.contains("chromecast with google tv")

            if (BuildConfig.DEBUG) {
                SecureLogger.d(
                    "CastManager",
                    "Cast device: model='$modelName' name='$friendlyName' isShield=$isShieldOrAndroidTV",
                )
            }

            // Get playback info from Jellyfin (This network call runs on IO thread)
            val playbackInfo = repository.getCastPlaybackInfo(itemId, isShieldOrAndroidTV)
            val mediaSource = playbackInfo.mediaSources.firstOrNull()

            if (mediaSource == null) {
                SecureLogger.e("CastManager", "No media sources returned from PlaybackInfo API")
                return null
            }

            // Get server URL
            val serverUrl = authRepository.getCurrentServer()?.url
            if (serverUrl.isNullOrBlank()) {
                SecureLogger.e("CastManager", "No server URL available")
                return null
            }

            // FIX: Force Stream URL for Direct Play
            // Never use mediaSource.path directly as it is a local file path which SHIELD cannot reach
            val streamPath = when {
                !mediaSource.transcodingUrl.isNullOrBlank() -> mediaSource.transcodingUrl!!
                // If direct play, build the static stream endpoint manually
                else -> "Videos/$itemId/stream?static=true"
            }

            // Build full URL
            val fullUrl = buildServerUrl(serverUrl, streamPath)

            // FIX: Override MIME type for compatibility
            val mimeType = if (!mediaSource.transcodingUrl.isNullOrBlank()) {
                // Transcoding - detect format from URL
                when {
                    streamPath.contains("master.m3u8", ignoreCase = true) ||
                        streamPath.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                    streamPath.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
                    streamPath.endsWith(".ts", ignoreCase = true) -> "video/mp2t"
                    else -> "application/x-mpegURL"
                }
            } else {
                // Direct play - Force 'video/mp4' for MKV to prevent Cast SDK rejection
                when (mediaSource.container?.lowercase(Locale.ROOT)) {
                    "ts", "m3u8", "hls" -> "application/x-mpegURL"
                    "mp4", "m4v" -> "video/mp4"
                    "webm" -> "video/webm"
                    "mkv" -> "video/mp4" // Override: Treat MKV as MP4 for Cast SDK
                    else -> "video/mp4"
                }
            }

            if (BuildConfig.DEBUG) {
                SecureLogger.d(
                    "CastManager",
                    "Cast playback resolved: container=${mediaSource.container} mimeType=$mimeType supportsTranscoding=${mediaSource.supportsTranscoding}",
                )
            }

            CastPlaybackData(
                url = fullUrl,
                mimeType = mimeType,
                playSessionId = playbackInfo.playSessionId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.e("CastManager", "Failed to resolve Cast playback data", e)
            null
        }
    }

    private fun buildServerUrl(serverUrl: String, path: String): String {
        val cleanServerUrl = serverUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return "$cleanServerUrl/$cleanPath"
    }

    /**
     * Append access token to URL for Cast receiver authentication.
     * Chromecast receivers cannot use custom headers, so we need to include
     * the auth token as a query parameter.
     */
    private fun addAuthTokenToUrl(url: String): String {
        val accessToken = authRepository.getCurrentServer()?.accessToken
        if (accessToken.isNullOrBlank()) {
            SecureLogger.w("CastManager", "addAuthTokenToUrl: No access token available")
            return url
        }

        // FIX: Check if api_key already exists to avoid duplication
        if (url.contains("api_key=")) {
            return url
        }

        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}api_key=$accessToken"
    }

    /**
     * Convert SubtitleSpec to Cast MediaTrack
     */
    private fun SubtitleSpec.toCastTrack(id: Long): com.google.android.gms.cast.MediaTrack {
        // Add authentication to subtitle URL for Cast receiver
        val authenticatedUrl = addAuthTokenToUrl(this.url)

        val builder = com.google.android.gms.cast.MediaTrack.Builder(
            id,
            com.google.android.gms.cast.MediaTrack.TYPE_TEXT,
        )
            .setContentId(authenticatedUrl)
            .setContentType(this.mimeType)
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

    fun startCasting(
        mediaItem: MediaItem,
        item: BaseItemDto,
        sideLoadedSubs: List<SubtitleSpec> = emptyList(),
        startPositionMs: Long = 0L,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
    ) {
        try {
            val castSession = castContext?.sessionManager?.currentCastSession
            if (castSession?.isConnected == true) {
                // Ensure callback is registered for seek-after-load pattern
                val remoteClient = castSession.remoteMediaClient
                if (remoteClient != null) {
                    remoteClient.unregisterCallback(remoteMediaClientCallback)
                    remoteClient.registerCallback(remoteMediaClientCallback)
                }

                val itemId = item.id.toString()

                // Launch coroutine to resolve playback data using PlaybackInfo API
                managerScope.launch {
                    val startTime = System.currentTimeMillis()

                    // Get playback data from Jellyfin
                    // Run on IO dispatcher to avoid blocking main thread
                    val playbackData = withContext(Dispatchers.IO) {
                        resolveCastPlaybackData(itemId)
                    }

                    if (playbackData == null) {
                        SecureLogger.e("CastManager", "Failed to resolve Cast playback data")
                        _castState.update { state ->
                            state.copy(error = "Failed to get stream URL")
                        }
                        return@launch
                    }

                    val playbackInfoTime = System.currentTimeMillis() - startTime
                    if (BuildConfig.DEBUG) {
                        SecureLogger.d("CastManager", "PlaybackInfo resolved in ${playbackInfoTime}ms")
                    }

                    // Add auth token to URL since Cast receivers can't use custom headers
                    val mediaUrl = addAuthTokenToUrl(playbackData.url)

                    // Determine stream type
                    val isLive = item.type == BaseItemKind.TV_CHANNEL || (item.runTimeTicks ?: 0L) <= 0L
                    val streamType = if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED

                    if (BuildConfig.DEBUG) {
                        SecureLogger.d(
                            "CastManager",
                            "Cast URL: $mediaUrl mimeType=${playbackData.mimeType} streamType=$streamType",
                        )
                    }

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

                    // Build media info for Google Default Media Receiver
                    val mediaInfo = MediaInfo.Builder(mediaUrl)
                        .setStreamType(streamType)
                        .setContentType(playbackData.mimeType)
                        .setMetadata(metadata)
                        .setMediaTracks(tracks)
                        .build()

                    // Set pending seek position
                    if (startPositionMs > 0L) {
                        pendingSeekPosition = startPositionMs
                    } else {
                        pendingSeekPosition = -1L
                    }

                    // Load media on Cast device
                    val request = MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .setAutoplay(true)
                        .build()

                    Log.i(
                        "CastManager",
                        "Cast load (PlaybackInfo): url=$mediaUrl mimeType=${playbackData.mimeType} streamType=$streamType startPos=$startPositionMs",
                    )

                    val loadStartTime = System.currentTimeMillis()
                    castSession.remoteMediaClient?.load(request)?.setResultCallback { result ->
                        val loadTime = System.currentTimeMillis() - loadStartTime
                        val totalTime = System.currentTimeMillis() - startTime
                        val status = result.status
                        Log.i("CastManager", "Cast load result: status=$status statusCode=${status.statusCode} (load took ${loadTime}ms, total ${totalTime}ms)")

                        if (status.isSuccess) {
                            val rmc = castSession.remoteMediaClient
                            val mediaStatus = rmc?.mediaStatus
                            val playerState = mediaStatus?.playerState
                            Log.i("CastManager", "Cast status after load: playerState=$playerState")

                            // If we have a pending seek and media is ready, seek immediately
                            if (pendingSeekPosition > 0L && mediaStatus != null) {
                                when (playerState) {
                                    MediaStatus.PLAYER_STATE_PLAYING,
                                    MediaStatus.PLAYER_STATE_PAUSED,
                                    MediaStatus.PLAYER_STATE_BUFFERING,
                                    -> {
                                        val seekOptions = MediaSeekOptions.Builder()
                                            .setPosition(pendingSeekPosition)
                                            .setResumeState(MediaSeekOptions.RESUME_STATE_PLAY)
                                            .build()
                                        rmc.seek(seekOptions)
                                        pendingSeekPosition = -1L
                                    }
                                }
                            }
                        } else {
                            SecureLogger.e("CastManager", "Cast load failed with status code: ${status.statusCode}")
                            _castState.update { state ->
                                state.copy(error = "Failed to cast media (error ${status.statusCode})")
                            }
                            pendingSeekPosition = -1L
                        }
                    }

                    _castState.update { state ->
                        state.copy(isCasting = true, isRemotePlaying = true, error = null)
                    }
                }
            } else {
                SecureLogger.w("CastManager", "No active Cast session")
                _castState.update { state ->
                    state.copy(error = "No active Cast session")
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Loads a non-playing preview (artwork + metadata) for the given item onto the Cast device.
     */
    fun loadPreview(item: BaseItemDto, imageUrl: String?, backdropUrl: String?) {
        try {
            val castSession = castContext?.sessionManager?.currentCastSession
            if (castSession?.isConnected != true) {
                return
            }

            val primaryImage = imageUrl?.let { addAuthTokenToUrl(it) }
            val backdropImage = backdropUrl?.let { addAuthTokenToUrl(it) }
            val largestImageUrl = backdropImage ?: primaryImage
            if (largestImageUrl.isNullOrBlank()) {
                return
            }

            val lowered = largestImageUrl.lowercase()
            val contentType = when {
                lowered.endsWith(".png") -> "image/png"
                lowered.endsWith(".webp") -> "image/webp"
                lowered.endsWith(".gif") -> "image/gif"
                else -> "image/jpeg"
            }

            val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(CastMediaMetadata.KEY_TITLE, item.name ?: "")
                val subtitle = buildString {
                    item.productionYear?.let { append(it) }
                    val runtime = item.runTimeTicks?.let { ticks ->
                        val totalSeconds = ticks / 10_000_000
                        val minutes = totalSeconds / 60
                        if (minutes > 0) "${minutes}m" else null
                    }
                    if (!runtime.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(runtime)
                    }
                }
                if (subtitle.isNotBlank()) {
                    putString(CastMediaMetadata.KEY_SUBTITLE, subtitle)
                }
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
                .setAutoplay(false)
                .build()

            castSession.remoteMediaClient?.load(request)

            _castState.update { state ->
                state.copy(isCasting = true, isRemotePlaying = false, error = null)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun stopCasting() {
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
            _castState.update { state ->
                state.copy(isRemotePlaying = false, isCasting = false, error = null)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun disconnectCastSession() {
        try {
            val sessionManager = castContext?.sessionManager
            val currentSession = sessionManager?.currentCastSession

            if (currentSession != null) {
                currentSession.remoteMediaClient?.stop()
                sessionManager.endCurrentSession(true)
            }

            _castState.update { state ->
                state.copy(
                    isRemotePlaying = false,
                    isCasting = false,
                    isConnected = false,
                    deviceName = null,
                    error = null,
                )
            }

            managerScope.launch {
                castPreferencesRepository.clearLastCastSession()
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun pauseCasting() {
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.pause()
            _castState.update { state ->
                state.copy(isRemotePlaying = false, error = null)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun resumeCasting() {
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.play()
            _castState.update { state ->
                state.copy(isRemotePlaying = true, error = null)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            val remoteClient = castContext?.sessionManager?.currentCastSession?.remoteMediaClient
            if (remoteClient != null) {
                val seekOptions = MediaSeekOptions.Builder()
                    .setPosition(positionMs)
                    .build()
                remoteClient.seek(seekOptions)
                _castState.update { state ->
                    state.copy(currentPosition = positionMs, error = null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun setVolume(volume: Float) {
        try {
            val castSession = castContext?.sessionManager?.currentCastSession
            if (castSession?.isConnected == true) {
                castSession.volume = volume.toDouble().coerceIn(0.0, 1.0)
                _castState.update { state ->
                    state.copy(volume = volume.coerceIn(0f, 1f), error = null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun getVolume(): Float {
        return try {
            castContext?.sessionManager?.currentCastSession?.volume?.toFloat() ?: 1.0f
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun getCurrentPosition(): Long {
        return try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.approximateStreamPosition ?: 0L
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun getDuration(): Long {
        return try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.mediaInfo?.streamDuration ?: 0L
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun updatePlaybackState() {
        val position = getCurrentPosition()
        val duration = getDuration()
        val volume = getVolume()
        val playing = isRemotePlaying()

        _castState.update { state ->
            state.copy(
                currentPosition = position,
                duration = duration,
                volume = volume,
                isRemotePlaying = playing,
            )
        }
    }

    fun release() {
        try {
            initializationDeferred?.cancel()
            initializationDeferred = null
            managerScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()

            castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
            sessionListenerAdded = false
            if (routeCallbackAdded) {
                MediaRouter.getInstance(context).removeCallback(routeCallback)
                routeCallbackAdded = false
            }
            castContext = null

            if (BuildConfig.DEBUG) {
                SecureLogger.d("CastManager", "Cast manager released")
            }
        } catch (e: CancellationException) {
            throw e
        }
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
        val authenticatedUrl = addAuthTokenToUrl(url)
        runCatching {
            addImage(WebImage(authenticatedUrl.toUri()))
        }.onFailure { throwable ->
            SecureLogger.w(
                "CastManager",
                "Failed to attach ${imageType.name.lowercase(Locale.ROOT)} image to cast metadata",
                throwable,
            )
        }
    }

    private fun buildImageUrl(item: BaseItemDto, imageType: ImageType): String? {
        val itemId = item.id.toString()

        val url = when (imageType) {
            ImageType.BACKDROP -> streamRepository.getBackdropUrl(item)
            else -> {
                if (imageType == ImageType.PRIMARY && item.type == BaseItemKind.EPISODE) {
                    streamRepository.getSeriesImageUrl(item)
                } else {
                    val tag = item.imageTags?.get(imageType)
                        ?: if (imageType == ImageType.PRIMARY) {
                            item.imageTags?.get(ImageType.PRIMARY)
                        } else {
                            null
                        }
                    streamRepository.getImageUrl(itemId, imageType.toRequestType(), tag)
                }
            }
        }

        if (url.isNullOrBlank()) {
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
