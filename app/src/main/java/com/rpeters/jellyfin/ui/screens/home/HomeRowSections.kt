package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.components.MediaCard
import com.rpeters.jellyfin.ui.components.immersive.ImmersivePosterCard
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.utils.getItemKey
import com.rpeters.jellyfin.utils.getUnwatchedEpisodeCount
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Compact poster subtitle for the density-redesign rails: the series name for episodes,
 * otherwise the production year — mirrors the metadata shown by the legacy [PosterMediaCard].
 */
private fun posterRowSubtitle(item: BaseItemDto): String = when {
    item.type == BaseItemKind.EPISODE && !item.seriesName.isNullOrBlank() -> item.seriesName.orEmpty()
    else -> item.productionYear?.toString().orEmpty()
}

@Composable
fun PosterRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = ImmersiveDimens.PosterCardWidth,
    modifier: Modifier = Modifier,
) {
    // Density-redesign spec targets an 88x132 (2:3) poster; derive the height from whatever
    // width a caller passes so existing adaptive-layout call sites keep working unchanged.
    val cardHeight = cardWidth * (ImmersiveDimens.PosterCardHeight / ImmersiveDimens.PosterCardWidth)
    HomeRowSection(
        title = title,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "poster_media_card" },
        ) { item ->
            ImmersivePosterCard(
                title = item.name ?: "",
                imageUrl = getImageUrl(item) ?: "",
                onCardClick = { onItemClick(item) },
                onCardLongClick = { onItemLongPress(item) },
                subtitle = posterRowSubtitle(item),
                rating = item.communityRating,
                unwatchedEpisodeCount = item.getUnwatchedEpisodeCount().takeIf { it > 0 },
                posterWidth = cardWidth,
                posterHeight = cardHeight,
            )
        }
    }
}

@Composable
fun SquareRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 280.dp,
    modifier: Modifier = Modifier,
) {
    HomeRowSection(
        title = title,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "square_media_card" },
        ) { item ->
            MediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
            )
        }
    }
}

@Composable
fun MediaRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 280.dp,
    modifier: Modifier = Modifier,
) {
    HomeRowSection(
        title = title,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "media_card" },
        ) { item ->
            MediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
            )
        }
    }
}

@Composable
fun HomeRowSection(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        HomeSectionTitle(title = title)

        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}

@Composable
fun HomeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
