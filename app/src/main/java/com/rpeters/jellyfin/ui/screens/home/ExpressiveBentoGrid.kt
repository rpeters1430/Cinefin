package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.*
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Internal model representing a single item in the Bento grid.
 */
internal data class BentoGridItem(
    val id: String,
    val type: BentoItemType,
    val item: BaseItemDto? = null,
    val title: String? = null,
    val description: String? = null,
    val icon: ImageVector? = null,
    val onClick: () -> Unit = {},
)

/**
 * A dynamic, Material 3 Expressive "Bento Box" layout for the home screen.
 * It uses variable card sizes and spans to create a visually engaging experience.
 *
 * @param contentLists The data source containing various media lists.
 * @param windowSizeClass The current window size class for adaptive layout.
 * @param getImageUrl Function to resolve the image URL for a given item.
 * @param onItemClick Callback when a media item is clicked.
 * @param modifier The modifier to be applied to the grid.
 * @param gridState The state object to be used to control or observe the grid's scroll state.
 * @param contentPadding The padding to apply around the grid content.
 */
@Composable
fun ExpressiveBentoGrid(
    contentLists: HomeContentLists,
    windowSizeClass: WindowSizeClass,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    // Determine number of columns based on screen width
    val columns = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        WindowWidthSizeClass.Expanded -> 4
        else -> 2
    }

    val aiDiscoveryTitle = stringResource(R.string.home_ai_discovery)
    val aiDiscoveryDescription = stringResource(R.string.home_ai_discovery_description)

    // Map content lists to bento grid items
    val bentoItems = remember(contentLists, aiDiscoveryTitle, aiDiscoveryDescription) {
        buildList {
            // 1. Featured Hero (Top recommendation)
            contentLists.featuredItems.firstOrNull()?.let { item ->
                add(
                    BentoGridItem(
                        id = "featured_hero_${item.id}",
                        type = BentoItemType.Featured,
                        item = item,
                        onClick = { onItemClick(item) },
                    ),
                )
            }

            // 2. Action Tiles (Next Up / Continue Watching)
            contentLists.continueWatching.take(2).forEach { item ->
                add(
                    BentoGridItem(
                        id = "action_nextup_${item.id}",
                        type = BentoItemType.Action,
                        item = item,
                        onClick = { onItemClick(item) },
                    ),
                )
            }

            // 3. Wide Discovery Banner
            add(
                BentoGridItem(
                    id = "ai_discovery_banner",
                    type = BentoItemType.Wide,
                    title = aiDiscoveryTitle,
                    description = aiDiscoveryDescription,
                    icon = Icons.Default.AutoAwesome,
                    onClick = { /* TODO: Implement AI Discovery navigation */ },
                ),
            )

            // 4. Secondary Featured Items
            contentLists.featuredItems.drop(1).take(2).forEach { item ->
                add(
                    BentoGridItem(
                        id = "featured_secondary_${item.id}",
                        type = BentoItemType.Featured,
                        item = item,
                        onClick = { onItemClick(item) },
                    ),
                )
            }

            // 5. Recent Movies as Action Tiles
            contentLists.recentMovies.take(4).forEach { item ->
                add(
                    BentoGridItem(
                        id = "recent_movie_${item.id}",
                        type = BentoItemType.Action,
                        item = item,
                        onClick = { onItemClick(item) },
                    ),
                )
            }

            // 6. Recent TV Shows
            contentLists.recentTVShows.take(2).forEach { item ->
                add(
                    BentoGridItem(
                        id = "recent_tv_${item.id}",
                        type = BentoItemType.Action,
                        item = item,
                        onClick = { onItemClick(item) },
                    ),
                )
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = bentoItems,
            key = { it.id },
            span = { bentoItem -> getBentoSpan(bentoItem.type, columns) },
        ) { bentoItem ->
            when (bentoItem.type) {
                BentoItemType.Featured -> {
                    bentoItem.item?.let { item ->
                        BentoFeaturedCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            onClick = onItemClick,
                            onItemLongPress = onItemLongPress,
                        )
                    }
                }
                BentoItemType.Action -> {
                    bentoItem.item?.let { item ->
                        BentoActionCard(
                            item = item,
                            icon = Icons.Default.PlayArrow,
                            onClick = onItemClick,
                            onItemLongPress = onItemLongPress,
                        )
                    }
                }
                BentoItemType.Wide -> {
                    BentoWideCard(
                        title = bentoItem.title ?: "",
                        description = bentoItem.description ?: "",
                        icon = bentoItem.icon ?: Icons.Default.AutoAwesome,
                        onClick = bentoItem.onClick,
                        onItemLongPress = onItemLongPress,
                        item = bentoItem.item,
                    )
                }
            }
        }
    }
}
