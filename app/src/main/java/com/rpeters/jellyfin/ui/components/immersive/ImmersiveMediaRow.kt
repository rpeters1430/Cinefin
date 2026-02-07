package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Reusable immersive media row with horizontal scrolling.
 * Used across immersive screens (Home, Movies, TV Shows).
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

        // Horizontal scrolling row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
            contentPadding = PaddingValues(horizontal = ImmersiveDimens.SpacingContentPadding),
        ) {
            items(
                items = items,
                key = { it.getItemKey() },
            ) { item ->
                ImmersiveMediaCard(
                    title = item.name ?: "Unknown",
                    imageUrl = getImageUrl(item) ?: "",
                    onCardClick = { onItemClick(item) },
                    subtitle = itemSubtitle(item),
                    rating = item.communityRating?.toFloat(),
                    isFavorite = item.userData?.isFavorite == true,
                    watchProgress = (item.userData?.playedPercentage ?: 0.0).toFloat() / 100f,
                    cardSize = size,
                )
            }
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
                "S${season}E${episode}"
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
