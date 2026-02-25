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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
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
                ExpressiveCircularLoading(
                    color = libraryType.color,
                    size = LibraryScreenDefaults.ViewModeIconSize,
                )
                Text(
                    text = stringResource(id = R.string.library_actions_loading_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!hasMoreItems) {
            Text(
                text = stringResource(id = R.string.library_actions_no_more_items),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Section used in carousel mode. */
@OptInAppExperimentalApis
@Composable
fun CarouselSection(
    title: String,
    items: List<BaseItemDto>,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onTVShowClick: ((String) -> Unit)? = null,
    onItemLongPress: ((BaseItemDto) -> Unit)? = null,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                horizontal = 0.dp,
                vertical = LibraryScreenDefaults.FilterChipSpacing,
            ),
        )

        LazyRow(
            modifier = Modifier.height(
                if (isTablet) {
                    LibraryScreenDefaults.TabletCarouselHeight
                } else {
                    LibraryScreenDefaults.CarouselHeight
                },
            ),
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.CarouselItemSpacing),
        ) {
            items(
                items = items,
                key = { it.id.toString() },
            ) { item ->
                LibraryItemCard(
                    item = item,
                    libraryType = libraryType,
                    getImageUrl = getImageUrl,
                    onItemClick = onItemClick,
                    onTVShowClick = onTVShowClick,
                    onItemLongPress = onItemLongPress,
                    isCompact = true,
                    isTablet = isTablet,
                )
            }
        }
    }
}
