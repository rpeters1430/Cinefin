package com.rpeters.jellyfin.ui.screens.tv

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.tv.TvContentCarousel
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.findDefaultVideoStream
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.UserPreferencesViewModel
import com.rpeters.jellyfin.utils.normalizeOfficialRating
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo

@Composable
fun TvItemDetailScreen(
    itemId: String?,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    seasonViewModel: TVSeasonViewModel = hiltViewModel(),
    onPlay: (itemId: String, itemName: String, startPositionMs: Long) -> Unit,
) {
    val appState by viewModel.appState.collectAsState()
    val seasonState by seasonViewModel.state.collectAsState()
    val userPrefs: UserPreferencesViewModel = hiltViewModel()
    val context = LocalContext.current
    val playerVm: com.rpeters.jellyfin.ui.player.VideoPlayerViewModel = hiltViewModel()
    val tvFocusManager = rememberTvFocusManager()
    
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val windowLayoutInfo = rememberWindowLayoutInfo()
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)

    val item: BaseItemDto? = itemId?.let { id ->
        appState.allMovies.firstOrNull { it.id.toString() == id }
            ?: appState.allTVShows.firstOrNull { it.id.toString() == id }
            ?: appState.recentlyAdded.firstOrNull { it.id.toString() == id }
            ?: appState.itemsByLibrary.values.asSequence().flatten().firstOrNull { it.id.toString() == id }
            ?: seasonState.seriesDetails // Fallback to details from season VM
            ?: appState.selectedItem?.takeIf { it.id.toString() == id }
    }

    // Fetch item from server when it's not already in local state
    LaunchedEffect(itemId, item) {
        if (item == null && itemId != null) {
            viewModel.loadItemById(itemId)
        }
    }

    // Load series data if the item is a series
    LaunchedEffect(itemId, item?.type) {
        if (itemId != null && item?.type == BaseItemKind.SERIES) {
            seasonViewModel.loadSeriesData(itemId)
        }
    }

    // Initialize with item's backdrop, but allow updates from focus
    var focusedBackdrop by remember(item) { 
        mutableStateOf(item?.let { viewModel.getBackdropUrl(it) }) 
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full-screen Immersive Backdrop
        TvImmersiveBackground(
            backdropUrl = focusedBackdrop,
            dimAmount = 0.7f,
            blurRadius = 15
        )

        // Gradient overlay for bottom-heavy content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 300f
                    )
                )
        )

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 56.dp)
                .padding(top = 80.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Main Info Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Poster Card
                TvCard(
                    onClick = {},
                    modifier = Modifier.size(260.dp, 390.dp),
                    scale = TvCardDefaults.scale(focusedScale = 1.05f),
                    colors = TvCardDefaults.colors(containerColor = Color.DarkGray)
                ) {
                    JellyfinAsyncImage(
                        model = item?.let { viewModel.getImageUrl(it) },
                        contentDescription = item?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        requestSize = rememberCoilSize(260.dp, 390.dp)
                    )
                }

                // Metadata Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TvText(
                        text = item?.name ?: "Unknown Title",
                        style = TvMaterialTheme.typography.displayMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Metadata Badges Row
                    val durationMs = ((item?.runTimeTicks ?: 0L) / 10_000L)
                    val durationText = if (durationMs > 0) formatDuration(durationMs) else null
                    val community = item?.communityRating?.let { String.format(Locale.ROOT, "%.1f★", it) }
                    val official = item?.officialRating?.let { normalizeOfficialRating(it) }
                    val year = item?.productionYear?.toString()
                    
                    val metaPieces = listOfNotNull(year, official, durationText, community)
                    if (metaPieces.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            metaPieces.forEach { text ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color.White.copy(alpha = 0.15f),
                                            TvMaterialTheme.shapes.small
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    TvText(
                                        text = text,
                                        style = TvMaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Genres
                    item?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                        TvText(
                            text = genres.joinToString(" • "),
                            style = TvMaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Overview
                    item?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        TvText(
                            text = overview,
                            style = TvMaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = TvMaterialTheme.typography.bodyLarge.lineHeight * 1.2f
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val resumeMs = item?.userData?.playbackPositionTicks?.div(10_000) ?: 0L
                        val isResuming = resumeMs > 0
                        
                        val playItem = if (item?.type == BaseItemKind.SERIES) seasonState.nextEpisode ?: item else item
                        
                        TvButton(
                            onClick = {
                                val id = playItem?.id?.toString()
                                val title = playItem?.name ?: ""
                                if (!id.isNullOrBlank()) {
                                    onPlay(id, title, resumeMs)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            TvIcon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            TvText(if (isResuming) "Resume" else "Play")
                        }

                        TvButton(
                            onClick = {
                                item?.let {
                                    userPrefs.toggleFavorite(it) { _, _ -> }
                                }
                            },
                            colors = TvButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            )
                        ) {
                            val isFav = item?.userData?.isFavorite == true
                            TvIcon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                        }

                        TvButton(
                            onClick = {
                                item?.let {
                                    if (it.userData?.played == true) {
                                        userPrefs.markAsUnwatched(it) { _, _ -> }
                                    } else {
                                        userPrefs.markAsWatched(it) { _, _ -> }
                                    }
                                }
                            },
                            colors = TvButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            )
                        ) {
                            val watched = item?.userData?.played == true
                            TvIcon(Icons.Default.Check, null, tint = if (watched) TvMaterialTheme.colorScheme.primary else Color.White)
                        }
                    }
                }
            }

            // TV Show Seasons & Episodes
            if (item?.type == BaseItemKind.SERIES && seasonState.seasons.isNotEmpty()) {
                var selectedSeasonIndex by remember { mutableStateOf(0) }
                val selectedSeason = seasonState.seasons.getOrNull(selectedSeasonIndex)
                
                LaunchedEffect(selectedSeason?.id) {
                    selectedSeason?.id?.toString()?.let { seasonViewModel.loadSeasonEpisodes(it) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvText(
                        text = "Seasons",
                        style = TvMaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )

                    TabRow(
                        selectedTabIndex = selectedSeasonIndex,
                        containerColor = Color.Transparent,
                        indicator = { _, _ -> /* Standard indicator or custom */ }
                    ) {
                        seasonState.seasons.forEachIndexed { index, season ->
                            Tab(
                                selected = selectedSeasonIndex == index,
                                onFocus = { selectedSeasonIndex = index },
                                onClick = { selectedSeasonIndex = index }
                            ) {
                                TvText(
                                    text = season.name ?: "Season ${season.indexNumber}",
                                    style = TvMaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = if (selectedSeasonIndex == index) Color.White else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    val episodes = selectedSeason?.id?.toString()?.let { seasonState.episodesBySeasonId[it] } ?: emptyList()
                    if (episodes.isNotEmpty()) {
                        TvContentCarousel(
                            items = episodes,
                            title = "",
                            layoutConfig = layoutConfig,
                            focusManager = tvFocusManager,
                            onItemFocus = { episode ->
                                // Optional: Update backdrop to episode backdrop
                                val epBackdrop = viewModel.getBackdropUrl(episode)
                                if (epBackdrop != null) focusedBackdrop = epBackdrop
                            },
                            onItemSelect = { episode ->
                                onPlay(episode.id.toString(), episode.name ?: "", 0L)
                            }
                        )
                    }
                }
            }

            // Similar Content (Movies or TV Shows)
            val similarItems = if (item?.type == BaseItemKind.SERIES) seasonState.similarSeries else emptyList()
            if (similarItems.isNotEmpty()) {
                TvContentCarousel(
                    items = similarItems,
                    title = "You Might Also Like",
                    layoutConfig = layoutConfig,
                    focusManager = tvFocusManager,
                    onItemSelect = { related ->
                        // In a real app, navigate to this item's detail
                    }
                )
            } else if (appState.recentlyAdded.isNotEmpty()) {
                // Fallback for movies or when similar items aren't loaded yet
                TvContentCarousel(
                    items = appState.recentlyAdded.take(10),
                    title = "Recently Added",
                    layoutConfig = layoutConfig,
                    focusManager = tvFocusManager,
                    onItemSelect = { related ->
                        // Navigate to related item
                    }
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
