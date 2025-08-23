package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.ui.components.ExpressiveCompactCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ExpressiveCardType
import com.rpeters.jellyfin.ui.components.MediaCard
import com.rpeters.jellyfin.ui.screens.home.EnhancedContentCarousel
import com.rpeters.jellyfin.ui.screens.home.LibraryGridSection
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.utils.PerformanceTracker
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
) {
    Scaffold(
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
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

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
                color = MaterialTheme.colorScheme.primary
            )
        },
        navigationIcon = {
            if (showBackButton) {
                Surface(
                    onClick = onBackClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_up),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        actions = {
            Surface(
                onClick = onRefresh,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onSettingsClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(id = R.string.settings),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { HomeHeader(currentServer) }

        val recentMovies = appState.recentlyAddedByTypes["MOVIE"]?.take(8) ?: emptyList()
        val recentTVShows = appState.recentlyAddedByTypes["SERIES"]?.take(8) ?: emptyList()
        val featuredItems = (recentMovies + recentTVShows).take(10)

        if (featuredItems.isNotEmpty()) {
            item {
                EnhancedContentCarousel(
                    items = featuredItems,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getSeriesImageUrl = getSeriesImageUrl,
                    onItemClick = onItemClick,
                    title = "Featured",
                )
            }
        }

        if (appState.libraries.isNotEmpty()) {
            item {
                val orderedLibraries = appState.libraries.sortedBy { library ->
                    when (library.collectionType?.toString()?.lowercase()) {
                        "movies" -> 0
                        "tvshows" -> 1
                        "music" -> 2
                        else -> 3
                    }
                }
                LibraryGridSection(
                    libraries = orderedLibraries,
                    getImageUrl = getImageUrl,
                    onLibraryClick = onItemClick,
                    title = "Libraries",
                )
            }
        }

        if (recentMovies.isNotEmpty()) {
            item {
                EnhancedContentCarousel(
                    items = recentMovies.take(15),
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getSeriesImageUrl = getSeriesImageUrl,
                    onItemClick = onItemClick,
                    title = "Recently Added Movies",
                )
            }
        }

        val recentEpisodes = appState.recentlyAddedByTypes["EPISODE"]?.take(15) ?: emptyList()
        if (recentEpisodes.isNotEmpty()) {
            item {
                EnhancedContentCarousel(
                    items = recentEpisodes,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getSeriesImageUrl = getSeriesImageUrl,
                    onItemClick = onItemClick,
                    title = "Recently Added TV Episodes",
                )
            }
        }

        val recentMusic = appState.recentlyAddedByTypes["AUDIO"]?.take(15) ?: emptyList()
        if (recentMusic.isNotEmpty()) {
            item {
                EnhancedContentCarousel(
                    items = recentMusic,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getSeriesImageUrl = getSeriesImageUrl,
                    onItemClick = onItemClick,
                    title = "Recently Added Music",
                )
            }
        }

        val recentVideos = appState.recentlyAddedByTypes["VIDEO"]?.take(15) ?: emptyList()
        if (recentVideos.isNotEmpty()) {
            item {
                EnhancedContentCarousel(
                    items = recentVideos,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getSeriesImageUrl = getSeriesImageUrl,
                    onItemClick = onItemClick,
                    title = "Recently Added Home Videos",
                )
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
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(20.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = "Welcome back!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
                        MediaCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
