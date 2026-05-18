package com.rpeters.jellyfin.data.model

/**
 * Represents a Jellyfin server discovered on the local network.
 */
data class DiscoveredServer(
    val name: String,
    val address: String,
    val id: String,
    val version: String? = null
)
