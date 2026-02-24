package com.rpeters.jellyfin.ui.player.cast

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.rpeters.jellyfin.ui.player.DiscoveryState
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
) {
    private var routeCallbackAdded = false
    private var discoveryJob: Job? = null

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
        if (castContext == null) {
            SecureLogger.w("CastDiscovery", "Cannot start discovery: CastContext is null")
            return
        }

        discoveryJob?.cancel()
        stateStore.update { it.copy(discoveryState = DiscoveryState.DISCOVERING, availableDevices = emptyList()) }

        val router = MediaRouter.getInstance(context)
        val selector = castContext.mergedSelector
        if (selector != null) {
            if (!routeCallbackAdded) {
                router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                routeCallbackAdded = true
            }
            updateDiscoveredDevices(router)
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

        stateStore.update { it.copy(discoveryState = DiscoveryState.IDLE) }
    }

    private fun updateDiscoveredDevices(router: MediaRouter) {
        // We can't access CastContext easily here, so we assume the router has the right routes
        // The filter is applied based on standard Cast framework behavior
        val devices = router.routes
            .filter { !it.isDefault && it.isEnabled && it.name.isNotEmpty() }
            .map { it.name }
            .distinct()

        stateStore.update { state ->
            state.copy(
                availableDevices = devices,
                discoveryState = if (devices.isNotEmpty()) DiscoveryState.DEVICES_FOUND else state.discoveryState,
            )
        }
    }
}
