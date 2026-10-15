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
        val answers = ranked.map { connection ->
            async { withTimeoutOrNull(PROBE_TIMEOUT_MS) { identityProbe.probe(connection.uri) } }
        }.awaitAll()
        val results = ranked.zip(answers)
        val best = results.firstOrNull { (_, identity) -> identity?.machineIdentifier == server.clientIdentifier }
            ?.first ?: return@coroutineScope null
        // A plex.direct name or LAN address can lead to a different server. Alternates later get
        // this server's token, so drop any address that said it is another machine; keep ones
        // that did not answer, since they may work from another network.
        val alternates = results
            .filter { (connection, identity) ->
                connection != best && (identity == null || identity.machineIdentifier == server.clientIdentifier)
            }
            .map { (connection, _) -> connection.uri }
        PlexServerAddress(best.uri, alternates)
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
