package com.example.jellyfinandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import com.example.jellyfinandroid.utils.getRatingAsDouble
import com.example.jellyfinandroid.utils.hasHighRating
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.stringResource
import com.example.jellyfinandroid.R
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jellyfinandroid.ui.components.MediaCard
import com.example.jellyfinandroid.ui.theme.MovieRed
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

enum class MovieFilter(val displayNameResId: Int) {
    ALL(R.string.filter_all_movies),
    RECENT(R.string.filter_recent),
    FAVORITES(R.string.filter_favorites),
    DECADE_2020S(R.string.filter_2020s),
    DECADE_2010S(R.string.filter_2010s),
    DECADE_2000S(R.string.filter_2000s),
    DECADE_1990S(R.string.filter_1990s),
    HIGH_RATED(R.string.filter_high_rated),
    UNWATCHED(R.string.filter_unwatched);
    
    companion object {
        fun getAllFilters() = entries
    }
}

enum class MovieSortOrder(val displayNameResId: Int) {
    TITLE_ASC(R.string.sort_title_asc),
    TITLE_DESC(R.string.sort_title_desc),
    YEAR_DESC(R.string.sort_year_desc),
    YEAR_ASC(R.string.sort_year_asc),
    RATING_DESC(R.string.sort_rating_desc),
    RATING_ASC(R.string.sort_rating_asc),
    RUNTIME_DESC(R.string.sort_runtime_desc),
    RUNTIME_ASC(R.string.sort_runtime_asc),
    DATE_ADDED_DESC(R.string.sort_date_added_desc),
    DATE_ADDED_ASC(R.string.sort_date_added_asc);
    
    companion object {
        fun getDefault() = TITLE_ASC
        fun getAllSortOrders() = entries
    }
}

enum class MovieViewMode {
    GRID,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    onBackClick: () -> Unit = {},
    onMovieClick: (BaseItemDto) -> Unit = {},
    viewModel: MainAppViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val appState by viewModel.appState.collectAsState()
    var selectedFilter by remember { mutableStateOf(MovieFilter.ALL) }
    var sortOrder by remember { mutableStateOf(MovieSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(MovieViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Filter movies from all items
    val movieItems = remember(appState.allItems) {
        appState.allItems.filter { it.type == BaseItemKind.MOVIE }
    }
    
    // Apply filtering and sorting
    val filteredAndSortedMovies = remember(movieItems, selectedFilter, sortOrder) {
        val filtered = when (selectedFilter) {
            MovieFilter.ALL -> movieItems
            MovieFilter.RECENT -> movieItems.filter { 
                ((it.productionYear as? Number)?.toInt() ?: 0) >= 2020 
            }
            MovieFilter.FAVORITES -> movieItems.filter { it.userData?.isFavorite == true }
            MovieFilter.DECADE_2020S -> movieItems.filter { 
                val year = (it.productionYear as? Number)?.toInt() ?: 0
                year >= 2020 && year <= 2029
            }
            MovieFilter.DECADE_2010S -> movieItems.filter { 
                val year = (it.productionYear as? Number)?.toInt() ?: 0
                year >= 2010 && year <= 2019
            }
            MovieFilter.DECADE_2000S -> movieItems.filter { 
                val year = (it.productionYear as? Number)?.toInt() ?: 0
                year >= 2000 && year <= 2009
            }
            MovieFilter.DECADE_1990S -> movieItems.filter { 
                val year = (it.productionYear as? Number)?.toInt() ?: 0
                year >= 1990 && year <= 1999
            }
            MovieFilter.HIGH_RATED -> movieItems.filter { it.hasHighRating() }
            MovieFilter.UNWATCHED -> movieItems.filter { 
                it.userData?.played != true 
            }
        }
        
        when (sortOrder) {
            MovieSortOrder.TITLE_ASC -> filtered.sortedBy { it.sortName ?: it.name }
            MovieSortOrder.TITLE_DESC -> filtered.sortedByDescending { it.sortName ?: it.name }
            MovieSortOrder.YEAR_DESC -> filtered.sortedByDescending { 
                (it.productionYear as? Number)?.toInt() ?: 0 
            }
            MovieSortOrder.YEAR_ASC -> filtered.sortedBy { 
                (it.productionYear as? Number)?.toInt() ?: 0 
            }
            MovieSortOrder.RATING_DESC -> filtered.sortedByDescending { it.getRatingAsDouble() }
            MovieSortOrder.RATING_ASC -> filtered.sortedBy { it.getRatingAsDouble() }
            MovieSortOrder.RUNTIME_DESC -> filtered.sortedByDescending { 
                it.runTimeTicks ?: 0L 
            }
            MovieSortOrder.RUNTIME_ASC -> filtered.sortedBy { 
                it.runTimeTicks ?: 0L 
            }
            MovieSortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateCreated }
            MovieSortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.dateCreated }
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
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    // View mode toggle
                    SingleChoiceSegmentedButtonRow {
                        MovieViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = MovieViewMode.entries.size
                                ),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MovieRed.copy(alpha = 0.2f),
                                    activeContentColor = MovieRed
                                )
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        MovieViewMode.GRID -> Icons.Default.GridView
                                        MovieViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
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
                                contentDescription = stringResource(id = R.string.sort)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            MovieSortOrder.getAllSortOrders().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = order.displayNameResId)) },
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
                            contentDescription = stringResource(id = R.string.refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
                items(MovieFilter.getAllFilters()) { filter ->
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(stringResource(id = filter.displayNameResId)) },
                        selected = selectedFilter == filter,
                        leadingIcon = if (filter == MovieFilter.FAVORITES) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MovieRed.copy(alpha = 0.2f),
                            selectedLabelColor = MovieRed,
                            selectedLeadingIconColor = MovieRed
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
                        CircularProgressIndicator(color = MovieRed)
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
                
                filteredAndSortedMovies.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                modifier = Modifier.padding(32.dp),
                                tint = MovieRed.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(id = R.string.no_movies_found),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(id = R.string.adjust_filters_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                else -> {
                    MoviesContent(
                        movies = filteredAndSortedMovies,
                        viewMode = viewMode,
                        getImageUrl = { item -> viewModel.getImageUrl(item) },
                        onMovieClick = onMovieClick,
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
private fun MoviesContent(
    movies: List<BaseItemDto>,
    viewMode: MovieViewMode,
    getImageUrl: (BaseItemDto) -> String?,
    onMovieClick: (BaseItemDto) -> Unit,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        MovieViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(movies) { movie ->
                    MediaCard(
                        item = movie,
                        getImageUrl = getImageUrl,
                        onClick = onMovieClick
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MoviesPaginationFooter(
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
        
        MovieViewMode.LIST -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(movies) { movie ->
                    MediaCard(
                        item = movie,
                        getImageUrl = getImageUrl,
                        onClick = onMovieClick
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item {
                        MoviesPaginationFooter(
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
private fun MoviesPaginationFooter(
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
                    color = MovieRed,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = stringResource(id = R.string.loading_more_movies),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!hasMoreItems) {
            Text(
                text = stringResource(id = R.string.no_more_movies),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}