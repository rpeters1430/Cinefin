package com.rpeters.jellyfin.ui.surface

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rpeters.jellyfin.ui.navigation.Screen
import com.rpeters.jellyfin.ui.surface.SurfaceLifecycleState.BACKGROUND
import com.rpeters.jellyfin.ui.surface.SurfaceLifecycleState.FOREGROUND
import com.rpeters.jellyfin.ui.surface.SurfaceMediaType.EPISODE
import com.rpeters.jellyfin.ui.surface.SurfaceMediaType.MOVIE
import com.rpeters.jellyfin.ui.surface.SurfaceMediaType.OTHER
import com.rpeters.jellyfin.ui.surface.components.NotificationSurfaceManager
import com.rpeters.jellyfin.ui.surface.components.QuickSettingsTileManager
import com.rpeters.jellyfin.ui.surface.components.ShortcutSurfaceManager
import com.rpeters.jellyfin.ui.surface.components.WidgetSurfaceManager
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Central orchestrator that translates domain state into Android surface updates.
 */
@Singleton
class ModernSurfaceCoordinator @Inject constructor(
    private val widgetSurfaceManager: WidgetSurfaceManager,
    private val shortcutSurfaceManager: ShortcutSurfaceManager,
    private val notificationSurfaceManager: NotificationSurfaceManager,
    private val quickSettingsTileManager: QuickSettingsTileManager,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val continueWatchingFlow = MutableStateFlow<List<SurfaceMediaItem>>(emptyList())
    private val nowPlayingFlow = MutableStateFlow<SurfaceNowPlaying?>(null)
    private val downloadFlow = MutableStateFlow<List<SurfaceDownloadSummary>>(emptyList())
    private val notificationsFlow = MutableStateFlow<List<SurfaceNotification>>(emptyList())
    private val lifecycleStateFlow = MutableStateFlow(BACKGROUND)
    private val refreshSignals = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val snapshotState = MutableStateFlow(ModernSurfaceSnapshot())
    val snapshot: StateFlow<ModernSurfaceSnapshot> = snapshotState.asStateFlow()

    private val initialized = AtomicBoolean(false)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            combine(
                continueWatchingFlow,
                nowPlayingFlow,
                downloadFlow,
                notificationsFlow,
                lifecycleStateFlow,
            ) { continueWatching, nowPlaying, downloads, notifications, lifecycle ->
                ModernSurfaceSnapshot(
                    continueWatching = continueWatching,
                    nowPlaying = nowPlaying,
                    downloads = downloads,
                    notifications = notifications,
                    lifecycleState = lifecycle,
                )
            }
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    snapshotState.value = snapshot
                    dispatchSnapshot(snapshot)
                }
        }

        scope.launch {
            refreshSignals.collectLatest {
                dispatchSnapshot(snapshotState.value)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        lifecycleStateFlow.value = FOREGROUND
    }

    override fun onStop(owner: LifecycleOwner) {
        lifecycleStateFlow.value = BACKGROUND
    }

    fun initialize() {
        if (initialized.compareAndSet(false, true)) {
            SecureLogger.i(TAG, "ModernSurfaceCoordinator initialized")
            refreshAll()
        }
    }

    fun updateContinueWatching(items: List<BaseItemDto>) {
        val sanitized = items.mapNotNull { it.toSurfaceMediaItem() }
            .filter { it.type == MOVIE || it.type == EPISODE }
            .take(CONTINUE_WATCHING_LIMIT)
        if (continueWatchingFlow.value != sanitized) {
            continueWatchingFlow.value = sanitized
        }
    }

    fun updateNowPlaying(state: SurfaceNowPlaying?) {
        if (nowPlayingFlow.value != state) {
            nowPlayingFlow.value = state
        }
    }

    fun updateDownloads(downloads: List<SurfaceDownloadSummary>) {
        val sanitized = downloads.distinctBy { it.id }
        if (downloadFlow.value != sanitized) {
            downloadFlow.value = sanitized
        }
    }

    fun updateNotifications(notifications: List<SurfaceNotification>) {
        val sanitized = notifications.distinctBy { it.id }
        if (notificationsFlow.value != sanitized) {
            notificationsFlow.value = sanitized
        }
    }

    fun refreshAll() {
        refreshSignals.tryEmit(Unit)
    }

    private suspend fun dispatchSnapshot(snapshot: ModernSurfaceSnapshot) {
        coroutineScope {
            launch { safeDispatch("widget") { widgetSurfaceManager.updateWidgets(snapshot) } }
            launch { safeDispatch("shortcuts") { shortcutSurfaceManager.updateShortcuts(snapshot) } }
            launch { safeDispatch("notifications") { notificationSurfaceManager.updateNotifications(snapshot) } }
            launch { safeDispatch("quick settings tile") { quickSettingsTileManager.updateQuickSettings(snapshot) } }
        }
    }

    private suspend fun safeDispatch(component: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { throwable ->
            if (throwable !is CancellationException) {
                SecureLogger.e(TAG, "Failed to update $component surface", throwable)
            }
        }
    }

    private fun BaseItemDto.toSurfaceMediaItem(): SurfaceMediaItem? {
        val idValue = id?.toString() ?: return null
        val titleValue = name?.takeIf { it.isNotBlank() } ?: return null
        val mediaType = when (type) {
            BaseItemKind.MOVIE -> MOVIE
            BaseItemKind.EPISODE -> EPISODE
            else -> OTHER
        }
        val route = when (mediaType) {
            MOVIE -> Screen.MovieDetail.createRoute(idValue)
            EPISODE -> Screen.TVEpisodeDetail.createRoute(idValue)
            OTHER -> Screen.ItemDetail.createRoute(idValue)
        }
        val episodeMetadata = if (mediaType == EPISODE) {
            SurfaceEpisodeMetadata(
                seasonNumber = parentIndexNumber,
                episodeNumber = indexNumber,
            )
        } else {
            null
        }
        val playbackProgress = userData?.let { data ->
            SurfacePlaybackProgress(
                percentage = data.playedPercentage,
                positionMs = data.playbackPositionTicks?.div(TICKS_TO_MILLIS_DIVISOR),
            )
        }

        return SurfaceMediaItem(
            id = idValue,
            title = titleValue,
            navigationRoute = route,
            type = mediaType,
            seriesName = seriesName,
            episodeMetadata = episodeMetadata,
            playbackProgress = playbackProgress,
        )
    }

    companion object {
        private const val TAG = "ModernSurfaceCoordinator"
        private const val CONTINUE_WATCHING_LIMIT = 8
        private const val TICKS_TO_MILLIS_DIVISOR = 10_000L
    }
}
