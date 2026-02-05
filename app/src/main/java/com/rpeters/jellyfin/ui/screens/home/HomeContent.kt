package com.rpeters.jellyfin.ui.screens.home

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.SurfaceCoordinatorViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.*

@OptInAppExperimentalApis
@Composable
fun HomeContent(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onItemLongPress: (BaseItemDto) -> Unit = {},
    onLibraryClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Calculate window size class for adaptive layout
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
    val adaptiveConfig = rememberAdaptiveLayoutConfig(windowSizeClass)
    val isTablet = adaptiveConfig.isTablet

    // DEBUG: Log received state for UI troubleshooting
    LaunchedEffect(appState.libraries.size, appState.recentlyAddedByTypes.size) {
        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
            SecureLogger.d(
                "HomeContent",
                "Received state - Libraries: ${appState.libraries.size}, " +
                    "RecentlyAddedByTypes: ${appState.recentlyAddedByTypes.mapValues { it.value.size }}",
            )
        }
    }

    // Consolidate all derived state computations
    val contentLists by remember(
        appState.allItems,
        appState.continueWatching,
        appState.recentlyAddedByTypes,
        adaptiveConfig.continueWatchingLimit,
        adaptiveConfig.rowItemLimit,
        adaptiveConfig.featuredItemsLimit,
    ) {
        derivedStateOf {
            val continueWatching = getContinueWatchingItems(appState, adaptiveConfig.continueWatchingLimit)
            val movies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val tvShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val episodes = appState.recentlyAddedByTypes[BaseItemKind.EPISODE.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val music = appState.recentlyAddedByTypes[BaseItemKind.AUDIO.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val videos = appState.recentlyAddedByTypes[BaseItemKind.VIDEO.name]
                ?.take(adaptiveConfig.rowItemLimit) ?: emptyList()
            val featured = (movies + tvShows).take(adaptiveConfig.featuredItemsLimit)

            HomeContentLists(
                continueWatching = continueWatching,
                recentMovies = movies,
                recentTVShows = tvShows,
                featuredItems = featured,
                recentEpisodes = episodes,
                recentMusic = music,
                recentVideos = videos,
            )
        }
    }

    val rowSections = remember(contentLists) {
        listOf(
            HomeRowSectionConfig(
                key = HomeSectionKeys.NEXT_UP,
                contentType = HomeSectionContentTypes.POSTER_ROW,
                titleRes = R.string.home_next_up,
                items = contentLists.recentEpisodes,
                rowKind = HomeRowKind.POSTER,
                imageSelector = HomeImageSelector.SERIES_OR_DEFAULT,
            ),
            HomeRowSectionConfig(
                key = HomeSectionKeys.RECENT_MOVIES,
                contentType = HomeSectionContentTypes.POSTER_ROW,
                titleRes = R.string.home_recently_added_movies,
                items = contentLists.recentMovies,
                rowKind = HomeRowKind.POSTER,
                imageSelector = HomeImageSelector.DEFAULT,
            ),
            HomeRowSectionConfig(
                key = HomeSectionKeys.RECENT_TV_SHOWS,
                contentType = HomeSectionContentTypes.POSTER_ROW,
                titleRes = R.string.home_recently_added_tv_shows,
                items = contentLists.recentTVShows,
                rowKind = HomeRowKind.POSTER,
                imageSelector = HomeImageSelector.DEFAULT,
            ),
            HomeRowSectionConfig(
                key = HomeSectionKeys.RECENT_STUFF,
                contentType = HomeSectionContentTypes.MEDIA_ROW,
                titleRes = R.string.home_recently_added_stuff,
                items = contentLists.recentVideos,
                rowKind = HomeRowKind.MEDIA,
                imageSelector = HomeImageSelector.BACKDROP_OR_DEFAULT,
            ),
        ).filter { it.items.isNotEmpty() }
    }

    val surfaceCoordinatorViewModel: SurfaceCoordinatorViewModel = hiltViewModel()

    LaunchedEffect(surfaceCoordinatorViewModel, contentLists.continueWatching) {
        snapshotFlow {
            contentLists.continueWatching.map { item ->
                val id = item.id.toString()
                Triple(id, item.name, item.seriesName)
            } to contentLists.continueWatching
        }
            .distinctUntilChangedBy { it.first }
            .collectLatest { (_, items) ->
                surfaceCoordinatorViewModel.updateContinueWatching(items)
            }
    }

    val imageProviders = remember(getImageUrl, getSeriesImageUrl, getBackdropUrl) {
        mapOf<HomeImageSelector, (BaseItemDto) -> String?>(
            HomeImageSelector.DEFAULT to getImageUrl,
            HomeImageSelector.SERIES_OR_DEFAULT to { item ->
                getSeriesImageUrl(item) ?: getImageUrl(item)
            },
            HomeImageSelector.BACKDROP_OR_DEFAULT to { item ->
                getBackdropUrl(item) ?: getImageUrl(item)
            },
        )
    }

    val unknownText = stringResource(id = R.string.unknown)
    val stableOnItemClick = remember(onItemClick) { onItemClick }
    val stableOnItemLongPress = remember(onItemLongPress) { onItemLongPress }
    val viewingMood = appState.viewingMood

    val listState = rememberLazyListState()

    PullToRefreshBox(
        isRefreshing = appState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        if (isTablet) {
            TabletHomeLayout(
                contentLists = contentLists,
                adaptiveConfig = adaptiveConfig,
                getImageUrl = getImageUrl,
                getBackdropUrl = getBackdropUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                onItemClick = stableOnItemClick,
                onItemLongPress = stableOnItemLongPress,
                unknownText = unknownText,
                viewingMood = viewingMood,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(adaptiveConfig.sectionSpacing),
                userScrollEnabled = true,
            ) {
                if (contentLists.featuredItems.isNotEmpty()) {
                    item(key = "featured", contentType = "carousel") {
                        val featured = remember(contentLists.featuredItems, unknownText) {
                            contentLists.featuredItems.map {
                                it.toCarouselItem(
                                    titleOverride = it.name ?: unknownText,
                                    subtitleOverride = itemSubtitle(it),
                                    imageUrl = getBackdropUrl(it) ?: getSeriesImageUrl(it)
                                        ?: getImageUrl(it) ?: "",
                                )
                            }
                        }
                        ExpressiveHeroCarousel(
                            items = featured,
                            onItemClick = { selected ->
                                contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                                    ?.let(stableOnItemClick)
                            },
                            onPlayClick = { selected ->
                                contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                                    ?.let(stableOnItemClick)
                            },
                            heroHeight = adaptiveConfig.heroHeight,
                            horizontalPadding = adaptiveConfig.heroHorizontalPadding,
                            pageSpacing = adaptiveConfig.heroPageSpacing,
                        )
                    }
                }

                // Viewing Mood Widget
                if (viewingMood != null) {
                    item(key = "viewing_mood", contentType = "ai_widget") {
                        ViewingMoodWidget(viewingMood = viewingMood)
                    }
                }

                // Continue Watching Section
                if (contentLists.continueWatching.isNotEmpty()) {
                    item(key = "continue_watching", contentType = "continueWatching") {
                        ContinueWatchingSection(
                            items = contentLists.continueWatching,
                            getImageUrl = getImageUrl,
                            onItemClick = stableOnItemClick,
                            onItemLongPress = stableOnItemLongPress,
                            cardWidth = adaptiveConfig.continueWatchingCardWidth,
                        )
                    }
                }

                // Media row sections
                rowSections.forEach { section ->
                    item(key = section.key, contentType = section.contentType) {
                        val imageProvider = imageProviders.getValue(section.imageSelector)
                        val title = stringResource(id = section.titleRes)

                        when (section.rowKind) {
                            HomeRowKind.POSTER -> {
                                PosterRowSection(
                                    title = title,
                                    items = section.items,
                                    getImageUrl = imageProvider,
                                    onItemClick = stableOnItemClick,
                                    onItemLongPress = stableOnItemLongPress,
                                    cardWidth = adaptiveConfig.posterCardWidth,
                                )
                            }
                            HomeRowKind.SQUARE -> {
                                SquareRowSection(
                                    title = title,
                                    items = section.items,
                                    getImageUrl = imageProvider,
                                    onItemClick = stableOnItemClick,
                                    onItemLongPress = stableOnItemLongPress,
                                    cardWidth = adaptiveConfig.mediaCardWidth,
                                )
                            }
                            HomeRowKind.MEDIA -> {
                                MediaRowSection(
                                    title = title,
                                    items = section.items,
                                    getImageUrl = imageProvider,
                                    onItemClick = stableOnItemClick,
                                    onItemLongPress = stableOnItemLongPress,
                                    cardWidth = adaptiveConfig.mediaCardWidth,
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ViewingMoodWidget(viewingMood: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Viewing Vibe",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = viewingMood,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * Tablet-optimized home layout using grids for better space utilization.
 */
@OptInAppExperimentalApis
@Composable
private fun TabletHomeLayout(
    contentLists: HomeContentLists,
    adaptiveConfig: com.rpeters.jellyfin.ui.adaptive.AdaptiveLayoutConfig,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit,
    unknownText: String,
    viewingMood: String?,
) {
    val gridColumns = adaptiveConfig.gridColumns
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(adaptiveConfig.sectionSpacing),
    ) {
        // Hero Carousel
        if (contentLists.featuredItems.isNotEmpty()) {
            item(key = "featured", contentType = "carousel") {
                val featured = remember(contentLists.featuredItems, unknownText) {
                    contentLists.featuredItems.map {
                        it.toCarouselItem(
                            titleOverride = it.name ?: unknownText,
                            subtitleOverride = itemSubtitle(it),
                            imageUrl = getBackdropUrl(it) ?: getSeriesImageUrl(it)
                                ?: getImageUrl(it) ?: "",
                        )
                    }
                }
                ExpressiveHeroCarousel(
                    items = featured,
                    onItemClick = { selected ->
                        contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                            ?.let(onItemClick)
                    },
                    onPlayClick = { selected ->
                        contentLists.featuredItems.firstOrNull { it.id.toString() == selected.id }
                            ?.let(onItemClick)
                    },
                    heroHeight = adaptiveConfig.heroHeight,
                    horizontalPadding = adaptiveConfig.heroHorizontalPadding,
                    pageSpacing = adaptiveConfig.heroPageSpacing,
                )
            }
        }

        // Viewing Mood Widget
        if (viewingMood != null) {
            item(key = "viewing_mood", contentType = "ai_widget") {
                ViewingMoodWidget(viewingMood = viewingMood)
            }
        }

        // Continue Watching Grid
        if (contentLists.continueWatching.isNotEmpty()) {
            item(key = "continue_watching_header", contentType = "section_header") {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "continue_watching_grid", contentType = "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .height((200.dp * ((contentLists.continueWatching.size + gridColumns - 1) / gridColumns)))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(
                        items = contentLists.continueWatching,
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = { getSeriesImageUrl(item) ?: getImageUrl(item) },
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }

        // Next Up Grid
        if (contentLists.recentEpisodes.isNotEmpty()) {
            item(key = "next_up_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_next_up),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "next_up_grid", contentType = "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .height((200.dp * ((contentLists.recentEpisodes.size.coerceAtMost(gridColumns * 2) + gridColumns - 1) / gridColumns)))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(
                        items = contentLists.recentEpisodes.take(gridColumns * 2),
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = { getSeriesImageUrl(item) ?: getImageUrl(item) },
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }

        // Recently Added Movies Row
        if (contentLists.recentMovies.isNotEmpty()) {
            item(key = "recent_movies_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_recently_added_movies),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "recent_movies_row", contentType = "row") {
                val rowState = rememberLazyListState()
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = contentLists.recentMovies,
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                            cardWidth = adaptiveConfig.posterCardWidth,
                        )
                    }
                }
            }
        }

        // Recently Added TV Shows Row
        if (contentLists.recentTVShows.isNotEmpty()) {
            item(key = "recent_tv_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_recently_added_tv_shows),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "recent_tv_row", contentType = "row") {
                val rowState = rememberLazyListState()
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = contentLists.recentTVShows,
                        key = { it.getItemKey() },
                    ) { item ->
                        PosterMediaCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                            cardWidth = adaptiveConfig.posterCardWidth,
                        )
                    }
                }
            }
        }

        // Recently Added Videos (Horizontal cards row)
        if (contentLists.recentVideos.isNotEmpty()) {
            item(key = "recent_videos_header", contentType = "section_header") {
                Text(
                    text = stringResource(id = R.string.home_recently_added_stuff),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "recent_videos_row", contentType = "row") {
                val rowState = rememberLazyListState()
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = contentLists.recentVideos,
                        key = { it.getItemKey() },
                    ) { item ->
                        MediaCard(
                            item = item,
                            getImageUrl = { getBackdropUrl(item) ?: getImageUrl(item) },
                            onClick = onItemClick,
                            onLongPress = onItemLongPress,
                            cardWidth = adaptiveConfig.mediaCardWidth,
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// Internal data structures and helper methods moved from HomeScreen.kt

internal data class HomeContentLists(
    val continueWatching: List<BaseItemDto> = emptyList(),
    val recentMovies: List<BaseItemDto> = emptyList(),
    val recentTVShows: List<BaseItemDto> = emptyList(),
    val featuredItems: List<BaseItemDto> = emptyList(),
    val recentEpisodes: List<BaseItemDto> = emptyList(),
    val recentMusic: List<BaseItemDto> = emptyList(),
    val recentVideos: List<BaseItemDto> = emptyList(),
)

internal enum class HomeImageSelector {
    DEFAULT,
    SERIES_OR_DEFAULT,
    BACKDROP_OR_DEFAULT,
}

internal enum class HomeRowKind {
    POSTER,
    SQUARE,
    MEDIA,
}

internal data class HomeRowSectionConfig(
    val key: String,
    val contentType: String,
    @StringRes val titleRes: Int,
    val items: List<BaseItemDto>,
    val rowKind: HomeRowKind,
    val imageSelector: HomeImageSelector,
)

internal object HomeSectionKeys {
    const val CONTINUE_WATCHING = "continue_watching"
    const val NEXT_UP = "next_up"
    const val RECENT_MOVIES = "recent_movies"
    const val RECENT_TV_SHOWS = "recent_tv_shows"
    const val RECENT_MUSIC = "recent_music"
    const val RECENT_STUFF = "recent_stuff"
}

internal object HomeSectionContentTypes {
    const val POSTER_ROW = "poster_row"
    const val MEDIA_ROW = "media_row"
    const val SQUARE_ROW = "square_row"
}

internal fun getContinueWatchingItems(appState: MainAppState, limit: Int): List<BaseItemDto> {
    return appState.continueWatching.take(limit)
}

internal fun itemSubtitle(item: BaseItemDto): String = when (item.type) {
    BaseItemKind.EPISODE -> item.seriesName ?: ""
    BaseItemKind.SERIES -> item.productionYear?.toString() ?: ""
    BaseItemKind.AUDIO -> item.artists?.firstOrNull() ?: ""
    BaseItemKind.MOVIE -> item.productionYear?.toString() ?: ""
    else -> ""
}

internal fun BaseItemDto.toCarouselItem(
    titleOverride: String,
    subtitleOverride: String,
    imageUrl: String,
): CarouselItem = CarouselItem(
    id = this.id.toString(),
    title = titleOverride,
    subtitle = subtitleOverride,
    imageUrl = imageUrl,
)
