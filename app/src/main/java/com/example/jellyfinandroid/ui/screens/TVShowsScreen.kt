package com.example.jellyfinandroid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jellyfinandroid.ui.components.MediaCard
import com.example.jellyfinandroid.ui.theme.SeriesBlue
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

enum class TVShowFilter(val displayName: String) {
    ALL("All Shows"),
    FAVORITES("Favorites"),
    CONTINUING("Continuing"),
    ENDED("Ended"),
    RECENT("Recent"),
    UNWATCHED("Unwatched"),
    IN_PROGRESS("In Progress"),
    HIGH_RATED("High Rated");
    
    companion object {
        fun getAllFilters() = entries
    }
}

enum class TVShowSortOrder(val displayName: String) {
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    YEAR_DESC("Newest First"),
    YEAR_ASC("Oldest First"),
    RATING_DESC("Highest Rated"),
    RATING_ASC("Lowest Rated"),
    DATE_ADDED_DESC("Recently Added"),
    DATE_ADDED_ASC("Oldest Added"),
    LAST_PLAYED_DESC("Recently Watched"),
    EPISODE_COUNT_DESC("Most Episodes"),
    EPISODE_COUNT_ASC("Fewest Episodes");
    
    companion object {
        fun getDefault() = TITLE_ASC
        fun getAllSortOrders() = entries
    }
}

enum class TVShowViewMode {
    GRID,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVShowsScreen(
    onTVShowClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsState()
    var selectedFilter by remember { mutableStateOf(TVShowFilter.ALL) }
    var sortOrder by remember { mutableStateOf(TVShowSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(TVShowViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Filter TV shows from all items
    val tvShowItems = remember(appState.allItems) {
        appState.allItems.filter { it.type == BaseItemKind.SERIES }
    }
    
    // Apply filtering and sorting
    val filteredAndSortedTVShows = remember(tvShowItems, selectedFilter, sortOrder) {
        val filtered = when (selectedFilter) {
            TVShowFilter.ALL -> tvShowItems
            TVShowFilter.FAVORITES -> tvShowItems.filter { it.userData?.isFavorite == true }
            TVShowFilter.CONTINUING -> tvShowItems.filter { it.status == "Continuing" }
            TVShowFilter.ENDED -> tvShowItems.filter { it.status == "Ended" }
            TVShowFilter.RECENT -> tvShowItems.filter { 
                ((it.productionYear as? Number)?.toInt() ?: 0) >= 2020 
            }
            TVShowFilter.UNWATCHED -> tvShowItems.filter { 
                it.userData?.played != true 
            }
            TVShowFilter.IN_PROGRESS -> tvShowItems.filter { 
                (it.userData?.playedPercentage ?: 0.0) > 0.0 && 
                (it.userData?.playedPercentage ?: 0.0) < 100.0 
            }
            TVShowFilter.HIGH_RATED -> tvShowItems.filter { 
                ((it.communityRating as? Number)?.toDouble() ?: 0.0) >= 7.0 
            }
        }
        
        when (sortOrder) {
            TVShowSortOrder.TITLE_ASC -> filtered.sortedBy { it.sortName ?: it.name }
            TVShowSortOrder.TITLE_DESC -> filtered.sortedByDescending { it.sortName ?: it.name }
            TVShowSortOrder.YEAR_DESC -> filtered.sortedByDescending { 
                (it.productionYear as? Number)?.toInt() ?: 0 
            }
            TVShowSortOrder.YEAR_ASC -> filtered.sortedBy { 
                (it.productionYear as? Number)?.toInt() ?: 0 
            }
            TVShowSortOrder.RATING_DESC -> filtered.sortedByDescending { 
                (it.communityRating as? Number)?.toDouble() ?: 0.0 
            }
            TVShowSortOrder.RATING_ASC -> filtered.sortedBy { 
                (it.communityRating as? Number)?.toDouble() ?: 0.0 
            }
            TVShowSortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateCreated }
            TVShowSortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.dateCreated }
            TVShowSortOrder.LAST_PLAYED_DESC -> filtered.sortedByDescending { 
                it.userData?.lastPlayedDate 
            }
            TVShowSortOrder.EPISODE_COUNT_DESC -> filtered.sortedByDescending { 
                it.childCount ?: 0 
            }
            TVShowSortOrder.EPISODE_COUNT_ASC -> filtered.sortedBy { 
                it.childCount ?: 0 
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = SeriesBlue
                        )
                        Text(
                            text = "TV Shows",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (filteredAndSortedTVShows.isNotEmpty()) {
                            Text(
                                text = "(${filteredAndSortedTVShows.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // View mode toggle
                    SingleChoiceSegmentedButtonRow {
                        TVShowViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = TVShowViewMode.entries.size
                                ),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = SeriesBlue.copy(alpha = 0.2f),
                                    activeContentColor = SeriesBlue
                                )
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        TVShowViewMode.GRID -> Icons.Default.GridView
                                        TVShowViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                    },
                                    contentDescription = mode.name,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                    
                    // Sort menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort"
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            TVShowSortOrder.getAllSortOrders().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.displayName) },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    
                    IconButton(onClick = { viewModel.refreshLibraryItems() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(TVShowFilter.getAllFilters()) { filter ->
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.displayName) },
                        selected = selectedFilter == filter,
                        leadingIcon = if (filter == TVShowFilter.FAVORITES) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SeriesBlue.copy(alpha = 0.2f),
                            selectedLabelColor = SeriesBlue,
                            selectedLeadingIconColor = SeriesBlue
                        )
                    )
                }
            }
            
            // Content
            when {
                appState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SeriesBlue)
                    }
                }
                
                appState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = appState.errorMessage ?: "Unknown error",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                filteredAndSortedTVShows.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                modifier = Modifier.padding(32.dp),
                                tint = SeriesBlue.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No TV shows found",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Try adjusting your filters or refresh the library",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                else -> {
                    TVShowsContent(
                        tvShows = filteredAndSortedTVShows,
                        viewMode = viewMode,
                        getImageUrl = { item -> viewModel.getImageUrl(item) },
                        onTVShowClick = onTVShowClick,
                        isLoadingMore = appState.isLoadingMore,
                        hasMoreItems = appState.hasMoreItems,
                        onLoadMore = { viewModel.loadMoreItems() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TVShowsContent(
    tvShows: List<BaseItemDto>,
    viewMode: TVShowViewMode,
    getImageUrl: (BaseItemDto) -> String?,
    onTVShowClick: ((String) -> Unit)?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        TVShowViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(tvShows) { tvShow ->
                    MediaCard(
                        item = tvShow,
                        getImageUrl = getImageUrl,
                        modifier = Modifier.clickable {
                            onTVShowClick?.invoke(tvShow.id.toString())
                        }
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TVShowsPaginationFooter(
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
        
        TVShowViewMode.LIST -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(tvShows) { tvShow ->
                    MediaCard(
                        item = tvShow,
                        getImageUrl = getImageUrl,
                        modifier = Modifier.clickable {
                            onTVShowClick?.invoke(tvShow.id.toString())
                        }
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item {
                        TVShowsPaginationFooter(
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TVShowsPaginationFooter(
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        if (hasMoreItems && !isLoadingMore) {
            onLoadMore()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoadingMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = SeriesBlue,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = "Loading more TV shows...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!hasMoreItems) {
            Text(
                text = "No more TV shows to load",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}