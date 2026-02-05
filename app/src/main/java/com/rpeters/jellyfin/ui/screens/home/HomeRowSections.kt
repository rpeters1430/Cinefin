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
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun PosterRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 150.dp,
    modifier: Modifier = Modifier,
) {
    HomeRowSection(
        title = title,
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "poster_media_card" },
        ) { item ->
            PosterMediaCard(
                item = item,
                getImageUrl = getImageUrl,
                onClick = onItemClick,
                onLongPress = onItemLongPress,
                cardWidth = cardWidth,
                showTitle = true,
                showMetadata = true,
                titleMinLines = 2,
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
