package com.rpeters.jellyfin.ui.player.cast

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.rpeters.jellyfin.data.preferences.CastPreferences
import com.rpeters.jellyfin.data.preferences.CastPreferencesRepository
import com.rpeters.jellyfin.ui.player.dlna.DlnaDevice
import com.rpeters.jellyfin.ui.player.dlna.DlnaDiscoveryController
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the asynchronous discovery of Cast devices on the network.
 */
@UnstableApi
@Singleton
class CastDiscoveryController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateStore: CastStateStore,
    private val castPreferencesRepository: CastPreferencesRepository,
    private val dlnaDiscoveryController: DlnaDiscoveryController,
) {
    private var routeCallbackAdded = false
    private var discoveryJob: Job? = null
    private var castDevices: List<String> = emptyList()
    private var dlnaDevices: List<String> = emptyList()
    private var currentSelector: MediaRouteSelector? = null
    private var preferredDeviceName: String? = null
    private var autoReconnectEnabled: Boolean = false
    private var autoReconnectAttempted: Boolean = false
    private var publishDiscoveryState: Boolean = true

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateDiscoveredDevices(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateDiscoveredDevices(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateDiscoveredDevices(router)
        }
    }

    companion object {
        private const val DISCOVERY_TIMEOUT_MS = 15_000L

        @JvmStatic
        internal fun prioritizedDevices(devices: List<String>, preferredDeviceName: String?): List<String> {
            if (preferredDeviceName.isNullOrBlank()) return devices
            return devices.sortedWith(
                compareByDescending<String> { it == preferredDeviceName }
                    .thenBy { it.lowercase() },
            )
        }

        @JvmStatic
        internal fun shouldAutoReconnect(
            preferredDeviceName: String?,
            autoReconnectEnabled: Boolean,
            autoReconnectAttempted: Boolean,
            devices: List<String>,
        ): Boolean {
            return !preferredDeviceName.isNullOrBlank() &&
                autoReconnectEnabled &&
                !autoReconnectAttempted &&
                devices.contains(preferredDeviceName)
        }

        @JvmStatic
        internal fun rememberedDeviceName(prefs: CastPreferences, nowMs: Long): String? {
            val lastCastTimestamp = prefs.lastCastTimestamp ?: return null
            val sessionIsFresh = nowMs - lastCastTimestamp <= CastPreferencesRepository.MAX_SESSION_AGE_MS
            if (!prefs.rememberLastDevice || !sessionIsFresh) return null
            return prefs.lastDeviceName?.takeIf { it.isNotBlank() }
        }

        @JvmStatic
        internal fun autoReconnectTarget(prefs: CastPreferences, nowMs: Long): String? {
            val rememberedDevice = rememberedDeviceName(prefs, nowMs) ?: return null
            return rememberedDevice.takeIf { prefs.autoReconnect }
        }
    }

    /**
     * Starts discovering devices.
     */
    fun startDiscovery(
        scope: CoroutineScope,
        castContext: CastContext?,
        onAutoReconnect: ((String) -> Unit)? = null,
    ) {
        discoveryJob?.cancel()
        castDevices = emptyList()
        dlnaDevices = emptyList()
        preferredDeviceName = null
        autoReconnectEnabled = false
        autoReconnectAttempted = false
        publishDiscoveryState = true
        stateStore.update { it.copy(discoveryState = DiscoveryState.DISCOVERING, availableDevices = emptyList()) }

        scope.launch {
            val prefs = castPreferencesRepository.castPreferencesFlow.first()
            val nowMs = System.currentTimeMillis()
            preferredDeviceName = rememberedDeviceName(prefs, nowMs)
            autoReconnectEnabled = autoReconnectTarget(prefs, nowMs) != null
            publishMergedDevices(onAutoReconnect)
        }

        beginRouteDiscovery(castContext)

        dlnaDiscoveryController.startDiscovery(scope) { devices ->
            dlnaDevices = devices.map { "DLNA: ${it.friendlyName}" }
            publishMergedDevices(onAutoReconnect)
        }

        discoveryJob = scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (stateStore.castState.value.discoveryState == DiscoveryState.DISCOVERING) {
                stateStore.update { state ->
                    if (state.availableDevices.isEmpty()) {
                        state.copy(discoveryState = DiscoveryState.TIMEOUT)
                    } else {
                        state.copy(discoveryState = DiscoveryState.DEVICES_FOUND)
                    }
                }
            }
        }
    }

    fun attemptAutoReconnect(
        scope: CoroutineScope,
        castContext: CastContext?,
        onAutoReconnect: (String) -> Unit,
    ) {
        discoveryJob?.cancel()
        castDevices = emptyList()
        dlnaDevices = emptyList()
        preferredDeviceName = null
        autoReconnectEnabled = false
        autoReconnectAttempted = false
        publishDiscoveryState = false

        scope.launch {
            val prefs = castPreferencesRepository.castPreferencesFlow.first()
            val reconnectTarget = autoReconnectTarget(prefs, System.currentTimeMillis()) ?: run {
                stopInternalDiscovery()
                return@launch
            }
            preferredDeviceName = reconnectTarget
            autoReconnectEnabled = true

            beginRouteDiscovery(castContext)
            dlnaDiscoveryController.startDiscovery(scope) { devices ->
                dlnaDevices = devices.map { "DLNA: ${it.friendlyName}" }
                publishMergedDevices(onAutoReconnect)
            }

            discoveryJob = launch {
                delay(DISCOVERY_TIMEOUT_MS)
                stopInternalDiscovery()
            }
        }
    }

    /**
     * Stops device discovery and clears callbacks.
     */
    fun stopDiscovery() {
        stopInternalDiscovery()
        stateStore.update { it.copy(discoveryState = DiscoveryState.IDLE) }
    }

    fun findDlnaDevice(displayName: String): DlnaDevice? = dlnaDiscoveryController.findByDisplayName(displayName)

    private fun updateDiscoveredDevices(router: MediaRouter) {
        val selector = currentSelector
        castDevices = router.routes
            .filter { route ->
                !route.isDefault &&
                    route.isEnabled &&
                    route.name.isNotEmpty() &&
                    selector != null &&
                    route.matchesSelector(selector)
            }
            .map { it.name }
            .distinct()
        publishMergedDevices()
    }

    private fun publishMergedDevices(onAutoReconnect: ((String) -> Unit)? = null) {
        val devices = prioritizedDevices((castDevices + dlnaDevices).distinct(), preferredDeviceName)
        if (publishDiscoveryState) {
            stateStore.update { state ->
                state.copy(
                    availableDevices = devices,
                    discoveryState = if (devices.isNotEmpty()) DiscoveryState.DEVICES_FOUND else state.discoveryState,
                )
            }
        }

        if (shouldAutoReconnect(preferredDeviceName, autoReconnectEnabled, autoReconnectAttempted, devices)) {
            autoReconnectAttempted = true
            preferredDeviceName?.let { onAutoReconnect?.invoke(it) }
        }
    }

    private fun beginRouteDiscovery(castContext: CastContext?) {
        if (castContext == null) {
            SecureLogger.w("CastDiscovery", "CastContext unavailable; discovering DLNA devices only")
            currentSelector = null
            return
        }
        val router = MediaRouter.getInstance(context)
        val selector = castContext.mergedSelector
        currentSelector = selector
        if (selector != null) {
            if (!routeCallbackAdded) {
                router.addCallback(
                    selector,
                    routeCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                        MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
                )
                routeCallbackAdded = true
            }
            updateDiscoveredDevices(router)
        }
    }

    private fun stopInternalDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null

        if (routeCallbackAdded) {
            MediaRouter.getInstance(context).removeCallback(routeCallback)
            routeCallbackAdded = false
        }
        currentSelector = null
        preferredDeviceName = null
        autoReconnectEnabled = false
        autoReconnectAttempted = false
        publishDiscoveryState = true
        dlnaDiscoveryController.stopDiscovery()
    }
}
