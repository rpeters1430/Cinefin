package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.immersive.ParallaxHeroSection
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.ShareUtils
import com.rpeters.jellyfin.ui.viewmodel.AlbumDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.getFormattedDuration
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Immersive album detail screen with Netflix/Disney+ inspired design.
 * Features:
 * - Full-bleed parallax album artwork backdrop (480dp height)
 * - Album title and artist overlaid on gradient
 * - Large "Play Album" button
 * - Track list with track numbers, duration, and play buttons
 * - Action buttons (Favorite, Share, Download)
 * - Floating back button
 * - Material 3 animations
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveAlbumDetailScreen(
    albumId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    mainViewModel: MainAppViewModel = hiltViewModel(),
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isFavorite by remember { mutableStateOf(false) }

    // Track scroll state for parallax effect
    val listState = rememberLazyListState()
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset / ImmersiveDimens.HeroHeightPhone.value
            } else {
                1f
            }
        }
    }

    val handlePlay: (BaseItemDto) -> Unit = { track ->
        val streamUrl = mainViewModel.getStreamUrl(track)
        if (streamUrl != null) {
            MediaPlayerUtils.playMedia(context, streamUrl, track)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Unable to start playback")
            }
        }
    }

    val handlePlayAlbum: () -> Unit = {
        state.tracks.firstOrNull()?.let { firstTrack ->
            handlePlay(firstTrack)
        }
    }

    LaunchedEffect(albumId) {
        viewModel.load(albumId)
    }

    LaunchedEffect(state.album) {
        isFavorite = state.album?.userData?.isFavorite == true
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { viewModel.load(albumId) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Parallax Hero Section with Album Artwork
                item(key = "hero", contentType = "hero") {
                    state.album?.let { album ->
                        ParallaxHeroSection(
                            imageUrl = mainViewModel.getImageUrl(album),
                            scrollOffset = scrollOffset,
                            height = ImmersiveDimens.HeroHeightPhone,
                            parallaxFactor = 0.5f,
                            contentScale = ContentScale.Crop,
                        ) {
                            // Album title and artist overlaid on gradient at bottom
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                                    .padding(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = album.name ?: "Album",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Text(
                                    text = album.albumArtist ?: album.artists?.firstOrNull() ?: "",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // Album metadata row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    album.productionYear?.let { year ->
                                        Text(
                                            text = year.toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.8f),
                                        )
                                    }

                                    Text(
                                        text = "${state.tracks.size} tracks",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.8f),
                                    )

                                    album.runTimeTicks?.let { ticks ->
                                        val duration = album.getFormattedDuration()
                                        duration?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White.copy(alpha = 0.8f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Large Play Album Button
                item(key = "play_button", contentType = "action") {
                    Button(
                        onClick = handlePlayAlbum,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                            .padding(top = 24.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MusicGreen,
                        ),
                        enabled = state.tracks.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Play Album",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Action Buttons Row
                item(key = "actions", contentType = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ImmersiveAlbumActionButton(
                            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = if (isFavorite) "Favorited" else "Favorite",
                            onClick = {
                                state.album?.let { album ->
                                    isFavorite = !isFavorite
                                    mainViewModel.toggleFavorite(album)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ImmersiveAlbumActionButton(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = {
                                state.album?.let { album ->
                                    ShareUtils.shareMedia(context, album)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ImmersiveAlbumActionButton(
                            icon = Icons.Default.Download,
                            label = "Download",
                            onClick = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Download not yet implemented")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Track List Header
                item(key = "track_list_header", contentType = "header") {
                    Text(
                        text = "Tracks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = ImmersiveDimens.SpacingContentPadding)
                            .padding(top = 32.dp, bottom = 16.dp),
                    )
                }

                // Track List
                items(
                    items = state.tracks,
                    key = { track -> track.getItemKey().ifEmpty { track.name ?: track.toString() } },
                ) { track ->
                    ImmersiveTrackItem(
                        track = track,
                        trackNumber = track.indexNumber ?: 0,
                        onPlayClick = { handlePlay(track) },
                        onFavoriteClick = { mainViewModel.toggleFavorite(track) },
                        modifier = Modifier.padding(horizontal = ImmersiveDimens.SpacingContentPadding),
                    )
                }
            }
        }

        // Floating Back Button
        FloatingActionButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun ImmersiveTrackItem(
    track: BaseItemDto,
    trackNumber: Int,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFavorite = track.userData?.isFavorite == true
    val duration = track.getFormattedDuration()

    ElevatedCard(
        onClick = onPlayClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCard),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 4.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Track Number
            Text(
                text = trackNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )

            // Track Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = track.name ?: "Unknown Track",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artists?.firstOrNull() ?: track.albumArtist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Duration
            duration?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Favorite Button
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MusicGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImmersiveAlbumActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
