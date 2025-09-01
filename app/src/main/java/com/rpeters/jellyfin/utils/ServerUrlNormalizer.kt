package com.rpeters.jellyfin.utils

import java.net.URI
import java.net.URISyntaxException

/**
 * Normalize a Jellyfin server URL by trimming whitespace, removing trailing slashes
 * and forcing lowercase scheme and host parts.
 */
fun normalizeServerUrl(input: String): String {
    var url = input.trim()
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "https://$url"
    }
    return try {
        val uri = URI(url)
        val scheme = (uri.scheme ?: "https").lowercase()
        val host = uri.host?.lowercase() ?: return input
        val path = uri.rawPath?.trimEnd('/') ?: ""
        buildString {
            append(scheme).append("://").append(host)
            if (path.isNotEmpty()) append(path)
        }
    } catch (_: URISyntaxException) {
        url
    }
}
