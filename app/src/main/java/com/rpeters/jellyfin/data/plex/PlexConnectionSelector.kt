package com.rpeters.jellyfin.data.plex

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** The address to use for a Plex server, plus every other address it listed, best first. */
data class PlexServerAddress(
    val uri: String,
    val alternateUris: List<String>,
)

/**
 * Picks how to reach a server from its plex.tv connections: probes `/identity` on all of them at
 * once and takes the best one that answers as that server, preferring local HTTPS, then local
 * HTTP, then remote, then Plex's bandwidth-limited relay.
 */
@Singleton
class PlexConnectionSelector @Inject constructor(
    private val identityProbe: PlexIdentityProbe,
) {
    /** @return null when no connection answers with [server]'s machine identifier. */
    suspend fun select(server: PlexResource): PlexServerAddress? = coroutineScope {
        val ranked = rank(server.connections)
        val answered = ranked.map { connection ->
            async {
                val identity = withTimeoutOrNull(PROBE_TIMEOUT_MS) { identityProbe.probe(connection.uri) }
                // A plex.direct name or LAN address can lead to a different server; only accept this one.
                identity?.machineIdentifier == server.clientIdentifier
            }
        }.awaitAll()
        val best = ranked.zip(answered).firstOrNull { (_, ok) -> ok }?.first ?: return@coroutineScope null
        PlexServerAddress(best.uri, ranked.map { it.uri }.filter { it != best.uri })
    }

    companion object {
        const val PROBE_TIMEOUT_MS = 3_000L

        /** Best first; connections in the same tier keep plex.tv's order. Duplicate URIs are dropped. */
        fun rank(connections: List<PlexConnection>): List<PlexConnection> =
            connections.distinctBy { it.uri }.sortedBy(::tier)

        private fun tier(connection: PlexConnection): Int = when {
            connection.relay -> 4
            connection.local && connection.isHttps -> 0
            connection.local -> 1
            connection.isHttps -> 2
            else -> 3
        }
    }
}
