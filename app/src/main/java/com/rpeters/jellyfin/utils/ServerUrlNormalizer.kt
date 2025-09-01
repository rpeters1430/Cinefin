package com.rpeters.jellyfin.utils

import java.net.URI

/**
 * Normalize a Jellyfin server URL by trimming whitespace, removing trailing slashes
 * and forcing lowercase scheme and host parts.
 */
fun normalizeServerUrl(input: String): String {
    var url = input.trim()
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "https://$url"
    }
    val uri = URI(url)
    val scheme = (uri.scheme ?: "https").lowercase()
    val host = (uri.host ?: uri.authority ?: "").lowercase()
    val port = if (uri.port != -1) ":${uri.port}" else ""
    val path = uri.rawPath?.trimEnd('/') ?: ""
    return buildString {
        append(scheme).append("://").append(host).append(port)
        if (path.isNotEmpty()) append(path)
    }
}
