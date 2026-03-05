package com.rpeters.jellyfin.ui.player.cast

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.rpeters.jellyfin.ui.player.dlna.DlnaDevice
import com.rpeters.jellyfin.ui.player.dlna.DlnaDiscoveryController
import com.rpeters.jellyfin.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val dlnaDiscoveryController: DlnaDiscoveryController,
) {
    private var routeCallbackAdded = false
    private var discoveryJob: Job? = null
    private var castDevices: List<String> = emptyList()
    private var dlnaDevices: List<String> = emptyList()

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
    }

    /**
     * Starts discovering devices.
     */
    fun startDiscovery(scope: CoroutineScope, castContext: CastContext?) {
        discoveryJob?.cancel()
        castDevices = emptyList()
        dlnaDevices = emptyList()
        stateStore.update { it.copy(discoveryState = DiscoveryState.DISCOVERING, availableDevices = emptyList()) }

        if (castContext == null) {
            SecureLogger.w("CastDiscovery", "CastContext unavailable; discovering DLNA devices only")
        } else {
            val router = MediaRouter.getInstance(context)
            val selector = castContext.mergedSelector
            if (selector != null) {
                if (!routeCallbackAdded) {
                    router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                    routeCallbackAdded = true
                }
                updateDiscoveredDevices(router)
            }
        }

        dlnaDiscoveryController.startDiscovery(scope) { devices ->
            dlnaDevices = devices.map { "DLNA: ${it.friendlyName}" }
            publishMergedDevices()
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

    /**
     * Stops device discovery and clears callbacks.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null

        if (routeCallbackAdded) {
            MediaRouter.getInstance(context).removeCallback(routeCallback)
            routeCallbackAdded = false
        }
        dlnaDiscoveryController.stopDiscovery()

        stateStore.update { it.copy(discoveryState = DiscoveryState.IDLE) }
    }

    fun findDlnaDevice(displayName: String): DlnaDevice? = dlnaDiscoveryController.findByDisplayName(displayName)

    private fun updateDiscoveredDevices(router: MediaRouter) {
        // We can't access CastContext easily here, so we assume the router has the right routes
        // The filter is applied based on standard Cast framework behavior
        castDevices = router.routes
            .filter { !it.isDefault && it.isEnabled && it.name.isNotEmpty() }
            .map { it.name }
            .distinct()
        publishMergedDevices()
    }

    private fun publishMergedDevices() {
        val devices = (castDevices + dlnaDevices).distinct()
        stateStore.update { state ->
            state.copy(
                availableDevices = devices,
                discoveryState = if (devices.isNotEmpty()) DiscoveryState.DEVICES_FOUND else state.discoveryState,
            )
        }
    }
}
