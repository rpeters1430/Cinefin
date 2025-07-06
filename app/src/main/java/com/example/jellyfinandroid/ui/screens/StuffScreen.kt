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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Widgets
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
import com.example.jellyfinandroid.ui.theme.BookPurple
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

enum class StuffFilter(val displayName: String) {
    ALL("All Stuff"),
    BOOKS("Books"),
    AUDIOBOOKS("Audiobooks"),
    VIDEOS("Videos"),
    PHOTOS("Photos"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    UNVIEWED("Unviewed");
    
    companion object {
        fun getAllFilters() = entries
    }
}

enum class StuffSortOrder(val displayName: String) {
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    TYPE_ASC("Type A-Z"),
    TYPE_DESC("Type Z-A"),
    DATE_ADDED_DESC("Recently Added"),
    DATE_ADDED_ASC("Oldest Added"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    YEAR_DESC("Newest First"),
    YEAR_ASC("Oldest First");
    
    companion object {
        fun getDefault() = TITLE_ASC
        fun getAllSortOrders() = entries
    }
}

enum class StuffViewMode {
    GRID,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StuffScreen(
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsState()
    var selectedFilter by remember { mutableStateOf(StuffFilter.ALL) }
    var sortOrder by remember { mutableStateOf(StuffSortOrder.getDefault()) }
    var viewMode by remember { mutableStateOf(StuffViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Filter stuff items from all items (everything that's not movies, TV shows, or music)
    val stuffItems = remember(appState.allItems) {
        appState.allItems.filter { 
            it.type == BaseItemKind.BOOK || 
            it.type == BaseItemKind.AUDIO_BOOK || 
            it.type == BaseItemKind.VIDEO ||
            it.type == BaseItemKind.PHOTO ||
            (it.type != BaseItemKind.MOVIE && 
             it.type != BaseItemKind.SERIES && 
             it.type != BaseItemKind.EPISODE &&
             it.type != BaseItemKind.AUDIO && 
             it.type != BaseItemKind.MUSIC_ALBUM && 
             it.type != BaseItemKind.MUSIC_ARTIST)
        }
    }
    
    // Apply filtering and sorting
    val filteredAndSortedStuff = remember(stuffItems, selectedFilter, sortOrder) {
        val filtered = when (selectedFilter) {
            StuffFilter.ALL -> stuffItems
            StuffFilter.BOOKS -> stuffItems.filter { it.type == BaseItemKind.BOOK }
            StuffFilter.AUDIOBOOKS -> stuffItems.filter { it.type == BaseItemKind.AUDIO_BOOK }
            StuffFilter.VIDEOS -> stuffItems.filter { it.type == BaseItemKind.VIDEO }
            StuffFilter.PHOTOS -> stuffItems.filter { it.type == BaseItemKind.PHOTO }
            StuffFilter.FAVORITES -> stuffItems.filter { it.userData?.isFavorite == true }
            StuffFilter.RECENT -> stuffItems.filter { 
                ((it.productionYear as? Number)?.toInt() ?: 0) >= 2020 
            }
            StuffFilter.UNVIEWED -> stuffItems.filter { 
                it.userData?.played != true 
            }
        }
        
        when (sortOrder) {
            StuffSortOrder.TITLE_ASC -> filtered.sortedBy { it.sortName ?: it.name }
            StuffSortOrder.TITLE_DESC -> filtered.sortedByDescending { it.sortName ?: it.name }
            StuffSortOrder.TYPE_ASC -> filtered.sortedBy { it.type?.toString() ?: "" }
            StuffSortOrder.TYPE_DESC -> filtered.sortedByDescending { it.type?.toString() ?: "" }
            StuffSortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateCreated }
            StuffSortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.dateCreated }
            StuffSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.runTimeTicks ?: 0L }
            StuffSortOrder.SIZE_ASC -> filtered.sortedBy { it.runTimeTicks ?: 0L }
            StuffSortOrder.YEAR_DESC -> filtered.sortedByDescending { 
                (it.productionYear as? Number)?.toInt() ?: 0 
            }
            StuffSortOrder.YEAR_ASC -> filtered.sortedBy { 
                (it.productionYear as? Number)?.toInt() ?: 0 
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
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = BookPurple
                        )
                        Text(
                            text = "Stuff",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (filteredAndSortedStuff.isNotEmpty()) {
                            Text(
                                text = "(${filteredAndSortedStuff.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // View mode toggle
                    SingleChoiceSegmentedButtonRow {
                        StuffViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = StuffViewMode.entries.size
                                ),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = BookPurple.copy(alpha = 0.2f),
                                    activeContentColor = BookPurple
                                )
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        StuffViewMode.GRID -> Icons.Default.GridView
                                        StuffViewMode.LIST -> Icons.Default.ViewList
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
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort"
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            StuffSortOrder.getAllSortOrders().forEach { order ->
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
                items(StuffFilter.getAllFilters()) { filter ->
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.displayName) },
                        selected = selectedFilter == filter,
                        leadingIcon = when (filter) {
                            StuffFilter.FAVORITES -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            StuffFilter.BOOKS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Book,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            StuffFilter.AUDIOBOOKS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.AudioFile,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            StuffFilter.VIDEOS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.VideoFile,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            StuffFilter.PHOTOS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp)
                                    )
                                }
                            }
                            else -> null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BookPurple.copy(alpha = 0.2f),
                            selectedLabelColor = BookPurple,
                            selectedLeadingIconColor = BookPurple
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
                        CircularProgressIndicator(color = BookPurple)
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
                
                filteredAndSortedStuff.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                modifier = Modifier.padding(32.dp),
                                tint = BookPurple.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No items found",
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
                    StuffContent(
                        stuffItems = filteredAndSortedStuff,
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
private fun StuffContent(
    stuffItems: List<BaseItemDto>,
    viewMode: StuffViewMode,
    getImageUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        StuffViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(stuffItems) { stuffItem ->
                    MediaCard(
                        item = stuffItem,
                        getImageUrl = getImageUrl
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StuffPaginationFooter(
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
        
        StuffViewMode.LIST -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(stuffItems) { stuffItem ->
                    MediaCard(
                        item = stuffItem,
                        getImageUrl = getImageUrl
                    )
                }
                
                if (hasMoreItems || isLoadingMore) {
                    item {
                        StuffPaginationFooter(
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
private fun StuffPaginationFooter(
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
                    color = BookPurple,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    text = "Loading more items...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!hasMoreItems) {
            Text(
                text = "No more items to load",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}