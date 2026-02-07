package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.rpeters.jellyfin.ui.components.PerformanceOptimizedLazyRow
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Reusable immersive media row with horizontal scrolling.
 * Used across immersive screens (Home, Movies, TV Shows).
 *
 * **Performance Optimizations** (Phase 5):
 * - Uses PerformanceOptimizedLazyRow for automatic item limiting
 * - Adaptive image quality based on device tier
 * - Conditional image loading for off-screen items
 * - Device-tier adaptive max items (20-50 based on RAM)
 *
 * @param performanceConfig Performance configuration (defaults to device-detected)
 */
@Composable
fun ImmersiveMediaRow(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    size: ImmersiveCardSize = ImmersiveCardSize.MEDIUM,
    modifier: Modifier = Modifier,
    performanceConfig: ImmersivePerformanceConfig = rememberImmersivePerformanceConfig(),
) {
    Column(modifier = modifier) {
        // Section title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                horizontal = ImmersiveDimens.SpacingContentPadding,
                vertical = Dimens.Spacing8,
            ),
        )

        // ✅ Performance: Horizontal scrolling row with optimized item limiting
        PerformanceOptimizedLazyRow(
            items = items,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
            contentPadding = PaddingValues(horizontal = ImmersiveDimens.SpacingContentPadding),
            maxVisibleItems = performanceConfig.maxRowItems, // Device-tier adaptive (20-50 items)
        ) { item, index, isVisible ->
            ImmersiveMediaCard(
                title = item.name ?: "Unknown",
                imageUrl = getImageUrl(item) ?: "",
                onCardClick = { onItemClick(item) },
                subtitle = itemSubtitle(item),
                rating = item.communityRating,
                isFavorite = item.userData?.isFavorite == true,
                watchProgress = (item.userData?.playedPercentage ?: 0.0).toFloat() / 100f,
                cardSize = size,
                loadImage = isVisible, // ✅ Performance: Only load images for visible items
                imageQuality = performanceConfig.imageQuality, // ✅ Performance: Adaptive quality
            )
        }
    }
}

/**
 * Helper to generate subtitle for items in immersive cards.
 */
fun itemSubtitle(item: BaseItemDto): String {
    return when (item.type) {
        BaseItemKind.EPISODE -> {
            val season = item.parentIndexNumber
            val episode = item.indexNumber
            if (season != null && episode != null) {
                "S${season}E$episode"
            } else {
                item.seriesName ?: ""
            }
        }
        BaseItemKind.MOVIE -> {
            item.productionYear?.toString() ?: ""
        }
        BaseItemKind.SERIES -> {
            val count = item.childCount
            if (count != null && count > 0) {
                "$count episodes"
            } else {
                ""
            }
        }
        else -> item.productionYear?.toString() ?: ""
    }
}
