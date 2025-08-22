package com.rpeters.jellyfin.ui.components

import androidx.compose.ui.graphics.Color
import com.rpeters.jellyfin.ui.theme.Quality4K
import com.rpeters.jellyfin.ui.theme.QualityHD
import com.rpeters.jellyfin.ui.theme.QualitySD
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale

/**
 * Get quality label and color for a media item
 */
fun getQualityLabel(item: BaseItemDto): Pair<String, Color>? {
    val mediaSource = item.mediaSources?.firstOrNull() ?: return null
    val videoStream = mediaSource.mediaStreams?.firstOrNull { (it.type as? String)?.lowercase(Locale.ROOT) == "video" }
    val width = videoStream?.width ?: 0
    return when {
        width >= 3800 -> "4K" to Quality4K
        width >= 1900 -> "HD" to QualityHD
        width > 0 -> "SD" to QualitySD
        mediaSource.container?.contains("4k", ignoreCase = true) == true -> "4K" to Quality4K
        mediaSource.container?.contains("hd", ignoreCase = true) == true -> "HD" to QualityHD
        else -> null
    }
}
