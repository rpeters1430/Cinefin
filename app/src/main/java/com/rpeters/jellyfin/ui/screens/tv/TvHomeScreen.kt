package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.*
import com.rpeters.jellyfin.ui.components.tv.*
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    onItemSelect: (String) -> Unit,
    onLibrarySelect: (String) -> Unit,
    onSearch: () -> Unit = {},
    onPlay: (itemId: String, itemName: String, startMs: Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    screenKey: String = "tv_home",
) {
    val appState by viewModel.appState.collectAsState()
    var focusedBackdrop by remember { mutableStateOf<String?>(null) }
    val tvLayout = CinefinTvTheme.layout

    LaunchedEffect(Unit) {
        viewModel.loadInitialData(forceRefresh = false)
        viewModel.loadFavorites()
    }

    Box(modifier = modifier.fillMaxSize()) {
        TvImmersiveBackground(backdropUrl = focusedBackdrop)

        if (appState.isLoading && appState.libraries.isEmpty()) {
            TvFullScreenLoading(message = "Syncing your library...")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = tvLayout.screenTopPadding,
                    bottom = tvLayout.contentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
            ) {
                item {
                    TvHomeHeader(
                        modifier = Modifier.padding(
                            start = tvLayout.screenHorizontalPadding,
                            bottom = 8.dp,
                        ),
                    )
                }

                if (appState.libraries.isNotEmpty()) {
                    item {
                        TvSectionRow(
                            title = "Your Libraries",
                            sectionPadding = tvLayout.screenHorizontalPadding,
                            items = appState.libraries,
                            onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                            onItemClick = { onLibrarySelect(it.id.toString()) },
                            content = { library, isFocused ->
                                LibraryCard(library, isFocused)
                            },
                        )
                    }
                }

                if (appState.continueWatching.isNotEmpty()) {
                    item {
                        TvSectionRow(
                            title = "Continue Watching",
                            sectionPadding = tvLayout.screenHorizontalPadding,
                            items = appState.continueWatching.take(10),
                            onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                            onItemClick = { onItemSelect(it.id.toString()) },
                            content = { item, isFocused ->
                                MediaCard(
                                    item = item,
                                    getImageUrl = { viewModel.getSeriesImageUrl(it) ?: viewModel.getImageUrl(it) },
                                    isFocused = isFocused,
                                    aspectRatio = 16f / 9f,
                                    width = 320.dp,
                                )
                            },
                        )
                    }
                }

                val recentMovies = appState.recentlyAddedByTypes[BaseItemKind.MOVIE.name].orEmpty()
                if (recentMovies.isNotEmpty()) {
                    item {
                        TvSectionRow(
                            title = "Recently Added Movies",
                            sectionPadding = tvLayout.screenHorizontalPadding,
                            items = recentMovies.take(15),
                            onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                            onItemClick = { onItemSelect(it.id.toString()) },
                            content = { item, isFocused ->
                                MediaCard(
                                    item = item,
                                    getImageUrl = viewModel::getImageUrl,
                                    isFocused = isFocused,
                                    aspectRatio = 2f / 3f,
                                    width = 180.dp,
                                )
                            },
                        )
                    }
                }

                val recentShows = appState.recentlyAddedByTypes[BaseItemKind.SERIES.name].orEmpty()
                if (recentShows.isNotEmpty()) {
                    item {
                        TvSectionRow(
                            title = "Latest TV Shows",
                            sectionPadding = tvLayout.screenHorizontalPadding,
                            items = recentShows.take(15),
                            onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                            onItemClick = { onItemSelect(it.id.toString()) },
                            content = { item, isFocused ->
                                MediaCard(
                                    item = item,
                                    getImageUrl = viewModel::getImageUrl,
                                    isFocused = isFocused,
                                    aspectRatio = 2f / 3f,
                                    width = 180.dp,
                                )
                            },
                        )
                    }
                }

                val recentStuff = appState.recentlyAddedByTypes[BaseItemKind.VIDEO.name].orEmpty()
                if (recentStuff.isNotEmpty()) {
                    item {
                        TvSectionRow(
                            title = "Recent Stuff",
                            sectionPadding = tvLayout.screenHorizontalPadding,
                            items = recentStuff.take(15),
                            onItemFocus = { focusedBackdrop = viewModel.getBackdropUrl(it) },
                            onItemClick = { onItemSelect(it.id.toString()) },
                            content = { item, isFocused ->
                                MediaCard(
                                    item = item,
                                    getImageUrl = viewModel::getImageUrl,
                                    isFocused = isFocused,
                                    aspectRatio = 16f / 9f,
                                    width = 280.dp,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSectionRow(
    title: String,
    sectionPadding: Dp,
    items: List<BaseItemDto>,
    onItemFocus: (BaseItemDto) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    content: @Composable (BaseItemDto, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = sectionPadding),
            color = Color.White.copy(alpha = 0.9f),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(items, key = { it.id.toString() }) { item ->
                var isFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier
                        .wrapContentSize()
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) onItemFocus(item)
                        },
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                    ),
                ) {
                    content(item, isFocused)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryCard(library: BaseItemDto, isFocused: Boolean) {
    Card(
        onClick = { /* Handled by parent surface */ },
        modifier = Modifier.size(200.dp, 100.dp),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = library.name ?: "Library",
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    isFocused: Boolean,
    aspectRatio: Float,
    width: Dp,
) {
    val height = width / aspectRatio

    StandardCardContainer(
        imageCard = {
            Card(
                onClick = { /* Handled by parent surface */ },
                modifier = Modifier.size(width, height),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    JellyfinAsyncImage(
                        model = getImageUrl(item),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        requestSize = rememberCoilSize(width, height),
                    )

                    val isPlayed = item.userData?.played == true
                    val playbackTicks = item.userData?.playbackPositionTicks ?: 0L
                    val runtimeTicks = item.runTimeTicks ?: 0L
                    val progress = if (runtimeTicks > 0) playbackTicks.toFloat() / runtimeTicks else 0f

                    if (progress > 0 && !isPlayed) {
                        TvPlaybackProgressBar(
                            progressRatio = progress,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                    }
                }
            }
        },
        title = {
            Text(
                text = item.name ?: "Unknown",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            )
        },
        modifier = Modifier.width(width),
    )
}

@Composable
fun TvHomeHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Welcome to Cinefin",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Browse your media collection",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
