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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingToolbar
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ToolbarAction
import com.rpeters.jellyfin.ui.utils.ShareUtils
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.SecureLogger
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
        SecureLogger.d("StuffScreen", "StuffScreen started: libraryId=$libraryId, collectionType=$collectionType")
    }
    val appState by viewModel.appState.collectAsState()
    val context = LocalContext.current

    if (BuildConfig.DEBUG) {
        SecureLogger.d("StuffScreen", "App state libraries count: ${appState.libraries.size}")
        SecureLogger.d("StuffScreen", "App state itemsByLibrary size: ${appState.itemsByLibrary.size}")
        appState.itemsByLibrary.forEach { (id, items) ->
            SecureLogger.d("StuffScreen", "itemsByLibrary[$id]: ${items.size} items")
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
            SecureLogger.d("StuffScreen", "LaunchedEffect triggered for libraryId=$libraryId")
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
            SecureLogger.d("StuffScreen", "libraryId=$libraryId, type=$type, items.size=${items.size}")
            if (items.isNotEmpty()) {
                val typeBreakdown = items.groupBy { it.type?.name }.mapValues { it.value.size }
                SecureLogger.d("StuffScreen", "Item types: $typeBreakdown")
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
                    SecureLogger.d("StuffScreen", "Using permissive filter for type=$type")
                }
                items
            }
        }

        val sorted = filtered.sortedBy { it.sortName ?: it.name }
        if (BuildConfig.DEBUG) {
            SecureLogger.d("StuffScreen", "Filtered items count: ${filtered.size}, Sorted items count: ${sorted.size}")
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
                            text = appState.errorMessage ?: stringResource(R.string.unknown_error),
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
                val paginationState = appState.libraryPaginationState[libraryId]
                StuffGrid(
                    stuffItems = stuffItems,
                    getImageUrl = { item -> viewModel.getImageUrl(item) },
                    itemKey = { item -> viewModel.libraryItemKey(item) },
                    onItemClick = onItemClick,
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    onShareClick = { ShareUtils.shareMedia(context, it) },
                    isLoadingMore = paginationState?.isLoadingMore ?: false,
                    hasMoreItems = paginationState?.hasMore ?: false,
                    onLoadMore = { viewModel.loadMoreLibraryItems(libraryId) },
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
    itemKey: (BaseItemDto) -> String,
    onItemClick: (String) -> Unit,
    onFavoriteClick: (BaseItemDto) -> Unit,
    onShareClick: (BaseItemDto) -> Unit,
    isLoadingMore: Boolean,
    hasMoreItems: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // Load more items when approaching the end
    LaunchedEffect(gridState, stuffItems.size, hasMoreItems, isLoadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && !isLoadingMore && hasMoreItems) {
                    val threshold = stuffItems.size - 10 // Load more when 10 items from the end
                    if (lastVisibleIndex >= threshold) {
                        onLoadMore()
                    }
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(
            items = stuffItems,
            key = itemKey,
            contentType = { "stuff_item" },
        ) { stuffItem ->
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
                    onFavoriteClick(stuffItem)
                },
                onMoreClick = {
                    onShareClick(stuffItem)
                },
            )
        }

        if (isLoadingMore || hasMoreItems) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        ExpressiveCircularLoading()
                    } else if (hasMoreItems) {
                        // Show a small indicator that more items can be loaded
                        Text(
                            text = "Scroll for more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
        itemKey = { item -> item.id?.toString() ?: "preview-${item.hashCode()}" },
        onItemClick = {},
        onFavoriteClick = {},
        onShareClick = {},
        isLoadingMore = false,
        hasMoreItems = false,
        onLoadMore = {},
    )
}
