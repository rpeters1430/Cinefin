package com.rpeters.jellyfin.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * plex.tv (`clients.plex.tv/api/v2`) responses. Only the fields Cinefin uses are declared; the
 * client's Json ignores the rest. Classes that hold a token redact it from toString().
 */

/** A sign-in PIN. [authToken] stays null until the user approves [code] on plex.tv. */
@Serializable
data class PlexPin(
    val id: Long,
    val code: String,
    /** Seconds left before the PIN expires, as of the response. */
    val expiresIn: Int? = null,
    val authToken: String? = null,
) {
    override fun toString(): String =
        "PlexPin(id=$id, code=$code, expiresIn=${expiresIn ?: "unknown"}, " +
            "authToken=${if (authToken == null) "null" else "redacted"})"
}

/** The signed-in plex.tv account. */
@Serializable
data class PlexTvUser(
    val id: Long,
    val uuid: String? = null,
    val username: String,
    val title: String? = null,
    val thumb: String? = null,
) {
    /** The name to show: Plex Home users have a [title] that differs from [username]. */
    val displayName: String get() = title?.takeIf { it.isNotBlank() } ?: username
}

/**
 * A device on the account from `/api/v2/resources`. Only resources that provide `server` are
 * Plex Media Servers; the list also contains players and other clients.
 */
@Serializable
data class PlexResource(
    val name: String,
    val product: String? = null,
    val productVersion: String? = null,
    /** The server's `machineIdentifier`. */
    val clientIdentifier: String,
    val provides: String = "",
    /** True for the account's own servers; false for servers shared with it. */
    val owned: Boolean = false,
    /** The token for this server. On a shared server it differs from the account token. */
    val accessToken: String? = null,
    /** Whether plex.tv has heard from the server recently. */
    val presence: Boolean = false,
    /** The owner's name on a shared server. */
    val sourceTitle: String? = null,
    val connections: List<PlexConnection> = emptyList(),
) {
    val isServer: Boolean get() = provides.split(',').any { it.trim() == "server" }

    override fun toString(): String =
        "PlexResource(name=$name, clientIdentifier=$clientIdentifier, provides=$provides, owned=$owned, " +
            "presence=$presence, connections=${connections.size}, accessToken=redacted)"
}

/** One address a server can be reached on. */
@Serializable
data class PlexConnection(
    val protocol: String = "",
    val address: String = "",
    val port: Int = 0,
    val uri: String,
    /** On the same network as the server, as plex.tv saw it. */
    val local: Boolean = false,
    /** Goes through Plex's relay, which is bandwidth-limited. */
    val relay: Boolean = false,
    @SerialName("IPv6") val ipv6: Boolean = false,
) {
    val isHttps: Boolean get() = protocol.equals("https", ignoreCase = true) || uri.startsWith("https://")
}

/** The error body plex.tv sends with a 4xx, e.g. code 1020 for an unknown or expired PIN. */
@Serializable
internal data class PlexTvErrors(val errors: List<PlexTvError> = emptyList())

@Serializable
internal data class PlexTvError(val code: Int = 0, val message: String = "", val status: Int = 0)
