package com.example.jellyfinandroid.ui.screens

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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import com.example.jellyfinandroid.ui.theme.MusicGreen
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

enum class MusicFilter(val displayName: String) {
    ALL("All Music"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    SONGS("Songs"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    UNPLAYED("Unplayed");
    
    companion object {
        fun getAllFilters() = entries
    }
}

enum class MusicSortOrder(val displayName: String) {
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    ARTIST_ASC("Artist A-Z"),
    ARTIST_DESC("Artist Z-A"),
    YEAR_DESC("Newest First"),
    YEAR_ASC("Oldest First"),
    DATE_ADDED_DESC("Recently Added"),
    DATE_ADDED_ASC("Oldest Added"),
    PLAY_COUNT_DESC("Most Played"),
    PLAY_COUNT_ASC("Least Played"),
    RUNTIME_DESC("Longest First"),
    RUNTIME_ASC("Shortest First");
    
    companion object {
        fun getDefault() = TITLE_ASC
        fun getAllSortOrders() = entries
    }
}

enum class MusicViewMode {
    GRID,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsState()
    var selectedFilter by remember { mutableStateOf(MusicFilter.ALL) }
    var sortOrder by remember { mutableStateOf(MusicSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(MusicViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Filter music items from all items
    val musicItems = remember(appState.allItems) {
        appState.allItems.filter { 
            it.type == BaseItemKind.AUDIO || 
            it.type == BaseItemKind.MUSIC_ALBUM || 
            it.type == BaseItemKind.MUSIC_ARTIST 
        }
    }
    
    // Apply filtering and sorting
    val filteredAndSortedMusic = remember(musicItems, selectedFilter, sortOrder) {
        val filtered = when (selectedFilter) {
            MusicFilter.ALL -> musicItems
            MusicFilter.ALBUMS -> musicItems.filter { it.type == BaseItemKind.MUSIC_ALBUM }
            MusicFilter.ARTISTS -> musicItems.filter { it.type == BaseItemKind.MUSIC_ARTIST }
            MusicFilter.SONGS -> musicItems.filter { it.type == BaseItemKind.AUDIO }
            MusicFilter.FAVORITES -> musicItems.filter { it.userData?.isFavorite == true }
            MusicFilter.RECENT -> musicItems.filter { 
                ((it.productionYear as? Number)?.toInt() ?: 0) >= 2020 
            }
            MusicFilter.UNPLAYED -> musicItems.filter { 
                it.userData?.played != true 
            }
        }
        
        when (sortOrder) {
            MusicSortOrder.TITLE_ASC -> filtered.sortedBy { it.sortName ?: it.name }
            MusicSortOrder.TITLE_DESC -> filtered.sortedByDescending { it.sortName ?: it.name }
            MusicSortOrder.ARTIST_ASC -> filtered.sortedBy { 
                it.albumArtist ?: it.artists?.firstOrNull() ?: it.name 
            }
            MusicSortOrder.ARTIST_DESC -> filtered.sortedByDescending { 
                it.albumArtist ?: it.artists?.firstOrNull() ?: it.name 
            }
            MusicSortOrder.YEAR_DESC -> filtered.sortedByDescending { 
                (it.productionYear as? Number)?.toInt() ?: 0 
            }
            MusicSortOrder.YEAR_ASC -> filtered.sortedBy { 
                (it.productionYear as? Number)?.toInt() ?: 0 
            }
            MusicSortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateCreated }
            MusicSortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.dateCreated }
            MusicSortOrder.PLAY_COUNT_DESC -> filtered.sortedByDescending { 
                it.userData?.playCount ?: 0 
            }
            MusicSortOrder.PLAY_COUNT_ASC -> filtered.sortedBy { 
                it.userData?.playCount ?: 0 
            }
            MusicSortOrder.RUNTIME_DESC -> filtered.sortedByDescending { 
                it.runTimeTicks ?: 0L 
            }
            MusicSortOrder.RUNTIME_ASC -> filtered.sortedBy { 
                it.runTimeTicks ?: 0L 
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
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MusicGreen
                        )
                        Text(
                            text = "Music",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (filteredAndSortedMusic.isNotEmpty()) {
                            Text(
                                text = "(${filteredAndSortedMusic.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // View mode toggle
                    SingleChoiceSegmentedButtonRow {
                        MusicViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = MusicViewMode.entries.size
                                ),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MusicGreen.copy(alpha = 0.2f),
                                    activeContentColor = MusicGreen
                                )
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        MusicViewMode.GRID -> Icons.Default.GridView
                                        MusicViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
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
                            MusicSortOrder.getAllSortOrders().forEach { order ->
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
                items(MusicFilter.getAllFilters()) { filter ->
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.displayName) },
                        selected = selectedFilter == filter,
                        leadingIcon = when (filter) {
                            MusicFilter.FAVORITES -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            MusicFilter.ALBUMS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            MusicFilter.ARTISTS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            else -> null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MusicGreen.copy(alpha = 0.2f),
                            selectedLabelColor = MusicGreen,
                            selectedLeadingIconColor = MusicGreen
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
                        CircularProgressIndicator(color = MusicGreen)
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
                
                filteredAndSortedMusic.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.padding(32.dp),
                                tint = MusicGreen.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No music found",
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
                    MusicContent(
                        musicItems = filteredAndSortedMusic,
                        viewMode = viewMode,
                        getImageUrl = { item -> viewModel.getImageUrl(item) },
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
private fun MusicContent(
    musicItems: List<BaseItemDto>,
    viewMode: MusicViewMode,
    getImageUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        MusicViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(musicItems) { musicItem ->
                    MediaCard(
                        item = musicItem,
                        getImageUrl = getImageUrl
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MusicPaginationFooter(
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
        
        MusicViewMode.LIST -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(musicItems) { musicItem ->
                    MediaCard(
                        item = musicItem,
                        getImageUrl = getImageUrl
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item {
                        MusicPaginationFooter(
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
private fun MusicPaginationFooter(
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
                    color = MusicGreen,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = "Loading more music...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!hasMoreItems) {
            Text(
                text = "No more music to load",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}