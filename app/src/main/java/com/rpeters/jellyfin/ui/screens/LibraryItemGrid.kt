package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jellyfin.sdk.model.api.BaseItemDto

/** Footer composable used for pagination within grids and lists. */
@Composable
fun PaginationFooter(
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    libraryType: LibraryType,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        if (hasMoreItems && !isLoadingMore) {
            onLoadMore()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(LibraryScreenDefaults.ContentPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.FilterChipSpacing),
            ) {
                CircularProgressIndicator(
                    color = libraryType.color,
                    modifier = Modifier.size(LibraryScreenDefaults.ViewModeIconSize),
                )
                Text(
                    text = "Loading more...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!hasMoreItems) {
            Text(
                text = "No more items to load",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Section used in carousel mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselSection(
    title: String,
    items: List<BaseItemDto>,
    carouselState: androidx.compose.material3.carousel.CarouselState,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                horizontal = LibraryScreenDefaults.ContentPadding,
                vertical = LibraryScreenDefaults.FilterChipSpacing,
            ),
        )

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            modifier = Modifier.height(LibraryScreenDefaults.CarouselHeight),
            preferredItemWidth = LibraryScreenDefaults.CarouselPreferredItemWidth,
            itemSpacing = LibraryScreenDefaults.CarouselItemSpacing,
            contentPadding = PaddingValues(horizontal = LibraryScreenDefaults.ContentPadding),
        ) { index ->
            LibraryItemCard(
                item = items[index],
                libraryType = libraryType,
                getImageUrl = getImageUrl,
                onTVShowClick = onTVShowClick,
                isCompact = true,
            )
        }
    }
}
