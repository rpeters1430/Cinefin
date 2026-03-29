package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo
import com.rpeters.jellyfin.ui.components.MediaInfoIcons
import com.rpeters.jellyfin.ui.components.tv.TvContentCarousel
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvErrorBanner
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.requestInitialFocus
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import com.rpeters.jellyfin.ui.viewmodel.UserPreferencesViewModel
import com.rpeters.jellyfin.utils.normalizeOfficialRating
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import java.util.Locale
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvItemDetailScreen(
    itemId: String?,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    seasonViewModel: TVSeasonViewModel = hiltViewModel(),
    onItemSelect: (String) -> Unit,
    onPlay: (itemId: String, itemName: String, startPositionMs: Long) -> Unit,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    val appState by viewModel.appState.collectAsState()
    val seasonState by seasonViewModel.state.collectAsState()
    val userPrefs: UserPreferencesViewModel = hiltViewModel()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val tvFocusManager = rememberTvFocusManager()

    val windowSizeClass = calculateWindowSizeClass(context as androidx.activity.ComponentActivity)
    val windowLayoutInfo = rememberWindowLayoutInfo()
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)

    val item: BaseItemDto? = itemId?.let { id ->
        appState.allMovies.firstOrNull { it.id.toString() == id }
            ?: appState.allTVShows.firstOrNull { it.id.toString() == id }
            ?: appState.recentlyAdded.firstOrNull { it.id.toString() == id }
            ?: appState.itemsByLibrary.values.asSequence().flatten().firstOrNull { it.id.toString() == id }
            ?: seasonState.seriesDetails?.takeIf { it.id.toString() == id }
            ?: appState.selectedItem?.takeIf { it.id.toString() == id }
    }

    // AI Summary state
    val aiSummary = appState.viewingMood // Reusing viewingMood for now, or could be a specific field

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
    val playButtonFocusRequester = remember { FocusRequester() }
    val startOverButtonFocusRequester = remember { FocusRequester() }
    val nextEpisodeButtonFocusRequester = remember { FocusRequester() }
    val favoriteButtonFocusRequester = remember { FocusRequester() }
    val watchedButtonFocusRequester = remember { FocusRequester() }
    val moreButtonFocusRequester = remember { FocusRequester() }
    val nextContentFocusRequester = remember { FocusRequester() }
    val resumeMs = item?.userData?.playbackPositionTicks?.div(10_000) ?: 0L
    val isResuming = resumeMs > 0L
    val nextEpisode = seasonState.nextEpisode
    val defaultActionFocusRequester = when {
        isResuming -> playButtonFocusRequester
        item?.type == BaseItemKind.SERIES && nextEpisode != null -> nextEpisodeButtonFocusRequester
        else -> playButtonFocusRequester
    }
    var lastFocusedActionRequester by remember(item?.id, isResuming, nextEpisode?.id) {
        mutableStateOf(defaultActionFocusRequester)
    }
    var showMoreDetails by remember(item?.id) { mutableStateOf(false) }
    defaultActionFocusRequester.requestInitialFocus(condition = item != null)

    // Backdrop animation
    var isReady by remember { mutableStateOf(false) }
    LaunchedEffect(focusedBackdrop) {
        if (focusedBackdrop != null) {
            isReady = true
        }
    }
    val backdropScale by animateFloatAsState(
        targetValue = if (isReady) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 10000),
        label = "backdropZoom",
    )

    TvScreenFocusScope(
        screenKey = "tv_item_${itemId ?: "unknown"}",
        focusManager = tvFocusManager,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .tvKeyboardHandler(
                    focusManager = focusManager,
                    onBack = onBack,
                    onSearch = onSearch,
                    onMore = {
                        showMoreDetails = !showMoreDetails
                    },
                ),
        ) {
            if (item == null) {
                TvImmersiveBackground(backdropUrl = null)

                when {
                    itemId.isNullOrBlank() -> {
                        TvEmptyState(
                            title = "Nothing To Show",
                            message = "This detail route did not receive a valid item.",
                            onAction = onBack,
                            actionText = "Back",
                        )
                    }
                    !appState.errorMessage.isNullOrBlank() -> {
                        TvErrorBanner(
                            title = "Unable To Load Details",
                            message = appState.errorMessage ?: "Unknown error",
                            onRetry = { viewModel.loadItemById(itemId) },
                            onDismiss = onBack,
                        )
                    }
                    else -> {
                        TvFullScreenLoading(message = "Loading details...")
                    }
                }
                return@Box
            }

            // Full-screen Immersive Backdrop with zoom (centered scale to avoid edge bleed)
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = backdropScale
                    scaleY = backdropScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                },
            ) {
                TvImmersiveBackground(
                    backdropUrl = focusedBackdrop,
                    dimAmount = 0.6f,
                    blurRadius = 10,
                )
            }

            // Enhanced Cinematic Gradients
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.0f),
                                Color.Black.copy(alpha = 0.9f),
                            ),
                            startY = 0f,
                            endY = 1000f,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.0f),
                            ),
                            startX = 0f,
                            endX = 1200f,
                        ),
                    ),
            )

            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 56.dp)
                    .padding(top = 80.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                // Main Info Section
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val isVideo = item.type == BaseItemKind.VIDEO

                    // Poster / Backdrop Card
                    val cardWidth = if (isVideo) 400.dp else 280.dp
                    val cardHeight = if (isVideo) 225.dp else 420.dp
                    val cardImageUrl = if (isVideo) {
                        item.let { viewModel.getBackdropUrl(it) ?: viewModel.getImageUrl(it) }
                    } else {
                        item.let { viewModel.getImageUrl(it) }
                    }

                    TvCard(
                        onClick = {},
                        modifier = Modifier.size(cardWidth, cardHeight),
                        scale = TvCardDefaults.scale(focusedScale = 1.05f),
                        colors = TvCardDefaults.colors(containerColor = Color.DarkGray),
                        border = TvCardDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                            ),
                        ),
                    ) {
                        JellyfinAsyncImage(
                            model = cardImageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            requestSize = rememberCoilSize(cardWidth, cardHeight),
                        )
                    }

                    // Metadata Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item.tvDetailEyebrow()?.let { eyebrow ->
                            TvText(
                                text = eyebrow,
                                style = TvMaterialTheme.typography.labelLarge,
                                color = TvMaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        TvText(
                            text = item.name ?: "Unknown Title",
                            style = TvMaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Metadata Badges Row
                        val durationMs = ((item.runTimeTicks ?: 0L) / 10_000L)
                        val durationText = if (durationMs > 0) formatDuration(durationMs) else null
                        val community = item.communityRating?.let { String.format(Locale.ROOT, "%.1f★", it) }
                        val official = item.officialRating?.let { normalizeOfficialRating(it) }
                        val year = item.productionYear?.toString()

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOfNotNull(year, official, durationText, community).forEach { text ->
                                TvText(
                                    text = text,
                                    style = TvMaterialTheme.typography.titleMedium,
                                    color = TvMaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            // Technical Media Info (4K, HDR, etc)
                            MediaInfoIcons(item = item, iconSize = 20.dp)
                        }

                        item.tvDetailContextLine()?.let { contextLine ->
                            TvText(
                                text = contextLine,
                                style = TvMaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.82f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Genres
                        item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                            TvText(
                                text = genres.joinToString(" • "),
                                style = TvMaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }

                        // AI Summary Card (Cinefin AI)
                        if (!aiSummary.isNullOrBlank()) {
                            TvCard(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = TvCardDefaults.colors(
                                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    contentColor = Color.White,
                                ),
                                shape = TvCardDefaults.shape(shape = TvMaterialTheme.shapes.medium),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TvIcon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = TvMaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Column {
                                        TvText(
                                            text = "CINEFIN AI SUMMARY",
                                            style = TvMaterialTheme.typography.labelMedium,
                                            color = TvMaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                        TvText(
                                            text = aiSummary,
                                            style = TvMaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        // Overview
                        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                            TvText(
                                text = overview,
                                style = TvMaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = TvMaterialTheme.typography.bodyLarge.lineHeight * 1.3f,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons
                        val playItem = if (item.type == BaseItemKind.SERIES) seasonState.nextEpisode ?: item else item
                        val playbackProgress = item.userData?.playedPercentage?.takeIf { it > 0.0 }

                        playbackProgress?.let { progress ->
                            TvText(
                                text = if (isResuming) "${progress.toInt()}% watched" else "${progress.toInt()}% complete",
                                style = TvMaterialTheme.typography.bodyLarge,
                                color = TvMaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvButton(
                                onClick = {
                                    val id = playItem.id.toString()
                                    val title = playItem.name ?: ""
                                    if (id.isNotBlank()) {
                                        onPlay(id, title, resumeMs)
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(playButtonFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            lastFocusedActionRequester = playButtonFocusRequester
                                        }
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                            nextContentFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            ) {
                                TvIcon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                TvText(if (isResuming) "Resume" else "Play")
                            }

                            if (isResuming) {
                                TvButton(
                                    onClick = {
                                        val id = playItem.id.toString()
                                        val title = playItem.name ?: ""
                                        if (id.isNotBlank()) {
                                            onPlay(id, title, 0L)
                                        }
                                    },
                                    modifier = Modifier
                                        .focusRequester(startOverButtonFocusRequester)
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                lastFocusedActionRequester = startOverButtonFocusRequester
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                                nextContentFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    colors = TvButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    TvText("Play From Start")
                                }
                            }

                            if (item.type == BaseItemKind.SERIES && nextEpisode != null) {
                                TvButton(
                                    onClick = {
                                        val nextId = nextEpisode.id.toString()
                                        if (!nextId.isNullOrBlank()) {
                                            onPlay(nextId, nextEpisode.name ?: "", 0L)
                                        }
                                    },
                                    modifier = Modifier
                                        .focusRequester(nextEpisodeButtonFocusRequester)
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                lastFocusedActionRequester = nextEpisodeButtonFocusRequester
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                                nextContentFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    colors = TvButtonDefaults.colors(
                                        containerColor = TvMaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    TvText("Next Episode")
                                }
                            }

                            TvButton(
                                onClick = {
                                    item.let {
                                        userPrefs.toggleFavorite(it) { _, _ -> }
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(favoriteButtonFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            lastFocusedActionRequester = favoriteButtonFocusRequester
                                        }
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                            nextContentFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                colors = TvButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White,
                                ),
                            ) {
                                val isFav = item.userData?.isFavorite == true
                                TvIcon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                            }

                            TvButton(
                                onClick = {
                                    item.let {
                                        if (it.userData?.played == true) {
                                            userPrefs.markAsUnwatched(it) { _, _ -> }
                                        } else {
                                            userPrefs.markAsWatched(it) { _, _ -> }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(watchedButtonFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            lastFocusedActionRequester = watchedButtonFocusRequester
                                        }
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                            nextContentFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                colors = TvButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White,
                                ),
                            ) {
                                val watched = item.userData?.played == true
                                TvIcon(Icons.Default.Check, null, tint = if (watched) TvMaterialTheme.colorScheme.primary else Color.White)
                            }

                            TvButton(
                                onClick = { showMoreDetails = !showMoreDetails },
                                modifier = Modifier
                                    .focusRequester(moreButtonFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            lastFocusedActionRequester = moreButtonFocusRequester
                                        }
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                            nextContentFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                colors = TvButtonDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White,
                                ),
                            ) {
                                TvIcon(Icons.Default.Info, null)
                                Spacer(Modifier.width(8.dp))
                                TvText(if (showMoreDetails) "Hide Details" else "More")
                            }
                        }

                        if (showMoreDetails) {
                            TvSurface(
                                tonalElevation = 8.dp,
                                shape = TvMaterialTheme.shapes.large,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.34f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    TvText(
                                        text = "More Details",
                                        style = TvMaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )

                                    DetailInfoRow("Type", item.tvDetailEyebrow() ?: "Unknown")
                                    item.tvDetailContextLine()?.let { DetailInfoRow("Context", it) }
                                    item.taglines?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { DetailInfoRow("Tagline", it) }
                                    item.studios?.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { DetailInfoRow("Studio", it) }
                                    item.genres?.takeIf { it.isNotEmpty() }?.let { DetailInfoRow("Genres", it.joinToString(" • ")) }
                                    item.communityRating?.let { DetailInfoRow("Community Rating", String.format(Locale.ROOT, "%.1f / 10", it)) }
                                    item.officialRating?.takeIf { it.isNotBlank() }?.let {
                                        DetailInfoRow("Official Rating", normalizeOfficialRating(it) ?: it)
                                    }
                                    item.runTimeTicks?.takeIf { it > 0L }?.let { DetailInfoRow("Runtime", formatDuration(it / 10_000L)) }
                                    item.userData?.lastPlayedDate?.toString()?.takeIf { it.isNotBlank() }?.let { DetailInfoRow("Last Played", it) }
                                    DetailInfoRow("Track Controls", "Audio and subtitles are available after playback starts.")
                                }
                            }
                        }
                    }
                }

                // Seasons Row for TV Shows
                if (item.type == BaseItemKind.SERIES && seasonState.seasons.isNotEmpty()) {
                    var selectedSeasonIndex by remember { mutableStateOf(0) }
                    val selectedSeason = seasonState.seasons.getOrNull(selectedSeasonIndex)

                    LaunchedEffect(selectedSeason?.id) {
                        selectedSeason?.id?.toString()?.let { seasonViewModel.loadSeasonEpisodes(it) }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvText(
                            text = "Seasons",
                            style = TvMaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )

                        TabRow(
                            selectedTabIndex = selectedSeasonIndex,
                            containerColor = Color.Transparent,
                            indicator = { _, _ -> /* Standard indicator or custom */ },
                        ) {
                            seasonState.seasons.forEachIndexed { index, season ->
                                Tab(
                                    selected = selectedSeasonIndex == index,
                                    onFocus = { selectedSeasonIndex = index },
                                    onClick = { selectedSeasonIndex = index },
                                ) {
                                    TvText(
                                        text = season.name ?: "Season ${season.indexNumber}",
                                        style = TvMaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = if (selectedSeasonIndex == index) Color.White else Color.White.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }

                        val episodes = selectedSeason?.id?.toString()?.let { seasonState.episodesBySeasonId[it] } ?: emptyList()
                        if (episodes.isNotEmpty()) {
                            TvContentCarousel(
                                items = episodes,
                                title = selectedSeason?.name ?: "Episodes",
                                layoutConfig = layoutConfig,
                                focusManager = tvFocusManager,
                                focusRequester = nextContentFocusRequester,
                                onExitUp = {
                                    lastFocusedActionRequester.requestFocus()
                                    true
                                },
                                onItemFocus = { episode ->
                                    // Optional: Update backdrop to episode backdrop
                                    val epBackdrop = viewModel.getBackdropUrl(episode)
                                    if (epBackdrop != null) focusedBackdrop = epBackdrop
                                },
                                onItemSelect = { episode ->
                                    onPlay(episode.id.toString(), episode.name ?: "", 0L)
                                },
                            )
                        }
                    }
                }

                val castPeople = item.people
                    ?.filter { person -> person.type.toString().lowercase() in setOf("actor", "gueststar") }
                    ?.take(12)
                    .orEmpty()
                if (castPeople.isNotEmpty()) {
                    TvPersonRail(
                        title = "Cast",
                        people = castPeople,
                        getPersonImageUrl = viewModel::getPersonImageUrl,
                    )
                }

                val relatedRail = remember(
                    item.id,
                    item.type,
                    seasonState.similarSeries,
                    appState.recentlyAddedByTypes,
                ) {
                    buildDetailRelatedRail(
                        item = item,
                        similarSeries = seasonState.similarSeries,
                        recentlyAddedByTypes = appState.recentlyAddedByTypes,
                    )
                }
                if (relatedRail.items.isNotEmpty()) {
                    TvContentCarousel(
                        items = relatedRail.items,
                        title = relatedRail.title,
                        layoutConfig = layoutConfig,
                        focusManager = tvFocusManager,
                        focusRequester = nextContentFocusRequester,
                        onExitUp = {
                            lastFocusedActionRequester.requestFocus()
                            true
                        },
                        onItemSelect = { related ->
                            onItemSelect(related.id.toString())
                        },
                    )
                }
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

private fun BaseItemDto.tvDetailEyebrow(): String? {
    return when (type) {
        BaseItemKind.MOVIE -> "MOVIE"
        BaseItemKind.SERIES -> "SERIES"
        BaseItemKind.SEASON -> "SEASON"
        BaseItemKind.EPISODE -> "EPISODE"
        BaseItemKind.VIDEO -> "HOME VIDEO"
        BaseItemKind.AUDIO -> "ALBUM"
        else -> type.name.replace('_', ' ')
    }
}

private fun BaseItemDto.tvDetailContextLine(): String? {
    return when (type) {
        BaseItemKind.EPISODE -> {
            val episodeCode = buildString {
                parentIndexNumber?.let { append("S$it") }
                indexNumber?.let {
                    if (isNotEmpty()) append(" ")
                    append("E$it")
                }
            }.ifBlank { null }
            listOfNotNull(seriesName, episodeCode).joinToString(" • ").ifBlank { null }
        }
        BaseItemKind.SEASON -> {
            listOfNotNull(seriesName, name).distinct().joinToString(" • ").ifBlank { null }
        }
        BaseItemKind.SERIES -> {
            childCount?.takeIf { it > 0 }?.let { "$it episodes available" }
        }
        BaseItemKind.VIDEO -> {
            listOfNotNull(productionYear?.toString(), officialRating).joinToString(" • ").ifBlank { null }
        }
        else -> null
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TvText(
            text = label.uppercase(Locale.ROOT),
            style = TvMaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.62f),
            fontWeight = FontWeight.SemiBold,
        )
        TvText(
            text = value,
            style = TvMaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun TvPersonRail(
    title: String,
    people: List<BaseItemPerson>,
    getPersonImageUrl: (BaseItemPerson) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvText(
            text = title,
            style = TvMaterialTheme.typography.headlineSmall,
            color = Color.White,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(end = 24.dp),
        ) {
            items(people, key = { person -> person.id.toString() }) { person ->
                TvCard(
                    onClick = {},
                    scale = TvCardDefaults.scale(focusedScale = 1.04f),
                    colors = TvCardDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Column(
                        modifier = Modifier.width(132.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .aspectRatio(1f)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val imageUrl = getPersonImageUrl(person)
                            if (imageUrl != null) {
                                JellyfinAsyncImage(
                                    model = imageUrl,
                                    contentDescription = person.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(132.dp)
                                        .background(Color.White.copy(alpha = 0.06f), CircleShape),
                                    requestSize = rememberCoilSize(132.dp, 132.dp),
                                )
                            } else {
                                TvIcon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(56.dp),
                                )
                            }
                        }

                        TvText(
                            text = person.name ?: "Unknown",
                            style = TvMaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        person.role?.takeIf { it.isNotBlank() }?.let { role ->
                            TvText(
                                text = role,
                                style = TvMaterialTheme.typography.bodySmall,
                                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class DetailRelatedRail(
    val title: String,
    val items: List<BaseItemDto>,
)

private fun buildDetailRelatedRail(
    item: BaseItemDto?,
    similarSeries: List<BaseItemDto>,
    recentlyAddedByTypes: Map<String, List<BaseItemDto>>,
): DetailRelatedRail {
    val currentId = item?.id?.toString()
    val recentMovies = recentlyAddedByTypes[BaseItemKind.MOVIE.name].orEmpty()
    val recentSeries = recentlyAddedByTypes[BaseItemKind.SERIES.name].orEmpty()
    val recentEpisodes = recentlyAddedByTypes[BaseItemKind.EPISODE.name].orEmpty()
    val recentVideos = recentlyAddedByTypes[BaseItemKind.VIDEO.name].orEmpty()

    fun filtered(items: List<BaseItemDto>): List<BaseItemDto> =
        items.filterNot { it.id.toString() == currentId }.take(10)

    return when (item?.type) {
        BaseItemKind.SERIES -> {
            val seriesLike = if (similarSeries.isNotEmpty()) similarSeries else recentSeries
            DetailRelatedRail("More Series Like This", filtered(seriesLike))
        }
        BaseItemKind.MOVIE -> DetailRelatedRail("More Movies", filtered(recentMovies))
        BaseItemKind.EPISODE -> DetailRelatedRail("More Episodes", filtered(recentEpisodes))
        BaseItemKind.SEASON -> DetailRelatedRail("More Episodes", filtered(recentEpisodes))
        BaseItemKind.VIDEO -> DetailRelatedRail("More Stuff", filtered(recentVideos))
        else -> DetailRelatedRail("Recently Added", filtered(recentMovies + recentSeries + recentEpisodes + recentVideos))
    }
}
