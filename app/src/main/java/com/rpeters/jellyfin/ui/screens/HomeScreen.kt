package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.components.ExpressiveHeroCarousel
import com.rpeters.jellyfin.ui.components.MediaCard
import com.rpeters.jellyfin.ui.components.MediaItemActionsSheet
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.components.WatchProgressBar
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.screens.home.LibraryGridSection
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SurfaceCoordinatorViewModel
import com.rpeters.jellyfin.utils.PerformanceTracker
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun HomeScreen(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onLibraryClick: (BaseItemDto) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    viewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
) {
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val managementEnabled = libraryActionPrefs.enableManagementActions
    val managementDisabledMessage = stringResource(id = R.string.library_actions_management_disabled)

    val handleItemLongPress: (BaseItemDto) -> Unit = { item ->
        if (managementEnabled) {
            selectedItem = item
            showManageSheet = true
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = managementDisabledMessage)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            HomeTopBar(
                currentServer = currentServer,
                showBackButton = showBackButton,
                onBackClick = onBackClick,
                onRefresh = onRefresh,
                onSettingsClick = onSettingsClick,
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        // Performance monitoring
        PerformanceTracker(
            enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
            intervalMs = 30000, // 30 seconds
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            HomeContent(
                appState = appState,
                currentServer = currentServer,
                onRefresh = onRefresh,
                getImageUrl = getImageUrl,
                getBackdropUrl = getBackdropUrl,
                getSeriesImageUrl = getSeriesImageUrl,
                onItemClick = onItemClick,
                onItemLongPress = handleItemLongPress,
                onLibraryClick = onLibraryClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Show media actions sheet when item is long-pressed
    if (showManageSheet && selectedItem != null) {
        val item = selectedItem!!
        val itemName = item.name ?: stringResource(id = R.string.unknown)
        val deleteSuccessMessage = stringResource(id = R.string.library_actions_delete_success, itemName)
        val deleteFailureTemplate = stringResource(id = R.string.library_actions_delete_failure, itemName, "%s")
        val refreshRequestedMessage = stringResource(id = R.string.library_actions_refresh_requested)

        MediaItemActionsSheet(
            item = item,
            sheetState = sheetState,
            onDismiss = {
                showManageSheet = false
                selectedItem = null
            },
            onPlay = {
                // TODO: Implement play functionality
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Play functionality coming soon")
                }
            },
            onDelete = { _, _ ->
                viewModel.deleteItem(item) { success, message ->
                    coroutineScope.launch {
                        val text = if (success) {
                            deleteSuccessMessage
                        } else {
                            String.format(deleteFailureTemplate, message ?: "")
                        }
                        snackbarHostState.showSnackbar(text)
                    }
                }
            },
            onRefreshMetadata = { _, _ ->
                viewModel.refreshItemMetadata(item) { success, message ->
                    coroutineScope.launch {
                        val text = if (success) {
                            refreshRequestedMessage
                        } else {
                            "Failed to refresh metadata: ${message ?: "Unknown error"}"
                        }
                        snackbarHostState.showSnackbar(text)
                    }
                }
            },
            onToggleWatched = {
                viewModel.toggleWatchedStatus(item)
            },
            managementEnabled = managementEnabled,
        )
    }
}

private fun itemSubtitle(item: BaseItemDto): String = when (item.type?.toString()) {
    "Episode" -> item.seriesName ?: ""
    "Series" -> item.productionYear?.toString() ?: ""
    "Audio" -> item.artists?.firstOrNull() ?: ""
    "Movie" -> item.productionYear?.toString() ?: ""
    else -> ""
}

private fun BaseItemDto.toCarouselItem(
    titleOverride: String,
    subtitleOverride: String,
    imageUrl: String,
): CarouselItem = CarouselItem(
    id = this.id?.toString() ?: (this.name ?: "") + hashCode(),
    title = titleOverride,
    subtitle = subtitleOverride,
    imageUrl = imageUrl,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    currentServer: JellyfinServer?,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = currentServer?.name ?: stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        navigationIcon = {
            if (showBackButton) {
                Surface(
                    onClick = onBackClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_up),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        actions = {
            Surface(
                onClick = onRefresh,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onSettingsClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(id = R.string.settings),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(8.dp),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    // ✅ DEBUG: Log received state for UI troubleshooting
    LaunchedEffect(appState.libraries.size, appState.recentlyAddedByTypes.size) {
        if (com.rpeters.jellyfin.BuildConfig.DEBUG) {
            android.util.Log.d(
                "HomeScreen",
                "Received state - Libraries: ${appState.libraries.size}, RecentlyAddedByTypes: ${appState.recentlyAddedByTypes.mapValues { it.value.size }}",
            )
            appState.libraries.forEachIndexed { index, library ->
                android.util.Log.d(
                    "HomeScreen",
                    "Library $index: ${library.name} (${library.collectionType})",
                )
            }
        }
    }
    // Precompute derived data to minimize recompositions during scroll
    val continueWatchingItems by remember(appState.allItems) {
        mutableStateOf(
            getContinueWatchingItems(appState),
        )
    }
    val surfaceCoordinatorViewModel: SurfaceCoordinatorViewModel = hiltViewModel()
    val recentMovies = remember(appState.recentlyAddedByTypes) {
        appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name]?.take(8) ?: emptyList()
    }
    val recentTVShows = remember(appState.recentlyAddedByTypes) {
        appState.recentlyAddedByTypes[BaseItemKind.SERIES.name]?.take(8) ?: emptyList()
    }
    val featuredItems by remember(
        recentMovies,
        recentTVShows,
    ) { mutableStateOf((recentMovies + recentTVShows).take(10)) }
    val recentEpisodes = remember(appState.recentlyAddedByTypes) {
        appState.recentlyAddedByTypes[BaseItemKind.EPISODE.name]?.take(15) ?: emptyList()
    }
    val recentMusic = remember(appState.recentlyAddedByTypes) {
        appState.recentlyAddedByTypes[BaseItemKind.AUDIO.name]?.take(15) ?: emptyList()
    }

    LaunchedEffect(surfaceCoordinatorViewModel) {
        snapshotFlow {
            continueWatchingItems.mapNotNull { item ->
                val id = item.id?.toString() ?: return@mapNotNull null
                Triple(id, item.name, item.seriesName)
            } to continueWatchingItems
        }
            .distinctUntilChangedBy { it.first }
            .collectLatest { (_, items) ->
                surfaceCoordinatorViewModel.updateContinueWatching(items)
            }
    }

    PullToRefreshBox(
        isRefreshing = appState.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "home_header", contentType = "header") { HomeHeader(currentServer) }

            // Continue Watching Section
            if (continueWatchingItems.isNotEmpty()) {
                item(key = "continue_watching", contentType = "continueWatching") {
                    ContinueWatchingSection(
                        items = continueWatchingItems,
                        getImageUrl = getImageUrl,
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                    )
                }
            }

            if (featuredItems.isNotEmpty()) {
                item(key = "featured", contentType = "carousel") {
                    val featured = remember(featuredItems) {
                        featuredItems.map {
                            it.toCarouselItem(
                                titleOverride = it.name ?: "Unknown",
                                subtitleOverride = itemSubtitle(it),
                                imageUrl = getBackdropUrl(it) ?: getSeriesImageUrl(it)
                                    ?: getImageUrl(it) ?: "",
                            )
                        }
                    }
                    ExpressiveHeroCarousel(
                        items = featured,
                        onItemClick = { selected ->
                            featuredItems.firstOrNull { it.id?.toString() == selected.id }
                                ?.let(onItemClick)
                        },
                        onPlayClick = { selected ->
                            featuredItems.firstOrNull { it.id?.toString() == selected.id }
                                ?.let(onItemClick)
                        },
                    )
                }
            }

            if (appState.libraries.isNotEmpty()) {
                item(key = "libraries", contentType = "libraries") {
                    val orderedLibraries by remember(appState.libraries) {
                        mutableStateOf(
                            appState.libraries.sortedBy { library ->
                                when (
                                    library.collectionType?.toString()
                                        ?.lowercase(Locale.getDefault())
                                ) {
                                    "movies" -> 0
                                    "tvshows" -> 1
                                    "music" -> 2
                                    else -> 3
                                }
                            },
                        )
                    }
                    LibraryGridSection(
                        libraries = orderedLibraries,
                        getImageUrl = getImageUrl,
                        onLibraryClick = onLibraryClick,
                        title = "Libraries",
                    )
                }
            }

            if (recentMovies.isNotEmpty()) {
                item(key = "recent_movies", contentType = "poster_row") {
                    PosterRowSection(
                        title = "Recently Added Movies",
                        items = recentMovies.take(15),
                        getImageUrl = getImageUrl,
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                    )
                }
            }

            if (recentTVShows.isNotEmpty()) {
                item(key = "recent_tvshows", contentType = "poster_row") {
                    PosterRowSection(
                        title = "Recently Added TV Shows",
                        items = recentTVShows.take(15),
                        getImageUrl = getImageUrl,
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                    )
                }
            }

            if (recentEpisodes.isNotEmpty()) {
                item(key = "recent_episodes", contentType = "poster_row") {
                    PosterRowSection(
                        title = "Recently Added TV Episodes",
                        items = recentEpisodes.take(15),
                        getImageUrl = { item -> getSeriesImageUrl(item) ?: getImageUrl(item) },
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                    )
                }
            }

            if (recentMusic.isNotEmpty()) {
                item(key = "recent_music", contentType = "music_row") {
                    SquareRowSection(
                        title = "Recently Added Music",
                        items = recentMusic.take(15),
                        getImageUrl = getImageUrl,
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                    )
                }
            }

            val recentVideos =
                appState.recentlyAddedByTypes[BaseItemKind.VIDEO.name]?.take(15) ?: emptyList()
            if (recentVideos.isNotEmpty()) {
                item(key = "recent_home_videos", contentType = "media_row") {
                    MediaRowSection(
                        title = "Recently Added Home Videos",
                        items = recentVideos,
                        getImageUrl = { item -> getBackdropUrl(item) ?: getImageUrl(item) },
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(currentServer: JellyfinServer?) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Text(
                        text = "Welcome back!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                currentServer?.let { server ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connected to ${server.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultsContent(
    searchResults: List<BaseItemDto>,
    isSearching: Boolean,
    errorMessage: String?,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isSearching) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = "Searching...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        errorMessage?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        if (searchResults.isEmpty() && !isSearching && errorMessage == null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val groupedResults = searchResults.groupBy { it.type }
        groupedResults.forEach { (type, items) ->
            item {
                Text(
                    text = when (type) {
                        BaseItemKind.MOVIE -> "Movies"
                        BaseItemKind.SERIES -> "TV Shows"
                        BaseItemKind.EPISODE -> "Episodes"
                        BaseItemKind.AUDIO -> "Music"
                        BaseItemKind.MUSIC_ALBUM -> "Albums"
                        BaseItemKind.MUSIC_ARTIST -> "Artists"
                        BaseItemKind.BOOK -> "Books"
                        BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                        else -> type?.toString() ?: "Other"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(
                items = items.chunked(2),
                key = { rowItems -> rowItems.firstOrNull()?.getItemKey() ?: "" },
            ) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { item ->
                        // Use poster cards for movies and TV shows, regular cards for others
                        if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.SERIES) {
                            PosterMediaCard(
                                item = item,
                                getImageUrl = getImageUrl,
                                modifier = Modifier.weight(1f),
                                showTitle = true,
                                showMetadata = true,
                            )
                        } else {
                            MediaCard(
                                item = item,
                                getImageUrl = getImageUrl,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// Helper function to get continue watching items
private fun getContinueWatchingItems(appState: MainAppState): List<BaseItemDto> {
    return appState.allItems.filter { item ->
        val percentage = item.userData?.playedPercentage ?: 0.0
        percentage > 0.0 && percentage < 100.0 &&
            (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.EPISODE)
    }.sortedByDescending { it.userData?.lastPlayedDate }.take(8)
}

@Composable
private fun ContinueWatchingSection(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Section Header
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        // Horizontal scrolling list of continue watching items
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(
                items = items,
                key = { it.getItemKey() },
            ) { item ->
                ContinueWatchingCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val watchedPercentage = item.userData?.playedPercentage ?: 0.0

    ElevatedCard(
        modifier = modifier.width(160.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongPress(item) },
            ),
        ) {
            Box {
                OptimizedImage(
                    imageUrl = getImageUrl(item),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    size = ImageSize.POSTER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )

                WatchProgressBar(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                )
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name ?: "Unknown",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.type == BaseItemKind.EPISODE) {
                    Text(
                        text = buildString {
                            item.seriesName?.let { append("$it • ") }
                            item.parentIndexNumber?.let { season ->
                                item.indexNumber?.let { episode ->
                                    append("S${season}E$episode")
                                }
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = "${watchedPercentage.roundToInt()}% watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PosterRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { item ->
                PosterMediaCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    onClick = onItemClick,
                    onLongPress = onItemLongPress,
                    showTitle = true,
                    showMetadata = true,
                )
            }
        }
    }
}

@Composable
private fun SquareRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { item ->
                MediaCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    onClick = onItemClick,
                    onLongPress = onItemLongPress,
                )
            }
        }
    }
}

@Composable
private fun MediaRowSection(
    title: String,
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { item ->
                MediaCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    onClick = onItemClick,
                    onLongPress = onItemLongPress,
                )
            }
        }
    }
}
