package com.rpeters.jellyfin.data.playback

import com.rpeters.jellyfin.data.model.ServerType
import com.rpeters.jellyfin.data.preferences.PlaybackPreferences
import com.rpeters.jellyfin.data.preferences.TranscodingQuality
import java.net.URI

/** Resolves Emby's remote-only bitrate policy without changing user-selected Cinefin quality. */
internal object RemoteStreamingQualityPolicy {
    fun resolveLimit(
        serverType: ServerType,
        serverUrl: String?,
        localAddress: String?,
        remoteBitrateLimit: Int,
        preferences: PlaybackPreferences,
    ): Int? {
        if (!serverType.supportsRemoteBitrateLimit || remoteBitrateLimit <= 0) return null
        if (hasExplicitBitratePreference(preferences)) return null
        if (isLocalConnection(serverUrl, localAddress)) return null
        return remoteBitrateLimit
    }

    private fun hasExplicitBitratePreference(preferences: PlaybackPreferences): Boolean =
        preferences.transcodingQuality != TranscodingQuality.AUTO ||
            preferences.maxBitrateWifi != PlaybackPreferences.DEFAULT.maxBitrateWifi ||
            preferences.maxBitrateCellular != PlaybackPreferences.DEFAULT.maxBitrateCellular

    private fun isLocalConnection(serverUrl: String?, localAddress: String?): Boolean {
        val serverHost = host(serverUrl) ?: return true
        val localHost = host(localAddress)
        if (localHost != null && serverHost.equals(localHost, ignoreCase = true)) return true
        return serverHost.isLocalHost()
    }

    private fun host(url: String?): String? = runCatching {
        if (url.isNullOrBlank()) return null
        val normalized = if (url.contains("://")) url else "http://$url"
        URI(normalized).host?.trim('[', ']')
    }.getOrNull()

    private fun String.isLocalHost(): Boolean {
        val normalized = lowercase()
        if (normalized == "localhost" || normalized.endsWith(".local")) return true
        if (normalized == "::1" || normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe80:")) return true

        val octets = normalized.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            octets[0] == 127
    }
}
