package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingToolbar
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ToolbarAction
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeVideosScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    if (BuildConfig.DEBUG) {
        android.util.Log.d("HomeVideosScreen", "HomeVideosScreen started")
    }
    val appState by viewModel.appState.collectAsState()
    
    // Find the first Home Videos library
    val homeVideosLibrary = remember(appState.libraries) {
        appState.libraries.find { library ->
            library.collectionType == org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS
        }
    }
    
    // Load home videos when library is found
    LaunchedEffect(homeVideosLibrary) {
        homeVideosLibrary?.id?.let { libraryId ->
            if (BuildConfig.DEBUG) {
                android.util.Log.d("HomeVideosScreen", "Loading home videos for library: $libraryId")
            }
            viewModel.loadHomeVideos(libraryId.toString())
        }
    }
    
    // Get home videos items from the library
    val homeVideosItems = remember(appState.homeVideosByLibrary, homeVideosLibrary) {
        val libraryId = homeVideosLibrary?.id?.toString()
        val items = if (libraryId != null) {
            appState.homeVideosByLibrary[libraryId] ?: emptyList()
        } else {
            emptyList()
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d("HomeVideosScreen", "Found ${items.size} home video items")
            if (items.isNotEmpty()) {
                val typeBreakdown = items.groupBy { it.type?.name }.mapValues { it.value.size }
                android.util.Log.d("HomeVideosScreen", "Item types: $typeBreakdown")
            }
        }
        // Filter for videos and photos
        items.filter { 
            it.type == BaseItemKind.VIDEO || it.type == BaseItemKind.PHOTO 
        }.sortedBy { it.sortName ?: it.name }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_videos)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                appState.isLoading -> {
                    ExpressiveFullScreenLoading(
                        message = "Loading home videos...",
                        modifier = Modifier.fillMaxSize(),
                    )
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

                homeVideosItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No home videos found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    HomeVideosGrid(
                        homeVideosItems = homeVideosItems,
                        getImageUrl = { item -> viewModel.getImageUrl(item) },
                        isLoadingMore = appState.isLoadingMore,
                        hasMoreItems = appState.hasMoreItems,
                        onLoadMore = { viewModel.loadMoreItems() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    )
                }
            }

            ExpressiveFloatingToolbar(
                isVisible = homeVideosItems.isNotEmpty(),
                onPlayClick = {},
                onQueueClick = {},
                onDownloadClick = {},
                onCastClick = {},
                onFavoriteClick = {},
                onShareClick = {},
                onMoreClick = { viewModel.loadInitialData() },
                primaryAction = ToolbarAction.PLAY,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun HomeVideosGrid(
    homeVideosItems: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(homeVideosItems) { homeVideoItem ->
            ExpressiveMediaCard(
                title = homeVideoItem.name ?: "",
                subtitle = homeVideoItem.type?.toString() ?: "",
                imageUrl = getImageUrl(homeVideoItem) ?: "",
                rating = (homeVideoItem.communityRating as? Double)?.toFloat(),
                isFavorite = homeVideoItem.userData?.isFavorite == true,
                onCardClick = { /* TODO: Handle item click, e.g., navigate to details screen */ },
            )
        }

        if (hasMoreItems || isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(hasMoreItems, isLoadingMore) {
                    if (hasMoreItems && !isLoadingMore) {
                        onLoadMore()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        ExpressiveCircularLoading()
                    } else if (!hasMoreItems) {
                        Text(
                            text = "No more items to load",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
