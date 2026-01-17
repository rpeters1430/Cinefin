package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
internal fun StuffLoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    ExpressiveFullScreenLoading(
        message = message,
        modifier = modifier,
    )
}

@Composable
internal fun StuffErrorState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun StuffEmptyState(
    type: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val emptyMessage = when (type) {
            "books" -> "No books found"
            "homevideos" -> "No videos found"
            else -> "No items found"
        }
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StuffGrid(
    stuffItems: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    itemKey: (BaseItemDto) -> String,
    onItemClick: (String) -> Unit,
    onFavoriteClick: (BaseItemDto) -> Unit,
    onShareClick: (BaseItemDto) -> Unit,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Load more items when approaching the end
    LaunchedEffect(gridState, stuffItems.size, hasMoreItems, isLoadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && !isLoadingMore && hasMoreItems) {
                    val threshold = stuffItems.size - 10 // Load more when 10 items from the end
                    if (lastVisibleIndex >= threshold) {
                        onLoadMore()
                    }
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(
            items = stuffItems,
            key = itemKey,
            contentType = { "stuff_item" },
        ) { stuffItem ->
            ExpressiveMediaCard(
                title = stuffItem.name ?: "",
                subtitle = stuffItem.type.toString(),
                imageUrl = getImageUrl(stuffItem) ?: "",
                rating = stuffItem.communityRating,
                isFavorite = stuffItem.userData?.isFavorite == true,
                onCardClick = { onItemClick(stuffItem.id.toString()) },
                onPlayClick = {
                    // For home videos, trigger playback
                    when (stuffItem.type) {
                        BaseItemKind.VIDEO -> {
                            onItemClick(stuffItem.id.toString())
                        }
                        else -> {
                            onItemClick(stuffItem.id.toString())
                        }
                    }
                },
                onFavoriteClick = {
                    onFavoriteClick(stuffItem)
                },
                onMoreClick = {
                    onShareClick(stuffItem)
                },
            )
        }

        if (isLoadingMore || hasMoreItems) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        ExpressiveCircularLoading()
                    } else if (hasMoreItems) {
                        // Show a small indicator that more items can be loaded
                        Text(
                            text = "Scroll for more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StuffGridPreview() {
    StuffGrid(
        stuffItems = emptyList(),
        getImageUrl = { null },
        itemKey = { item -> item.id.toString() },
        onItemClick = {},
        onFavoriteClick = {},
        onShareClick = {},
        isLoadingMore = false,
        hasMoreItems = false,
        onLoadMore = {},
    )
}
