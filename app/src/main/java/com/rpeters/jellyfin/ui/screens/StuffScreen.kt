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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingToolbar
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.ToolbarAction
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun StuffScreen(
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
) {
    val appState by viewModel.appState.collectAsState()

    val stuffItems = remember(appState.allItems) {
        appState.allItems.filter {
            it.type == BaseItemKind.BOOK ||
                it.type == BaseItemKind.AUDIO_BOOK ||
                it.type == BaseItemKind.VIDEO ||
                it.type == BaseItemKind.PHOTO ||
                (
                    it.type != BaseItemKind.MOVIE &&
                        it.type != BaseItemKind.SERIES &&
                        it.type != BaseItemKind.EPISODE &&
                        it.type != BaseItemKind.AUDIO &&
                        it.type != BaseItemKind.MUSIC_ALBUM &&
                        it.type != BaseItemKind.MUSIC_ARTIST
                    )
        }.sortedBy { it.sortName ?: it.name }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            appState.isLoading -> {
                ExpressiveFullScreenLoading(
                    message = "Loading stuff...",
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
                    Text(
                        text = "No items found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                StuffGrid(
                    stuffItems = stuffItems,
                    getImageUrl = { item -> viewModel.getImageUrl(item) },
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
            onDownloadClick = { viewModel.refreshLibraryItems() },
            onCastClick = {},
            onFavoriteClick = {},
            onShareClick = {},
            onMoreClick = {},
            primaryAction = ToolbarAction.DOWNLOAD,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun StuffGrid(
    stuffItems: List<BaseItemDto>,
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
        items(stuffItems) { stuffItem ->
            ExpressiveMediaCard(
                title = stuffItem.name ?: "",
                subtitle = stuffItem.type?.toString() ?: "",
                imageUrl = getImageUrl(stuffItem) ?: "",
                rating = (stuffItem.communityRating as? Double)?.toFloat(),
                isFavorite = stuffItem.userData?.isFavorite == true,
                onCardClick = {},
            )
        }

        if (hasMoreItems || isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(Unit) {
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

@Preview(showBackground = true)
@Composable
private fun StuffGridPreview() {
    StuffGrid(
        stuffItems = emptyList(),
        getImageUrl = { null },
        isLoadingMore = false,
        hasMoreItems = false,
        onLoadMore = {},
    )
}

