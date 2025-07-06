package com.example.jellyfinandroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.example.jellyfinandroid.ui.ShimmerBox
import com.example.jellyfinandroid.ui.theme.AudioBookOrange
import com.example.jellyfinandroid.ui.theme.BookPurple
import com.example.jellyfinandroid.ui.theme.MovieRed
import com.example.jellyfinandroid.ui.theme.MusicGreen
import com.example.jellyfinandroid.ui.theme.SeriesBlue
import com.example.jellyfinandroid.ui.viewmodel.LibraryTypeViewModel
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

enum class LibraryType(
    val displayName: String,
    val icon: ImageVector,
    val color: Color,
    val itemKinds: List<BaseItemKind>
) {
    MOVIES(
        displayName = "Movies",
        icon = Icons.Default.Movie,
        color = MovieRed,
        itemKinds = listOf(BaseItemKind.MOVIE)
    ),
    TV_SHOWS(
        displayName = "TV Shows",
        icon = Icons.Default.Tv,
        color = SeriesBlue,
        itemKinds = listOf(BaseItemKind.SERIES, BaseItemKind.EPISODE)
    ),
    MUSIC(
        displayName = "Music",
        icon = Icons.Default.MusicNote,
        color = MusicGreen,
        itemKinds = listOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST)
    ),
    STUFF(
        displayName = "Stuff",
        icon = Icons.Default.Widgets,
        color = BookPurple,
        itemKinds = listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK, BaseItemKind.VIDEO, BaseItemKind.PHOTO)
    )
}

enum class ViewMode {
    GRID,
    LIST,
    CAROUSEL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTypeScreen(
    libraryType: LibraryType,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel()
) {
    val appState by viewModel.appState.collectAsState()
    var viewMode by remember { mutableStateOf(ViewMode.GRID) }
    var selectedFilter by remember { mutableStateOf("All") }
    
    // Filter items based on library type
    val filteredItems = remember(appState.allItems, libraryType) {
        appState.allItems.filter { item ->
            libraryType.itemKinds.contains(item.type)
        }
    }
    
    // Further filter based on selected filter
    val displayItems = remember(filteredItems, selectedFilter) {
        when (selectedFilter) {
            "Recent" -> filteredItems.sortedByDescending { it.dateCreated }
            "Favorites" -> filteredItems.filter { it.userData?.isFavorite == true }
            "A-Z" -> filteredItems.sortedBy { it.sortName ?: it.name }
            else -> filteredItems
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
                            imageVector = libraryType.icon,
                            contentDescription = null,
                            tint = libraryType.color
                        )
                        Text(
                            text = libraryType.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // View mode selector
                    SingleChoiceSegmentedButtonRow {
                        ViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ViewMode.entries.size
                                ),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = libraryType.color.copy(alpha = 0.2f),
                                    activeContentColor = libraryType.color
                                )
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ViewMode.GRID -> Icons.Default.GridView
                                        ViewMode.LIST -> Icons.Default.ViewList
                                        ViewMode.CAROUSEL -> Icons.Default.ViewCarousel
                                    },
                                    contentDescription = mode.name,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    IconButton(onClick = { viewModel.loadInitialData() }) {
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
                items(listOf("All", "Recent", "Favorites", "A-Z")) { filter ->
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        selected = selectedFilter == filter,
                        leadingIcon = if (filter == "Favorites") {
                            {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = libraryType.color.copy(alpha = 0.2f),
                            selectedLabelColor = libraryType.color,
                            selectedLeadingIconColor = libraryType.color
                        )
                    )
                }
            }
            
            // Content based on view mode
            when {
                appState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = libraryType.color
                        )
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
                                text = appState.errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                displayItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = libraryType.icon,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = libraryType.color.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "No ${libraryType.displayName.lowercase()} found",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Try refreshing or check your library settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                else -> {
                    LibraryContent(
                        items = displayItems,
                        viewMode = viewMode,
                        libraryType = libraryType,
                        getImageUrl = { item -> viewModel.getImageUrl(item) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryContent(
    items: List<BaseItemDto>,
    viewMode: ViewMode,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        when (viewMode) {
            ViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items) { item ->
                        LibraryItemCard(
                            item = item,
                            libraryType = libraryType,
                            getImageUrl = getImageUrl,
                            isCompact = true
                        )
                    }
                }
            }
            
            ViewMode.LIST -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items) { item ->
                        LibraryItemCard(
                            item = item,
                            libraryType = libraryType,
                            getImageUrl = getImageUrl,
                            isCompact = false
                        )
                    }
                }
            }
            
            ViewMode.CAROUSEL -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Group items by some criteria for carousel sections
                    val groupedItems = items.chunked(10)
                    
                    items(groupedItems.size) { index ->
                        Column {
                            Text(
                                text = when (index) {
                                    0 -> "Featured ${libraryType.displayName}"
                                    1 -> "Recently Added"
                                    else -> "More ${libraryType.displayName}"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            
                            HorizontalMultiBrowseCarousel(
                                state = rememberCarouselState { groupedItems[index].size },
                                modifier = Modifier.height(280.dp),
                                preferredItemWidth = 200.dp,
                                itemSpacing = 8.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) { itemIndex ->
                                LibraryItemCard(
                                    item = groupedItems[index][itemIndex],
                                    libraryType = libraryType,
                                    getImageUrl = getImageUrl,
                                    isCompact = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryItemCard(
    item: BaseItemDto,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isCompact) Modifier.width(180.dp) else Modifier),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        if (isCompact) {
            // Compact card for grid/carousel
            Column {
                Box {
                    SubcomposeAsyncImage(
                        model = getImageUrl(item),
                        contentDescription = item.name,
                        loading = {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            )
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = libraryType.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = libraryType.color.copy(alpha = 0.6f)
                                )
                            }
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )
                    
                    // Favorite indicator
                    if (item.userData?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        )
                    }
                }
                
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = item.name ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Full-width card for list view
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    SubcomposeAsyncImage(
                        model = getImageUrl(item),
                        contentDescription = item.name,
                        loading = {
                            ShimmerBox(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(140.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = libraryType.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = libraryType.color.copy(alpha = 0.6f)
                                )
                            }
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(100.dp)
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    
                    if (item.userData?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        )
                    }
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    item.overview?.let { overview ->
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Additional info based on library type
                    when (libraryType) {
                        LibraryType.MOVIES -> {
                            item.runTimeTicks?.let { runtime ->
                                val minutes = (runtime / 600000000).toInt()
                                Text(
                                    text = "${minutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = libraryType.color
                                )
                            }
                        }
                        LibraryType.TV_SHOWS -> {
                            if (item.type == BaseItemKind.SERIES) {
                                item.childCount?.let { count ->
                                    Text(
                                        text = "$count episodes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = libraryType.color
                                    )
                                }
                            }
                        }
                        LibraryType.MUSIC -> {
                            item.artists?.firstOrNull()?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = libraryType.color
                                )
                            }
                        }
                        LibraryType.STUFF -> {
                            item.type?.let { type ->
                                Text(
                                    text = type.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = libraryType.color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}