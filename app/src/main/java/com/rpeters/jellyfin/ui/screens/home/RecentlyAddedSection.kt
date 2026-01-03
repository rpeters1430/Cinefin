package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.components.RecentlyAddedCard
import com.rpeters.jellyfin.ui.tv.TvFocusableCarousel
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun RecentlyAddedSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh $title")
            }
        }
        val listState = rememberLazyListState()
        val tvFocusManager = rememberTvFocusManager()
        TvFocusableCarousel(
            carouselId = "recently_added_${title.replace(' ', '_')}",
            focusManager = tvFocusManager,
            lazyListState = listState,
            itemCount = items.size,
        ) { focusModifier ->
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(focusModifier),
            ) {
                items(
                    items = items,
                    key = { it.getItemKey().ifEmpty { it.name ?: it.toString() } },
                    contentType = { "recently_added_item" },
                ) { item ->
                    RecentlyAddedCard(
                        item = item,
                        getImageUrl = getImageUrl,
                        getSeriesImageUrl = getSeriesImageUrl,
                        onClick = onItemClick,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecentlyAddedSectionPreview() {
    RecentlyAddedSection(
        title = "Recently Added Movies",
        items = emptyList(),
        getImageUrl = { null },
        getSeriesImageUrl = { null },
        onItemClick = {},
        onRefresh = {},
    )
}
