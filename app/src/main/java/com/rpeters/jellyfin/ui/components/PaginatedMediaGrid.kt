package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.viewmodel.LibraryBrowserViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * A composable that displays a paginated grid of media items.
 * Uses Jetpack Compose Paging to efficiently handle large datasets.
 */
@Composable
fun PaginatedMediaGrid(
    viewModel: LibraryBrowserViewModel,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(minSize = 150.dp),
    contentPadding: PaddingValues = PaddingValues(Dimens.Spacing16),
) {
    val pagingFlow = viewModel.getLibraryItemsPagingFlow()

    if (pagingFlow == null) {
        // No library selected
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select a library to browse",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()

    PaginatedMediaGridContent(
        lazyPagingItems = lazyPagingItems,
        getImageUrl = getImageUrl,
        onItemClick = onItemClick,
        modifier = modifier,
        columns = columns,
        contentPadding = contentPadding,
    )
}

@Composable
private fun PaginatedMediaGridContent(
    lazyPagingItems: LazyPagingItems<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(minSize = 150.dp),
    contentPadding: PaddingValues = PaddingValues(Dimens.Spacing16),
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.id.toString() },
        ) { index ->
            val item = lazyPagingItems[index]
            if (item != null) {
                MediaCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Placeholder for loading items
                MediaCardPlaceholder()
            }
        }

        // Handle different load states
        when (val loadState = lazyPagingItems.loadState.append) {
            is LoadState.Loading -> {
                item {
                    LoadingIndicator()
                }
            }
            is LoadState.Error -> {
                item {
                    ErrorItem(
                        error = loadState.error,
                        onRetry = { lazyPagingItems.retry() },
                    )
                }
            }
            is LoadState.NotLoading -> {
                // No additional items to load or loading completed
            }
        }

        // Handle refresh state
        when (val refreshState = lazyPagingItems.loadState.refresh) {
            is LoadState.Loading -> {
                if (lazyPagingItems.itemCount == 0) {
                    item {
                        InitialLoadingIndicator()
                    }
                }
            }
            is LoadState.Error -> {
                if (lazyPagingItems.itemCount == 0) {
                    item {
                        InitialErrorState(
                            error = refreshState.error,
                            onRetry = { lazyPagingItems.refresh() },
                        )
                    }
                }
            }
            is LoadState.NotLoading -> {
                // Content loaded successfully
            }
        }
    }
}

@Composable
private fun MediaCardPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
    ) {
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

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveCircularLoading()
    }
}

@Composable
private fun InitialLoadingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressiveCircularLoading()
        Text(
            text = "Loading library...",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ErrorItem(
    error: Throwable,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Error loading more items",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error.message ?: stringResource(R.string.unknown_error),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun InitialErrorState(
    error: Throwable,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Failed to load library",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = error.message ?: stringResource(R.string.unknown_error),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Retry")
        }
    }
}
