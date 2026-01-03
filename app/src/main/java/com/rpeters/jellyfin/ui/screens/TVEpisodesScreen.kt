@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.LogCategory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.MediaItemActionsSheet
import com.rpeters.jellyfin.ui.components.WatchProgressBar
import com.rpeters.jellyfin.ui.components.WatchedIndicatorBadge
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.LibraryActionsPreferencesViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SeasonEpisodesViewModel
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun TVEpisodesScreen(
    seasonId: String,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    onEpisodeClick: (BaseItemDto) -> Unit = {},
    viewModel: SeasonEpisodesViewModel = hiltViewModel(),
    mainAppViewModel: MainAppViewModel = hiltViewModel(),
    libraryActionsPreferencesViewModel: LibraryActionsPreferencesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val libraryActionPrefs by libraryActionsPreferencesViewModel.preferences.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val managementEnabled = libraryActionPrefs.enableManagementActions
    val managementDisabledMessage = stringResource(id = R.string.library_actions_management_disabled)

    val handleItemLongPress: (BaseItemDto) -> Unit = { item ->
        if (managementEnabled) {
            selectedItem = item
            showManageSheet = true
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message = managementDisabledMessage)
            }
        }
    }

    val handlePlay: (BaseItemDto) -> Unit = { item ->
        val streamUrl = mainAppViewModel.getStreamUrl(item)
        if (streamUrl != null) {
            MediaPlayerUtils.playMedia(context, streamUrl, item)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Unable to start playback")
            }
        }
    }

    LaunchedEffect(seasonId) {
        viewModel.loadEpisodes(seasonId)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            Logger.e(LogCategory.UI, "TVEpisodesScreen", error)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.episodes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.refresh() },
                            enabled = !state.isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(id = R.string.refresh),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        AnimatedContent(
            targetState = when {
                state.isLoading && state.episodes.isEmpty() -> EpisodeScreenState.LOADING
                !state.errorMessage.isNullOrEmpty() -> EpisodeScreenState.ERROR
                state.episodes.isEmpty() -> EpisodeScreenState.EMPTY
                else -> EpisodeScreenState.CONTENT
            },
            transitionSpec = {
                fadeIn(MotionTokens.expressiveEnter) + slideInVertically { it / 4 } togetherWith
                    fadeOut(MotionTokens.expressiveExit) + slideOutVertically { -it / 4 }
            },
            label = "episode_screen_content",
        ) { screenState ->
            when (screenState) {
                EpisodeScreenState.LOADING -> {
                    ExpressiveFullScreenLoading(
                        message = "Loading Episodes...",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                EpisodeScreenState.ERROR -> {
                    ExpressiveErrorState(
                        message = state.errorMessage ?: stringResource(id = R.string.unknown_error),
                        onRetry = { viewModel.loadEpisodes(seasonId) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                EpisodeScreenState.EMPTY -> {
                    ExpressiveEmptyState(
                        icon = Icons.Default.Tv,
                        title = stringResource(id = R.string.no_episodes_found),
                        subtitle = "This season doesn't have any episodes yet.",
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                EpisodeScreenState.CONTENT -> {
                    EpisodeList(
                        episodes = state.episodes,
                        getImageUrl = getImageUrl,
                        onEpisodeClick = onEpisodeClick,
                        onEpisodeLongPress = handleItemLongPress,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    // Show media actions sheet when item is long-pressed
    selectedItem?.let { item ->
        if (showManageSheet) {
            val itemName = item.name ?: stringResource(id = R.string.unknown)
            val deleteSuccessMessage = stringResource(id = R.string.library_actions_delete_success, itemName)
            val deleteFailureTemplate = stringResource(id = R.string.library_actions_delete_failure, itemName, "%s")
            val refreshRequestedMessage = stringResource(id = R.string.library_actions_refresh_requested)
            val unknownErrorMessage = stringResource(id = R.string.unknown_error)

            MediaItemActionsSheet(
                item = item,
                sheetState = sheetState,
                onDismiss = {
                    showManageSheet = false
                    selectedItem = null
                },
                onPlay = {
                    handlePlay(item)
                },
                onDelete = { _, _ ->
                    mainAppViewModel.deleteItem(item) { success, message ->
                        coroutineScope.launch {
                            val text = if (success) {
                                deleteSuccessMessage
                            } else {
                                String.format(deleteFailureTemplate, message ?: "")
                            }
                            snackbarHostState.showSnackbar(text)
                        }
                    }
                },
                onRefreshMetadata = { _, _ ->
                    mainAppViewModel.refreshItemMetadata(item) { success, message ->
                        coroutineScope.launch {
                            val text = if (success) {
                                refreshRequestedMessage
                            } else {
                                "Failed to refresh metadata: ${message ?: unknownErrorMessage}"
                            }
                            snackbarHostState.showSnackbar(text)
                        }
                    }
                },
                onToggleWatched = {
                    mainAppViewModel.toggleWatchedStatus(item)
                },
                managementEnabled = managementEnabled,
            )
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onEpisodeClick: (BaseItemDto) -> Unit,
    onEpisodeLongPress: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = episodes,
            key = { episode -> episode.getItemKey().ifEmpty { episode.name ?: episode.toString() } },
        ) { episode ->
            ExpressiveEpisodeRow(
                episode = episode,
                getImageUrl = getImageUrl,
                onClick = onEpisodeClick,
                onLongClick = onEpisodeLongPress,
            )
        }
    }
}

@Composable
private fun ExpressiveEpisodeRow(
    episode: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit,
    onLongClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "episode_card_scale",
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                onClick = { onClick(episode) },
                onLongClick = { onLongClick(episode) },
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(80.dp),
            ) {
                SubcomposeAsyncImage(
                    model = getImageUrl(episode),
                    contentDescription = episode.name,
                    loading = {
                        ExpressiveLoadingCard(
                            modifier = Modifier
                                .width(120.dp)
                                .height(80.dp),
                            showTitle = false,
                            showSubtitle = false,
                            imageHeight = 80.dp,
                        )
                    },
                    error = {
                        Surface(
                            modifier = Modifier
                                .width(120.dp)
                                .height(80.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .width(120.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                // Enhanced watch status badge
                WatchedIndicatorBadge(
                    item = episode,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )

                // Enhanced progress bar with rounded corners
                WatchProgressBar(
                    item = episode,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Episode number and title
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    episode.indexNumber?.let { episodeNum ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "E$episodeNum",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Text(
                        text = episode.name ?: "Episode ${episode.indexNumber ?: ""}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Episode metadata
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    episode.runTimeTicks?.let { runtime ->
                        val minutes = (runtime / 600_000_000).toInt()
                        Text(
                            text = "${minutes}min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    episode.communityRating?.let { rating ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = String.format(java.util.Locale.ROOT, "%.1f", rating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                episode.overview?.let { overview ->
                    if (overview.isNotBlank()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2,
                        )
                    }
                }
            }
        }
    }
}

// Content state enum for animated transitions
enum class EpisodeScreenState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}

// Expressive Error State component
@Composable
private fun ExpressiveErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = "Error Loading Episodes",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

// Expressive Empty State component
@Composable
private fun ExpressiveEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "empty_icon_scale",
            )

            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .padding(24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    tint = iconTint,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
