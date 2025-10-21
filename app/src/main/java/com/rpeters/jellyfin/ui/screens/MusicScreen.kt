package com.rpeters.jellyfin.ui.screens

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.viewmodel.AudioPlaybackViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

enum class MusicFilter(val displayNameResId: Int) {
    ALL(R.string.filter_all_music),
    ALBUMS(R.string.filter_albums),
    ARTISTS(R.string.filter_artists),
    SONGS(R.string.filter_songs),
    FAVORITES(R.string.filter_favorites_music),
    RECENT(R.string.filter_recent_music),
    UNPLAYED(R.string.filter_unplayed_music),
    ;

    companion object {
        fun getAllFilters() = entries
    }
}

enum class MusicSortOrder(val displayNameResId: Int) {
    TITLE_ASC(R.string.sort_title_asc_music),
    TITLE_DESC(R.string.sort_title_desc_music),
    ARTIST_ASC(R.string.sort_artist_asc),
    ARTIST_DESC(R.string.sort_artist_desc),
    YEAR_DESC(R.string.sort_year_desc_music),
    YEAR_ASC(R.string.sort_year_asc_music),
    DATE_ADDED_DESC(R.string.sort_date_added_desc_music),
    DATE_ADDED_ASC(R.string.sort_date_added_asc_music),
    PLAY_COUNT_DESC(R.string.sort_play_count_desc),
    PLAY_COUNT_ASC(R.string.sort_play_count_asc),
    RUNTIME_DESC(R.string.sort_runtime_desc_music),
    RUNTIME_ASC(R.string.sort_runtime_asc_music),
    ;

    companion object {
        fun getDefault() = TITLE_ASC
        fun getAllSortOrders() = entries
    }
}

enum class MusicViewMode {
    GRID,
    LIST,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onBackClick: () -> Unit = {},
    viewModel: MainAppViewModel = hiltViewModel(),
    audioPlaybackViewModel: AudioPlaybackViewModel = hiltViewModel(),
    onItemClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val appState by viewModel.appState.collectAsState()
    val playbackState by audioPlaybackViewModel.playbackState.collectAsState()
    val playbackQueue by audioPlaybackViewModel.queue.collectAsState()
    var selectedFilter by remember { mutableStateOf(MusicFilter.ALL) }
    var sortOrder by remember { mutableStateOf(MusicSortOrder.TITLE_ASC) }
    var viewMode by remember { mutableStateOf(MusicViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Get music items via unified loader and enrich with recent audio
    val musicItems = remember(appState.itemsByLibrary, appState.recentlyAddedByTypes, appState.libraries) {
        val libraryMusic = viewModel.getLibraryTypeData(LibraryType.MUSIC)
        val recentMusic = appState.recentlyAddedByTypes[BaseItemKind.AUDIO.name] ?: emptyList()
        (libraryMusic + recentMusic).distinctBy { it.id }
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(id = R.string.music),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    // View mode toggle
                    SingleChoiceSegmentedButtonRow {
                        MusicViewMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = MusicViewMode.entries.size,
                                ),
                                onClick = { viewMode = mode },
                                selected = viewMode == mode,
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MusicGreen.copy(alpha = 0.2f),
                                    activeContentColor = MusicGreen,
                                ),
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        MusicViewMode.GRID -> Icons.Default.GridView
                                        MusicViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                    },
                                    contentDescription = mode.name,
                                    modifier = Modifier.padding(2.dp),
                                )
                            }
                        }
                    }

                    // Sort menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(id = R.string.sort),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            MusicSortOrder.getAllSortOrders().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = order.displayNameResId)) },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }

                    IconButton(onClick = { viewModel.refreshLibraryItems() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(id = R.string.refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (playbackState.isConnected && (playbackState.currentMediaItem != null || playbackQueue.isNotEmpty())) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val metadata = playbackState.currentMediaItem?.mediaMetadata
                        val artistName = metadata?.artist?.toString().orEmpty()
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.now_playing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = metadata?.title?.toString()
                                    ?: stringResource(id = R.string.music_queue_empty),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                            )
                            if (artistName.isNotBlank()) {
                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            val queueLabel = if (playbackQueue.isEmpty()) {
                                stringResource(id = R.string.music_queue_empty)
                            } else {
                                stringResource(id = R.string.music_queue_size, playbackQueue.size)
                            }
                            Text(
                                text = queueLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        IconButton(onClick = audioPlaybackViewModel::toggleShuffle) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = stringResource(id = R.string.music_toggle_shuffle),
                                tint = if (playbackState.shuffleEnabled) {
                                    MusicGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }

                        IconButton(onClick = audioPlaybackViewModel::togglePlayPause) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = stringResource(id = R.string.music_play_pause),
                            )
                        }

                        IconButton(onClick = audioPlaybackViewModel::skipToNext) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(id = R.string.music_skip_next),
                            )
                        }
                    }
                }
            }

            // Filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(MusicFilter.getAllFilters()) { filter ->
                    FilterChip(
                        onClick = { selectedFilter = filter },
                        label = { Text(stringResource(id = filter.displayNameResId)) },
                        selected = selectedFilter == filter,
                        leadingIcon = when (filter) {
                            MusicFilter.FAVORITES -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp),
                                    )
                                }
                            }
                            MusicFilter.ALBUMS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp),
                                    )
                                }
                            }
                            MusicFilter.ARTISTS -> {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.padding(2.dp),
                                    )
                                }
                            }
                            else -> null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MusicGreen.copy(alpha = 0.2f),
                            selectedLabelColor = MusicGreen,
                            selectedLeadingIconColor = MusicGreen,
                        ),
                    )
                }
            }

            // Content
            when {
                appState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpressiveCircularLoading(
                            size = 48.dp,
                            showPulse = true,
                        )
                    }
                }

                appState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = appState.errorMessage ?: "Unknown error",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                filteredAndSortedMusic.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.padding(32.dp),
                                tint = MusicGreen.copy(alpha = 0.6f),
                            )
                            Text(
                                text = stringResource(id = R.string.no_music_found),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(id = R.string.adjust_music_filters_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    MusicContent(
                        musicItems = filteredAndSortedMusic,
                        viewMode = viewMode,
                        getImageUrl = { item -> viewModel.getImageUrl(item) },
                        onItemClick = onItemClick,
                        isLoadingMore = appState.isLoadingMore,
                        hasMoreItems = appState.hasMoreItems,
                        onLoadMore = { viewModel.loadMoreItems() },
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
    onItemClick: (BaseItemDto) -> Unit,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (viewMode) {
        MusicViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize(),
            ) {
                items(musicItems) { musicItem ->
                    val coroutineScope = rememberCoroutineScope()
                    ExpressiveMusicCard(
                        item = musicItem,
                        getImageUrl = getImageUrl,
                        onClick = { onItemClick(musicItem) },
                        playbackUtils = null, // Will be enhanced in future version
                        coroutineScope = coroutineScope,
                    )
                }

                if (hasMoreItems || isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MusicPaginationFooter(
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore,
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
                modifier = modifier.fillMaxSize(),
            ) {
                items(musicItems) { musicItem ->
                    val coroutineScope = rememberCoroutineScope()
                    ExpressiveMusicCard(
                        item = musicItem,
                        getImageUrl = getImageUrl,
                        onClick = { onItemClick(musicItem) },
                        playbackUtils = null, // Will be enhanced in future version
                        coroutineScope = coroutineScope,
                    )
                }

                if (hasMoreItems || isLoadingMore) {
                    item {
                        MusicPaginationFooter(
                            isLoadingMore = isLoadingMore,
                            hasMoreItems = hasMoreItems,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveMusicCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit = {},
    playbackUtils: EnhancedPlaybackUtils? = null,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null,
    modifier: Modifier = Modifier,
) {
    val imageUrl = getImageUrl(item) ?: ""
    val title = item.name ?: "Unknown Title"

    // Determine subtitle based on item type
    val subtitle = when (item.type) {
        BaseItemKind.MUSIC_ALBUM -> {
            val artist = item.albumArtist ?: item.artists?.firstOrNull()
            artist?.let { "$it • ${item.productionYear ?: ""}" } ?: "${item.productionYear ?: ""}"
        }
        BaseItemKind.MUSIC_ARTIST -> {
            "Artist"
        }
        BaseItemKind.AUDIO -> {
            val artist = item.artists?.firstOrNull()
            artist?.let { "$it • ${item.productionYear ?: ""}" } ?: "${item.productionYear ?: ""}"
        }
        else -> {
            item.type?.toString() ?: ""
        }
    }

    // Get rating if available
    val rating = item.communityRating?.toFloat()

    // Check if favorite
    val isFavorite = item.userData?.isFavorite == true

    ExpressiveMediaCard(
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        rating = rating,
        isFavorite = isFavorite,
        onCardClick = onClick,
        onPlayClick = {
            // Use enhanced playback system if available
            if (playbackUtils != null && coroutineScope != null) {
                coroutineScope.launch {
                    playbackUtils.playMedia(
                        item = item,
                        onPlaybackStarted = { url, playbackInfo ->
                            android.util.Log.d("MusicScreen", "Playback started: ${playbackInfo.reason}")
                        },
                        onPlaybackError = { error ->
                            android.util.Log.e("MusicScreen", "Playback failed: $error")
                            // Could show user-friendly error message
                        },
                    )
                }
            } else {
                // Fallback to original behavior
                when (item.type) {
                    BaseItemKind.AUDIO -> {
                        // Play individual audio track directly
                        onClick()
                    }
                    BaseItemKind.MUSIC_ALBUM -> {
                        // Navigate to album detail for track selection
                        onClick()
                    }
                    BaseItemKind.MUSIC_ARTIST -> {
                        // Navigate to artist detail
                        onClick()
                    }
                    else -> {
                        onClick()
                    }
                }
            }
        },
        onFavoriteClick = {
            // TODO: Implement favorite toggle
            // This would typically call a ViewModel method to update favorite status
        },
        onMoreClick = {
            // TODO: Show context menu with options like:
            // - Add to queue
            // - Download
            // - Share
            // - View details
        },
        modifier = modifier,
    )
}

@Composable
private fun MusicPaginationFooter(
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
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
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpressiveCircularLoading(
                    size = 24.dp,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(id = R.string.loading_more_music),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!hasMoreItems) {
            Text(
                text = stringResource(id = R.string.no_more_music),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
