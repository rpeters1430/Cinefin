package com.rpeters.jellyfin.data.preferences

/**
 * Data class representing Seerr (Overseerr/Jellyseerr) integration preferences.
 */
data class SeerrPreferences(
    val baseUrl: String,
    val apiKey: String,
    val isEnabled: Boolean = false
) {
    companion object {
        val DEFAULT = SeerrPreferences(
            baseUrl = "",
            apiKey = "",
            isEnabled = false
        )
    }

    /**
     * Checks if the Seerr configuration is valid (URL and API key present).
     */
    val isValid: Boolean get() = baseUrl.isNotBlank() && 
                                 apiKey.isNotBlank() && 
                                 runCatching { java.net.URL(baseUrl).host.isNotBlank() }.getOrDefault(false)
}
