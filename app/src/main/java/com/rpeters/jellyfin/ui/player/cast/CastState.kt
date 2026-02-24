package com.rpeters.jellyfin.ui.player.cast

import androidx.media3.common.util.UnstableApi

@UnstableApi
enum class DiscoveryState {
    IDLE,
    DISCOVERING,
    DEVICES_FOUND,
    TIMEOUT,
    ERROR,
}

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
    // Discovery
    val discoveryState: DiscoveryState = DiscoveryState.IDLE,
    val availableDevices: List<String> = emptyList(),
)
