package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingToolbar
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
        resolveCollectionType(collectionType, librariesById, libraryId)
    }

    // Filter stuff items from the library-specific home videos
    val stuffItems = remember(appState.itemsByLibrary, libraryId, type) {
        buildStuffItems(appState.itemsByLibrary, libraryId, type)
    }

    val loadingMessage = when (type) {
        "books" -> "Loading books..."
        "homevideos" -> "Loading videos..."
        else -> "Loading items..."
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            appState.isLoading -> {
                StuffLoadingState(
                    message = loadingMessage,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            appState.errorMessage != null -> {
                StuffErrorState(
                    message = appState.errorMessage ?: stringResource(R.string.unknown_error),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            stuffItems.isEmpty() -> {
                StuffEmptyState(
                    type = type,
                    modifier = Modifier.fillMaxSize(),
                )
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

private fun resolveCollectionType(
    collectionType: String?,
    librariesById: Map<UUID, BaseItemDto>,
    libraryId: String,
): String {
    return collectionType
        ?: librariesById[UUID.fromString(libraryId)]
            ?.collectionType?.toString()?.lowercase(Locale.getDefault()) ?: "mixed"
}

private fun buildStuffItems(
    itemsByLibrary: Map<String, List<BaseItemDto>>,
    libraryId: String,
    type: String,
): List<BaseItemDto> {
    val items = itemsByLibrary[libraryId] ?: emptyList()
    if (BuildConfig.DEBUG) {
        SecureLogger.d("StuffScreen", "libraryId=$libraryId, type=$type, items.size=${items.size}")
        if (items.isNotEmpty()) {
            val typeBreakdown = items.groupBy { it.type.name }.mapValues { it.value.size }
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
    return sorted
}
