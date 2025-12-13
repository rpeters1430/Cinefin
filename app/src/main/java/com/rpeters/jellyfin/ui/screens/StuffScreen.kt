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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingToolbar
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ToolbarAction
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale
import java.util.UUID

@Composable
fun StuffScreen(
    libraryId: String,
    collectionType: String?,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    onItemClick: (String) -> Unit = {},
) {
    if (BuildConfig.DEBUG) {
        android.util.Log.d("StuffScreen", "StuffScreen started: libraryId=$libraryId, collectionType=$collectionType")
    }
    val appState by viewModel.appState.collectAsState()

    if (BuildConfig.DEBUG) {
        android.util.Log.d("StuffScreen", "App state libraries count: ${appState.libraries.size}")
        android.util.Log.d("StuffScreen", "App state itemsByLibrary size: ${appState.itemsByLibrary.size}")
        appState.itemsByLibrary.forEach { (id, items) ->
            android.util.Log.d("StuffScreen", "itemsByLibrary[$id]: ${items.size} items")
        }
    }

    // Ensure libraries are available if navigating directly here
    LaunchedEffect(Unit) {
        if (appState.libraries.isEmpty()) {
            viewModel.loadInitialData()
        }
    }

    LaunchedEffect(libraryId) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("StuffScreen", "LaunchedEffect triggered for libraryId=$libraryId")
        }
        viewModel.loadHomeVideos(libraryId)
    }

    val librariesById = remember(appState.libraries) {
        appState.libraries.associateBy { it.id }
    }
    val type = remember(collectionType, librariesById, libraryId) {
        collectionType ?: librariesById[UUID.fromString(libraryId)]
            ?.collectionType?.toString()?.lowercase(Locale.getDefault()) ?: "mixed"
    }

    // Filter stuff items from the library-specific home videos
    val stuffItems = remember(appState.itemsByLibrary, libraryId, type) {
        val items = appState.itemsByLibrary[libraryId] ?: emptyList()
        if (BuildConfig.DEBUG) {
            android.util.Log.d("StuffScreen", "libraryId=$libraryId, type=$type, items.size=${items.size}")
            if (items.isNotEmpty()) {
                val typeBreakdown = items.groupBy { it.type?.name }.mapValues { it.value.size }
                android.util.Log.d("StuffScreen", "Item types: $typeBreakdown")
            }
        }

        // For "stuff" library, we want to show all items except movies, TV shows, music, etc.
        // We'll be more permissive with the filtering to ensure items are displayed
        val filtered = when (type) {
            "books" -> items.filter {
                it.type == BaseItemKind.BOOK || it.type == BaseItemKind.AUDIO_BOOK
            }
            "homevideos" -> items.filter { it.type == BaseItemKind.VIDEO }
            else -> {
                // For "stuff" or "mixed" libraries, show all items
                // This is more permissive than the previous filtering
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("StuffScreen", "Using permissive filter for type=$type")
                }
                items
            }
        }

        val sorted = filtered.sortedBy { it.sortName ?: it.name }
        if (BuildConfig.DEBUG) {
            android.util.Log.d("StuffScreen", "Filtered items count: ${filtered.size}, Sorted items count: ${sorted.size}")
        }
        sorted
    }

    val loadingMessage = when (type) {
        "books" -> "Loading books..."
        "homevideos" -> "Loading videos..."
        else -> "Loading items..."
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            appState.isLoading -> {
                ExpressiveFullScreenLoading(
                    message = loadingMessage,
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

            stuffItems.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val emptyMessage = when (type) {
                        "books" -> "No books found"
                        "homevideos" -> "No videos found"
                        else -> "No items found"
                    }
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                StuffGrid(
                    stuffItems = stuffItems,
                    getImageUrl = { item -> viewModel.getImageUrl(item) },
                    onItemClick = onItemClick,
                    isLoadingMore = appState.isLoadingMore,
                    hasMoreItems = appState.hasMoreItems,
                    onLoadMore = { viewModel.loadMoreItems() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        ExpressiveFloatingToolbar(
            isVisible = stuffItems.isNotEmpty(),
            onPlayClick = {},
            onQueueClick = {},
            onDownloadClick = {}, // No-op or implement download functionality if available
            onCastClick = {},
            onFavoriteClick = {},
            onShareClick = {},
            onMoreClick = { viewModel.loadInitialData() },
            primaryAction = ToolbarAction.PLAY,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun StuffGrid(
    stuffItems: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (String) -> Unit,
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
        items(stuffItems) { stuffItem ->
            ExpressiveMediaCard(
                title = stuffItem.name ?: "",
                subtitle = stuffItem.type?.toString() ?: "",
                imageUrl = getImageUrl(stuffItem) ?: "",
                rating = (stuffItem.communityRating as? Double)?.toFloat(),
                isFavorite = stuffItem.userData?.isFavorite == true,
                onCardClick = { onItemClick(stuffItem.id?.toString() ?: "") },
                onPlayClick = {
                    // For home videos, trigger playback
                    when (stuffItem.type) {
                        BaseItemKind.VIDEO -> {
                            stuffItem.id?.toString()?.let(onItemClick)
                        }
                        else -> {
                            stuffItem.id?.toString()?.let(onItemClick)
                        }
                    }
                },
                onFavoriteClick = {
                    // TODO: Implement favorite toggle
                },
                onMoreClick = {
                    // TODO: Show context menu with download, share options
                },
            )
        }

        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ExpressiveCircularLoading()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StuffGridPreview() {
    StuffGrid(
        stuffItems = emptyList(),
        getImageUrl = { null },
        onItemClick = {},
        isLoadingMore = false,
        hasMoreItems = false,
        onLoadMore = {},
    )
}
