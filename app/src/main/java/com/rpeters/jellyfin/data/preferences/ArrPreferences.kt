package com.rpeters.jellyfin.data.preferences

data class SonarrPreferences(
    val baseUrl: String,
    val apiKey: String,
    val isEnabled: Boolean = false,
) {
    val isValid: Boolean get() = baseUrl.isNotBlank() && 
                                 apiKey.isNotBlank() && 
                                 runCatching { java.net.URL(baseUrl).host.isNotBlank() }.getOrDefault(false)

    companion object {
        val DEFAULT = SonarrPreferences(baseUrl = "", apiKey = "", isEnabled = false)
    }
}

data class RadarrPreferences(
    val baseUrl: String,
    val apiKey: String,
    val isEnabled: Boolean = false,
) {
    val isValid: Boolean get() = baseUrl.isNotBlank() && 
                                 apiKey.isNotBlank() && 
                                 runCatching { java.net.URL(baseUrl).host.isNotBlank() }.getOrDefault(false)

    companion object {
        val DEFAULT = RadarrPreferences(baseUrl = "", apiKey = "", isEnabled = false)
    }
}
