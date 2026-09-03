package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.immersive.ParallaxHeroSection
import com.rpeters.jellyfin.ui.components.immersive.normalizedParallaxScrollOffset
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.LibraryPlaylistsAccent
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.ShareUtils
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.PlaylistDetailViewModel
import com.rpeters.jellyfin.utils.getFormattedDuration
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale
import kotlin.random.Random

private val YouTubeRed = Color(0xFFFF0000)

/**
 * Immersive playlist detail screen designed for Jellyfin playlists, including
 * YouTube playlists synced via YouTarr or other tools.
 */
@OptInAppExperimentalApis
@Composable
fun ImmersivePlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onVideoDetailClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    mainViewModel: MainAppViewModel = hiltViewModel(),
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playlistEmptyMessage = stringResource(R.string.playlist_empty)
    val downloadingPlaylistItemsMessage = stringResource(R.string.downloading_playlist_items)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    val listState = rememberLazyListState()
    val heroHeightPx = with(LocalDensity.current) { ImmersiveDimens.HeroHeightPhone.toPx() }
    val scrollOffset by remember(listState, heroHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                normalizedParallaxScrollOffset(
                    scrollOffsetPx = listState.firstVisibleItemScrollOffset,
                    heroHeightPx = heroHeightPx,
                )
            } else {
                1f
            }
        }
    }

    LaunchedEffect(playlistId) {
        viewModel.load(playlistId)
    }

    val handlePlayItem: (BaseItemDto) -> Unit = { item ->
        val streamUrl = mainViewModel.getStreamUrl(item)
        if (streamUrl != null) {
            MediaPlayerUtils.playMedia(
                context = context,
                streamUrl = streamUrl,
                item = item,
                playlistId = playlistId,
            )
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Unable to start playback")
            }
        }
    }

    val handlePlayAll: () -> Unit = {
        state.items.firstOrNull()?.let { firstItem ->
            handlePlayItem(firstItem)
        }
    }

    val handleShuffle: () -> Unit = {
        if (state.items.isNotEmpty()) {
            val randomIndex = Random.nextInt(state.items.size)
            handlePlayItem(state.items[randomIndex])
        }
    }

    val handleDownloadAll: () -> Unit = {
        coroutineScope.launch {
            if (state.items.isEmpty()) {
                snackbarHostState.showSnackbar(playlistEmptyMessage)
            } else {
                snackbarHostState.showSnackbar(downloadingPlaylistItemsMessage)
                state.items.forEach { videoItem ->
                    downloadsViewModel.startDownload(videoItem)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.load(playlistId) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.playlist == null && state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ExpressiveCircularLoading()
                }
            } else if (state.playlist == null && state.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = state.errorMessage ?: stringResource(R.string.unknown_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(onClick = { viewModel.load(playlistId) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    // Parallax Hero Section with Playlist Backdrop
                    item(key = "hero", contentType = "hero") {
                        state.playlist?.let { playlist ->
                            ParallaxHeroSection(
                                imageUrl = mainViewModel.getBackdropUrl(playlist)
                                    ?: mainViewModel.getImageUrl(playlist),
                                scrollOffset = scrollOffset,
                                height = ImmersiveDimens.HeroHeightPhone,
                                parallaxFactor = 0.5f,
                                contentScale = ContentScale.Crop,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                        .padding(bottom = 32.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    // Badge (YouTube Playlist or General Playlist)
                                    Surface(
                                        color = if (state.isYouTubePlaylist) YouTubeRed else LibraryPlaylistsAccent,
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (state.isYouTubePlaylist) Icons.Default.PlayCircleFilled else Icons.AutoMirrored.Filled.PlaylistPlay,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = if (state.isYouTubePlaylist) stringResource(R.string.youtube_playlist) else stringResource(R.string.playlist),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }

                                    // Title
                                    Text(
                                        text = playlist.name ?: stringResource(R.string.playlist),
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    // Channel / Uploader / Studio
                                    val creatorName = playlist.studios?.firstOrNull()?.name
                                        ?: playlist.artists?.firstOrNull()
                                        ?: playlist.albumArtist
                                    if (!creatorName.isNullOrBlank()) {
                                        Text(
                                            text = creatorName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White.copy(alpha = 0.9f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    // Metadata row (item count, cumulative duration)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val itemCount = state.items.size.takeIf { it > 0 } ?: (playlist.childCount ?: 0)
                                        Text(
                                            text = stringResource(R.string.videos_count, itemCount),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.8f),
                                        )

                                        val duration = playlist.getFormattedDuration()
                                        if (!duration.isNullOrBlank()) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White.copy(alpha = 0.6f),
                                            )
                                            Text(
                                                text = duration,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White.copy(alpha = 0.8f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Play All & Shuffle Buttons
                    item(key = "play_buttons", contentType = "play_buttons") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                .padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = handlePlayAll,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isYouTubePlaylist) YouTubeRed else MaterialTheme.colorScheme.primary,
                                ),
                                enabled = state.items.isNotEmpty(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.play_all),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            FilledTonalButton(
                                onClick = handleShuffle,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
                                enabled = state.items.isNotEmpty(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.shuffle_playlist),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // Action buttons (Favorite, Share, Download All)
                    item(key = "actions", contentType = "actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                .padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PlaylistActionButton(
                                icon = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                label = stringResource(R.string.favorites),
                                isSelected = state.isFavorite,
                                onClick = { viewModel.toggleFavorite() },
                                modifier = Modifier.weight(1f),
                            )

                            PlaylistActionButton(
                                icon = Icons.Default.Share,
                                label = stringResource(R.string.share),
                                onClick = {
                                    state.playlist?.let {
                                        ShareUtils.shareMedia(context, it)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )

                            PlaylistActionButton(
                                icon = Icons.Default.Download,
                                label = stringResource(R.string.download),
                                onClick = handleDownloadAll,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Overview (if available)
                    state.playlist?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        item(key = "overview", contentType = "overview") {
                            var isExpanded by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                    .padding(top = 16.dp)
                                    .clickable { isExpanded = !isExpanded },
                            ) {
                                Text(
                                    text = overview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Section Divider / Header
                    item(key = "header_videos", contentType = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                .padding(top = 24.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.videos_count, state.items.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Empty Items State
                    if (state.items.isEmpty()) {
                        item(key = "empty_items", contentType = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_playlist_items),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        // Video Items List
                        itemsIndexed(
                            items = state.items,
                            key = { index, item -> item.getItemKey() + "_$index" },
                            contentType = { _, _ -> "playlist_item" },
                        ) { index, item ->
                            PlaylistItemRow(
                                index = index + 1,
                                item = item,
                                getImageUrl = { mainViewModel.getImageUrl(it) },
                                onClick = { handlePlayItem(item) },
                                onDetailClick = {
                                    onVideoDetailClick?.invoke(item.id.toString())
                                },
                                onToggleWatched = {
                                    mainViewModel.toggleWatchedStatus(item)
                                },
                                onDownload = {
                                    downloadsViewModel.startDownload(item)
                                },
                                onShare = {
                                    ShareUtils.shareMedia(context, item)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Floating Back Button
        FloatingActionButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(48.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.navigate_up),
                modifier = Modifier.size(24.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun PlaylistActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
        colors = if (isSelected) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistItemRow(
    index: Int,
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
    onToggleWatched: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isPlayed = item.userData?.played == true
    val playbackPosition = item.userData?.playbackPositionTicks ?: 0L
    val totalDurationTicks = item.runTimeTicks ?: 0L
    val progressFraction = if (totalDurationTicks > 0L && playbackPosition > 0L) {
        (playbackPosition.toFloat() / totalDurationTicks.toFloat()).coerceIn(0f, 1f)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ImmersiveDimens.SpacingContentPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Index number
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.width(24.dp),
            )

            // Video Thumbnail
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(getImageUrl(item))
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Duration badge
                val formattedDuration = item.getFormattedDuration()
                if (!formattedDuration.isNullOrBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    ) {
                        Text(
                            text = formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                // Watched indicator overlay
                if (isPlayed) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }

                // Progress Bar for partially watched video
                if (progressFraction != null && !isPlayed) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                    )
                }
            }

            // Video Details (Title & Creator/Studio/Date)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val uploaderOrStudio = item.studios?.firstOrNull()?.name
                    ?: item.artists?.firstOrNull()
                    ?: item.albumArtist
                if (!uploaderOrStudio.isNullOrBlank()) {
                    Text(
                        text = uploaderOrStudio,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // More actions menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.play_now)) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPlayed) "Mark as Unwatched" else "Mark as Watched") },
                        leadingIcon = {
                            Icon(
                                if (isPlayed) Icons.Default.VisibilityOff else Icons.Default.CheckCircle,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleWatched()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download)) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDownload()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDetailClick()
                        },
                    )
                }
            }
        }
    }
}
