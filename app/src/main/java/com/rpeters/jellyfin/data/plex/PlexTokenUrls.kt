package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.JellyfinServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * ExoPlayer, Coil and Cast fetch URLs without going through the app's interceptor, so a Plex URL
 * handed to them carries its token as the `X-Plex-Token` query parameter instead of a header.
 */
object PlexTokenUrls {
    const val TOKEN_PARAMETER = PlexClientHeaders.TOKEN

    /**
     * @return [url] with `X-Plex-Token` set to [token], replacing any token already there; or
     *   [url] unchanged when it is not a URL or does not point at one of [server]'s addresses.
     */
    fun withToken(url: String, token: String, server: JellyfinServer): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (!isServerHost(parsed, server)) return url
        return parsed.newBuilder().setQueryParameter(TOKEN_PARAMETER, token).build().toString()
    }

    /** True when [url] points at [server]'s own address or one of its stored alternates. */
    fun isServerHost(url: HttpUrl, server: JellyfinServer): Boolean =
        (listOf(server.url) + server.alternateServerUrls)
            .mapNotNull { it.toHttpUrlOrNull() }
            .any { it.host.equals(url.host, ignoreCase = true) && it.port == url.port }
}
