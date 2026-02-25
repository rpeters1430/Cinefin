# Immersive Library Screens Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate code duplication across `ImmersiveMoviesScreen`, `ImmersiveTVShowsScreen`, and `ImmersiveHomeVideosScreen` by extracting a single shared `ImmersiveLibraryScreen` composable.

**Architecture:** Create `ImmersiveLibraryScreen.kt` with a shared display composable driven by config objects. Each of the three screens becomes a thin container that pre-sorts data and supplies config. The shared composable owns the hero carousel, adaptive grid, pagination trigger, pull-to-refresh, error/empty states, and floating header.

**Tech Stack:** Jetpack Compose, Hilt, Material3, OkHttp/Coil, Kotlin Coroutines, `kotlinx.coroutines.flow`

**Design doc:** `docs/plans/2026-02-24-immersive-library-screens-refactor-design.md`

---

## Key Files

| File | Action |
|------|--------|
| `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveLibraryScreen.kt` | **CREATE** — shared composable + config types |
| `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveMoviesScreen.kt` | **REPLACE** — thin container only |
| `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVShowsScreen.kt` | **REPLACE** — thin container only |
| `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeVideosScreen.kt` | **REPLACE** — proper container + ViewModel decoupling |
| `app/src/main/java/com/rpeters/jellyfin/ui/navigation/MediaNavGraph.kt` | **MODIFY** — update call sites for changed signatures |

### Context: What the shared component replaces

Both `ImmersiveMoviesScreen` and `ImmersiveTVShowsScreen` are ~300-line files that are nearly identical — both contain:
- `PerformanceMetricsTracker`
- Sort state (`selectedSort`, `showSortMenu`)
- `LaunchedEffect` pagination trigger
- `Box` > `ImmersiveScaffold` > `ExpressivePullToRefreshBox`
- `LazyVerticalGrid` with hero carousel item + grid items
- Floating header Row (back + sort dropdown)

`ImmersiveHomeVideosScreen` has the same outer structure but diverges in:
- Non-functional Settings icon (remove)
- Sort menu hidden in non-visible top bar (move to floating header)
- No error state display (add)
- No rendered carousel (add — `featuredVideos` was computed but unused)
- ViewModel injected directly into display composable (add proper container)

---

## Task 1: Create `ImmersiveLibraryScreen.kt` (shared component)

**File to create:** `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveLibraryScreen.kt`

**Step 1: Create the file with config types and shared composable**

```kotlin
package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveErrorState
import com.rpeters.jellyfin.ui.components.ExpressivePullToRefreshBox
import com.rpeters.jellyfin.ui.components.ExpressiveSimpleEmptyState
import com.rpeters.jellyfin.ui.components.immersive.FabAction
import com.rpeters.jellyfin.ui.components.immersive.FabOrientation
import com.rpeters.jellyfin.ui.components.immersive.FloatingActionGroup
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveHeroCarousel
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveScaffold
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.BaseItemDto

/** Theme and empty-state configuration for [ImmersiveLibraryScreen]. */
data class ImmersiveLibraryConfig(
    val themeColor: Color,
    val emptyStateIcon: ImageVector,
    val emptyStateTitle: String,
    val emptyStateSubtitle: String,
)

/** A single entry in the sort dropdown for [ImmersiveLibraryScreen]. */
data class ImmersiveSortOption(
    val labelRes: Int,
    val key: String,
)

/**
 * Shared immersive library browse screen used by Movies, TV Shows, and Home Videos.
 *
 * Callers are responsible for:
 * - Pre-sorting [items] before passing them in
 * - Building [featuredItems] for the hero carousel (pass empty list to hide carousel)
 * - Providing [buildCarouselItem] to map each featured item to carousel metadata
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveLibraryScreen(
    items: List<BaseItemDto>,
    featuredItems: List<BaseItemDto>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    config: ImmersiveLibraryConfig,
    sortOptions: List<ImmersiveSortOption>,
    selectedSortIndex: Int,
    onSortSelected: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (String) -> Unit,
    onCarouselItemClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    buildCarouselItem: (BaseItemDto) -> CarouselItem?,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    val carouselItems = remember(featuredItems) {
        featuredItems.mapNotNull { buildCarouselItem(it) }
    }

    var showSortMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, items.size, hasMoreItems, isLoadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val nearEnd = lastVisibleIndex >= (items.lastIndex - 8).coerceAtLeast(0)
                if (nearEnd && hasMoreItems && !isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ImmersiveScaffold(
            topBarVisible = false,
            topBarTitle = "",
            topBarTranslucent = false,
            floatingActionButton = {
                FloatingActionGroup(
                    orientation = FabOrientation.Vertical,
                    primaryAction = FabAction(
                        icon = Icons.Default.Search,
                        contentDescription = "Search",
                        onClick = onSearchClick,
                    ),
                    secondaryActions = emptyList(),
                )
            },
        ) { _ ->
            ExpressivePullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicatorColor = config.themeColor,
                useWavyIndicator = true,
            ) {
                when {
                    errorMessage != null -> {
                        ExpressiveErrorState(
                            title = "Error Loading Library",
                            message = errorMessage,
                            icon = config.emptyStateIcon,
                            onRetry = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    items.isEmpty() && !isLoading -> {
                        ExpressiveSimpleEmptyState(
                            icon = config.emptyStateIcon,
                            title = config.emptyStateTitle,
                            subtitle = config.emptyStateSubtitle,
                            iconTint = config.themeColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 0.dp,
                                start = ImmersiveDimens.SpacingRowTight,
                                end = ImmersiveDimens.SpacingRowTight,
                                bottom = 120.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                            horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                        ) {
                            if (carouselItems.isNotEmpty()) {
                                item(
                                    key = "library_hero",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .offset(x = -ImmersiveDimens.SpacingRowTight)
                                            .width(LocalConfiguration.current.screenWidthDp.dp)
                                            .height(ImmersiveDimens.HeroHeightPhone + 60.dp)
                                            .clipToBounds(),
                                    ) {
                                        ImmersiveHeroCarousel(
                                            items = carouselItems,
                                            onItemClick = { onCarouselItemClick(it.id) },
                                            onPlayClick = { onCarouselItemClick(it.id) },
                                            pageSpacing = 0.dp,
                                        )
                                    }
                                }
                            }

                            items(
                                items = items,
                                key = { it.id.toString() },
                            ) { item ->
                                ImmersiveMediaCard(
                                    title = item.name ?: "Unknown",
                                    subtitle = item.productionYear?.toString() ?: "",
                                    imageUrl = getImageUrl(item) ?: "",
                                    onCardClick = { onItemClick(item.id.toString()) },
                                    onPlayClick = { onItemClick(item.id.toString()) },
                                    cardSize = ImmersiveCardSize.SMALL,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating header: back button + sort dropdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }

            Box {
                Surface(
                    onClick = { showSortMenu = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    sortOptions.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(id = option.labelRes)) },
                            onClick = {
                                onSortSelected(index)
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}
```

**Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | head -40
```
Expected: No errors for the new file. (Other files will still have errors until Tasks 2–5.)

---

## Task 2: Replace `ImmersiveMoviesScreen.kt`

**File to replace:** `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveMoviesScreen.kt`

Note: `onMovieClick` changes from `(BaseItemDto) -> Unit` to `(String) -> Unit` — the navGraph call site will be updated in Task 5.

**Step 1: Replace the entire file**

```kotlin
package com.rpeters.jellyfin.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.theme.MovieRed
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel

private val moviesSortOptions = listOf(
    ImmersiveSortOption(labelRes = R.string.sort_title_asc, key = "alphabetical"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_desc, key = "recently_added"),
    ImmersiveSortOption(labelRes = R.string.sort_year_desc, key = "year_newest"),
)

@OptInAppExperimentalApis
@Composable
fun ImmersiveMoviesScreenContainer(
    onMovieClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val movies = viewModel.getLibraryTypeData(LibraryType.MOVIES)
    val libraryId = viewModel.getLibraryIdForType(LibraryType.MOVIES)
    val paginationState = libraryId?.let { appState.libraryPaginationState[it] }

    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortedMovies = remember(movies, selectedSortIndex) {
        when (selectedSortIndex) {
            0 -> movies.sortedBy { (it.sortName ?: it.name).orEmpty().lowercase() }
            1 -> movies.sortedByDescending { it.dateCreated }
            2 -> movies.sortedByDescending { it.productionYear ?: 0 }
            else -> movies
        }
    }
    val featuredMovies = remember(movies) {
        movies.sortedByDescending { it.dateCreated }.take(5)
    }

    LaunchedEffect(Unit) {
        if (movies.isEmpty()) {
            viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = false)
        }
    }

    ImmersiveLibraryScreen(
        items = sortedMovies,
        featuredItems = featuredMovies,
        isLoading = appState.isLoadingMovies,
        isLoadingMore = paginationState?.isLoadingMore ?: false,
        hasMoreItems = paginationState?.hasMore ?: false,
        config = ImmersiveLibraryConfig(
            themeColor = MovieRed,
            emptyStateIcon = Icons.Default.Movie,
            emptyStateTitle = "No movies found",
            emptyStateSubtitle = "Try adding some movies to your library",
        ),
        sortOptions = moviesSortOptions,
        selectedSortIndex = selectedSortIndex,
        onSortSelected = { selectedSortIndex = it },
        onLoadMore = { libraryId?.let(viewModel::loadMoreLibraryItems) },
        onItemClick = onMovieClick,
        onCarouselItemClick = onMovieClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.MOVIES, forceRefresh = true) },
        onSearchClick = onSearchClick,
        onBackClick = onBackClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        buildCarouselItem = { movie ->
            CarouselItem(
                id = movie.id.toString(),
                title = movie.name ?: "Unknown",
                subtitle = movie.productionYear?.toString() ?: "",
                imageUrl = viewModel.getBackdropUrl(movie) ?: viewModel.getImageUrl(movie) ?: "",
            )
        },
        modifier = modifier,
    )
}
```

**Step 2: Verify it compiles (with expected errors for Tasks 3–5)**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|ImmersiveMoviesScreen"
```
Expected: No errors for `ImmersiveMoviesScreen.kt`. Errors for `ImmersiveTVShowsScreen`, `ImmersiveHomeVideosScreen`, and `MediaNavGraph` are expected until Tasks 3–5.

---

## Task 3: Replace `ImmersiveTVShowsScreen.kt`

**File to replace:** `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVShowsScreen.kt`

Note: `onTVShowClick` was already `(String) -> Unit` — signature unchanged.

**Step 1: Replace the entire file**

```kotlin
package com.rpeters.jellyfin.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemKind

private val tvShowsSortOptions = listOf(
    ImmersiveSortOption(labelRes = R.string.sort_title_asc_shows, key = "alphabetical"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_desc_shows, key = "recently_added"),
    ImmersiveSortOption(labelRes = R.string.sort_year_desc_shows, key = "year_newest"),
)

@OptInAppExperimentalApis
@Composable
fun ImmersiveTVShowsScreenContainer(
    onTVShowClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()
    val tvShows = viewModel.getLibraryTypeData(LibraryType.TV_SHOWS)
    val recentEpisodes = appState.recentlyAddedByTypes[BaseItemKind.EPISODE.name] ?: emptyList()
    val libraryId = viewModel.getLibraryIdForType(LibraryType.TV_SHOWS)
    val paginationState = libraryId?.let { appState.libraryPaginationState[it] }

    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortedShows = remember(tvShows, selectedSortIndex) {
        when (selectedSortIndex) {
            0 -> tvShows.sortedBy { (it.sortName ?: it.name).orEmpty().lowercase() }
            1 -> tvShows.sortedByDescending { it.dateCreated }
            2 -> tvShows.sortedByDescending { it.productionYear ?: 0 }
            else -> tvShows
        }
    }
    val featuredShows = remember(tvShows, recentEpisodes) {
        val fromRecentEpisodes = recentEpisodes
            .filter { it.seriesId != null || it.seriesName != null || it.name != null }
            .distinctBy { it.seriesId ?: it.seriesName ?: it.name }
        if (fromRecentEpisodes.isNotEmpty()) fromRecentEpisodes.take(5)
        else tvShows.sortedByDescending { it.dateCreated }.take(5)
    }

    LaunchedEffect(Unit) {
        if (tvShows.isEmpty()) {
            viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = false)
        }
    }

    ImmersiveLibraryScreen(
        items = sortedShows,
        featuredItems = featuredShows,
        isLoading = appState.isLoadingTVShows,
        isLoadingMore = paginationState?.isLoadingMore ?: false,
        hasMoreItems = paginationState?.hasMore ?: false,
        config = ImmersiveLibraryConfig(
            themeColor = SeriesBlue,
            emptyStateIcon = Icons.Default.Tv,
            emptyStateTitle = stringResource(id = R.string.no_tv_shows_found),
            emptyStateSubtitle = stringResource(id = R.string.adjust_tv_shows_filters_hint),
        ),
        sortOptions = tvShowsSortOptions,
        selectedSortIndex = selectedSortIndex,
        onSortSelected = { selectedSortIndex = it },
        onLoadMore = { libraryId?.let(viewModel::loadMoreLibraryItems) },
        onItemClick = onTVShowClick,
        onCarouselItemClick = onTVShowClick,
        onRefresh = { viewModel.loadLibraryTypeData(LibraryType.TV_SHOWS, forceRefresh = true) },
        onSearchClick = onSearchClick,
        onBackClick = onBackClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        buildCarouselItem = { item ->
            CarouselItem(
                id = (item.seriesId ?: item.id).toString(),
                title = item.seriesName ?: item.name ?: "Unknown",
                subtitle = if (item.seriesId != null) "New Episode Added" else (item.productionYear?.toString() ?: ""),
                imageUrl = viewModel.getBackdropUrl(item) ?: viewModel.getSeriesImageUrl(item) ?: viewModel.getImageUrl(item) ?: "",
            )
        },
        modifier = modifier,
    )
}
```

**Note:** `stringResource` cannot be called from a remember block. The config object must be constructed inside the Composable (not inside `remember`). The `emptyStateTitle` and `emptyStateSubtitle` strings are known strings — create them as `val` before the `ImmersiveLibraryScreen` call:

```kotlin
val emptyTitle = stringResource(id = R.string.no_tv_shows_found)
val emptySubtitle = stringResource(id = R.string.adjust_tv_shows_filters_hint)
```

Then pass `emptyStateTitle = emptyTitle, emptyStateSubtitle = emptySubtitle`.

**Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|ImmersiveTVShows"
```
Expected: No errors for `ImmersiveTVShowsScreen.kt`.

---

## Task 4: Replace `ImmersiveHomeVideosScreen.kt`

**File to replace:** `app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeVideosScreen.kt`

Changes vs current implementation:
- Removes ViewModel direct injection from display composable — now a proper container
- Removes non-functional Settings icon — replaced with sort dropdown in floating header
- Sort menu moved from hidden top bar to the floating header (consistent with Movies/TV Shows)
- Carousel now renders (was computed but unused before)
- Error state display is now wired up
- `sortHomeVideos` helper function stays (private, moved inside the file)

**Step 1: Replace the entire file**

```kotlin
package com.rpeters.jellyfin.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.theme.PhotoYellow
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

private val homeVideosSortOptions = listOf(
    ImmersiveSortOption(labelRes = R.string.sort_title_asc, key = "name_asc"),
    ImmersiveSortOption(labelRes = R.string.sort_title_desc, key = "name_desc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_desc, key = "date_added_desc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_added_asc, key = "date_added_asc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_created_desc, key = "date_created_desc"),
    ImmersiveSortOption(labelRes = R.string.sort_date_created_asc, key = "date_created_asc"),
)

@OptInAppExperimentalApis
@Composable
fun ImmersiveHomeVideosScreenContainer(
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()

    val homeVideosLibraries = remember(appState.libraries) {
        appState.libraries.filter { it.collectionType == CollectionType.HOMEVIDEOS }
    }

    LaunchedEffect(homeVideosLibraries) {
        homeVideosLibraries.forEach { library ->
            val currentItems = appState.itemsByLibrary[library.id.toString()] ?: emptyList()
            if (currentItems.isEmpty()) {
                viewModel.loadHomeVideos(library.id.toString())
            }
        }
    }

    val homeVideosLibraryIds = remember(homeVideosLibraries) {
        homeVideosLibraries.map { it.id.toString() }
    }

    val homeVideosItems = remember(appState.itemsByLibrary, homeVideosLibraries) {
        homeVideosLibraries
            .flatMap { appState.itemsByLibrary[it.id.toString()] ?: emptyList() }
            .filter { it.type == BaseItemKind.VIDEO || it.type == BaseItemKind.MOVIE }
    }

    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortedVideos = remember(homeVideosItems, selectedSortIndex) {
        sortHomeVideosByIndex(homeVideosItems, selectedSortIndex)
    }

    val featuredVideos = remember(sortedVideos) { sortedVideos.take(5) }

    val isLoadingMore = remember(appState.libraryPaginationState, homeVideosLibraryIds) {
        homeVideosLibraryIds.any { appState.libraryPaginationState[it]?.isLoadingMore == true }
    }
    val hasMoreItems = remember(appState.libraryPaginationState, homeVideosLibraryIds) {
        homeVideosLibraryIds.any { appState.libraryPaginationState[it]?.hasMore == true }
    }

    ImmersiveLibraryScreen(
        items = sortedVideos,
        featuredItems = featuredVideos,
        isLoading = appState.isLoading,
        isLoadingMore = isLoadingMore,
        hasMoreItems = hasMoreItems,
        config = ImmersiveLibraryConfig(
            themeColor = PhotoYellow,
            emptyStateIcon = Icons.Default.Photo,
            emptyStateTitle = stringResource(id = R.string.no_home_videos_found),
            emptyStateSubtitle = stringResource(id = R.string.adjust_home_videos_filters_hint),
        ),
        sortOptions = homeVideosSortOptions,
        selectedSortIndex = selectedSortIndex,
        onSortSelected = { selectedSortIndex = it },
        onLoadMore = { viewModel.loadMoreHomeVideos(homeVideosLibraries) },
        onItemClick = onItemClick,
        onCarouselItemClick = onItemClick,
        onRefresh = { viewModel.loadInitialData() },
        onSearchClick = { /* Home videos uses back nav for search */ },
        onBackClick = onBackClick,
        getImageUrl = { viewModel.getImageUrl(it) },
        buildCarouselItem = { video ->
            CarouselItem(
                id = video.id.toString(),
                title = video.name ?: "Home Video",
                subtitle = video.productionYear?.toString() ?: "",
                imageUrl = viewModel.getBackdropUrl(video) ?: viewModel.getImageUrl(video) ?: "",
            )
        },
        errorMessage = appState.errorMessage,
        modifier = modifier,
    )
}

private fun sortHomeVideosByIndex(videos: List<BaseItemDto>, index: Int): List<BaseItemDto> =
    when (index) {
        0 -> videos.sortedBy { it.sortName ?: it.name }
        1 -> videos.sortedByDescending { it.sortName ?: it.name }
        2 -> videos.sortedByDescending { it.dateCreated }
        3 -> videos.sortedBy { it.dateCreated }
        4 -> videos.sortedByDescending { it.premiereDate ?: it.dateCreated }
        5 -> videos.sortedBy { it.premiereDate ?: it.dateCreated }
        else -> videos
    }
```

**Note on `stringResource`:** Like Task 3, call `stringResource` as top-level `val` inside the composable before the `ImmersiveLibraryScreen` call — not inside `remember` blocks.

**Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|ImmersiveHomeVideos"
```
Expected: No errors for `ImmersiveHomeVideosScreen.kt`.

---

## Task 5: Update `MediaNavGraph.kt` call sites

**File to modify:** `app/src/main/java/com/rpeters/jellyfin/ui/navigation/MediaNavGraph.kt`

Three call sites need updating:

### Change 1: Movies — update `onMovieClick` lambda (was `(BaseItemDto) -> Unit`, now `(String) -> Unit`)

Find this block (around line 40):
```kotlin
ImmersiveMoviesScreenContainer(
    onMovieClick = { item ->
        item.id.let { movieId ->
            navController.navigate(Screen.MovieDetail.createRoute(movieId.toString()))
        }
    },
```

Replace `onMovieClick` lambda with:
```kotlin
ImmersiveMoviesScreenContainer(
    onMovieClick = { movieId ->
        navController.navigate(Screen.MovieDetail.createRoute(movieId))
    },
```

### Change 2: Home Videos — replace `ImmersiveHomeVideosScreen` with `ImmersiveHomeVideosScreenContainer`

Find the import line:
```kotlin
import com.rpeters.jellyfin.ui.screens.ImmersiveHomeVideosScreen
```
Replace with:
```kotlin
import com.rpeters.jellyfin.ui.screens.ImmersiveHomeVideosScreenContainer
```

Find the call site (around line 250):
```kotlin
ImmersiveHomeVideosScreen(
    onBackClick = { navController.popBackStack() },
    onItemClick = { id ->
        val item = appState.itemsByLibrary.values.flatten()
            .find { it.id.toString() == id }
        if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.VIDEO) {
            navController.navigate(Screen.HomeVideoDetail.createRoute(id))
        } else {
            navController.navigate(Screen.ItemDetail.createRoute(id))
        }
    },
    viewModel = viewModel,
)
```

Replace with:
```kotlin
ImmersiveHomeVideosScreenContainer(
    onBackClick = { navController.popBackStack() },
    onItemClick = { id ->
        val item = appState.itemsByLibrary.values.flatten()
            .find { it.id.toString() == id }
        if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.VIDEO) {
            navController.navigate(Screen.HomeVideoDetail.createRoute(id))
        } else {
            navController.navigate(Screen.ItemDetail.createRoute(id))
        }
    },
    viewModel = viewModel,
)
```

**Step 2: Full compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -i error
```
Expected: No errors.

**Step 3: Run unit tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: All existing tests pass (no tests reference the removed private composables).

**Step 4: Commit**

```bash
git add \
  app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveLibraryScreen.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveMoviesScreen.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveTVShowsScreen.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/screens/ImmersiveHomeVideosScreen.kt \
  app/src/main/java/com/rpeters/jellyfin/ui/navigation/MediaNavGraph.kt

git commit -m "refactor: extract shared ImmersiveLibraryScreen composable

- Create ImmersiveLibraryScreen.kt with shared display component
- ImmersiveMoviesScreen: replace display composable with thin container
- ImmersiveTVShowsScreen: replace display composable with thin container
- ImmersiveHomeVideosScreen: add proper container, fix sort/header/error/carousel
- MediaNavGraph: update Movies callback signature and HomeVideos container name
- ~600 lines reduced to ~400 lines; no behavior changes for Movies or TV Shows
- HomeVideos: carousel now renders, sort accessible from floating header, error state wired up"
```

---

## Task 6: Build debug APK and smoke-check

**Step 1: Build debug APK**

```bash
./gradlew assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

**Step 2: Manual smoke-check (if device/emulator available)**

```bash
./gradlew installDebug
```

Verify each screen:
1. Movies screen — hero carousel shows top 5, grid loads, sort dropdown has 3 options, back button works
2. TV Shows screen — hero carousel shows recent series, grid loads, sort dropdown has 3 options
3. Home Videos screen — carousel shows (if 5+ videos), sort dropdown has 6 options, back button shows (Settings icon is gone), error state displays if server unreachable

---

## Troubleshooting

**`stringResource` called in non-composable context:**
Move any `stringResource` calls to top-level `val` inside the `@Composable` function before they are used in data classes or `remember` blocks.

**`mutableIntStateOf` not found:**
Import `androidx.compose.runtime.mutableIntStateOf` (available since Compose 1.5).

**`viewModel.getSeriesImageUrl` not found in ImmersiveHomeVideosScreen:**
It's only used in the TV Shows container. Home Videos uses `viewModel.getBackdropUrl` or `viewModel.getImageUrl`.

**`viewModel.loadMoreHomeVideos` signature:**
The existing call is `viewModel.loadMoreHomeVideos(homeVideosLibraries)` — keep this as-is (the ViewModel method already accepts a list of library objects).

**Navigation doesn't reach new container name:**
Check that the import in `MediaNavGraph.kt` uses `ImmersiveHomeVideosScreenContainer` (not `ImmersiveHomeVideosScreen`).
