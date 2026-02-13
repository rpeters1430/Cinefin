package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.HdrOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Sd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.PerformanceOptimizedLazyRow
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.components.immersive.AudioInfoCard
import com.rpeters.jellyfin.ui.components.immersive.HdrType
import com.rpeters.jellyfin.ui.components.immersive.ResolutionQuality
import com.rpeters.jellyfin.ui.components.immersive.VideoInfoCard
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.JellyfinTeal80
import com.rpeters.jellyfin.ui.theme.Quality1440
import com.rpeters.jellyfin.ui.theme.Quality4K
import com.rpeters.jellyfin.ui.theme.QualityHD
import com.rpeters.jellyfin.ui.theme.QualitySD
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.ui.utils.findDefaultVideoStream
import com.rpeters.jellyfin.utils.normalizeOfficialRating
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@OptInAppExperimentalApis
@Composable
fun ImmersiveMovieDetailScreen(
    movie: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String? = { null },
    getPersonImageUrl: (org.jellyfin.sdk.model.api.BaseItemPerson) -> String? = { null },
    onBackClick: () -> Unit,
    onPlayClick: (BaseItemDto, Int?) -> Unit = { item, _ -> },
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onShareClick: (BaseItemDto) -> Unit = {},
    onDeleteClick: (BaseItemDto) -> Unit = {},
    onMarkWatchedClick: (BaseItemDto) -> Unit = {},
    onRelatedMovieClick: (String) -> Unit = {},
    onPersonClick: (String, String) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onGenerateAiSummary: () -> Unit = {},
    aiSummary: String? = null,
    isLoadingAiSummary: Boolean = false,
    relatedItems: List<BaseItemDto> = emptyList(),
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    themes: List<String> = emptyList(),
    isLoadingThemes: Boolean = false,
    whyYoullLoveThis: String? = null,
    isLoadingWhyYoullLoveThis: Boolean = false,
    aiRecommendations: List<BaseItemDto> = emptyList(),
    isAiRecommendationsLoading: Boolean = false,
    isRefreshing: Boolean = false,
    serverUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val perfConfig = rememberImmersivePerformanceConfig()
    var isFavorite by remember { mutableStateOf(movie.userData?.isFavorite == true) }
    var isWatched by remember { mutableStateOf(movie.userData?.played == true) }
    var selectedSubtitleIndex by remember { mutableStateOf<Int?>(null) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    // Track scroll state - reset on navigation by using movie ID as key for remember
    val listState = remember(movie.id.toString()) { LazyListState() }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Static Hero Background (Fixed) - Extended to edges
        StaticHeroSection(
            imageUrl = getBackdropUrl(movie),
            height = ImmersiveDimens.HeroHeightPhone + 60.dp, // ✅ Increased height for top bleed
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-60).dp), // ✅ Bleed into status bar area
            content = {}, // Content moved to LazyColumn
        )

        // 2. Scrollable Content Layer
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 40.dp,
                ),
            ) {
                // Movie Hero Content (Logo, Title, Metadata) - Now scrolls
                item(key = "hero_content") {
                    MovieHeroContent(
                        movie = movie,
                        getLogoUrl = getLogoUrl,
                    )
                }

                // ✅ Solid background spacer to cover hero when scrolled
                item(key = "background_spacer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
                // Overview and AI Summary Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        movie.overview?.let { overview ->
                            if (overview.isNotBlank()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = overview,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3,
                                        textAlign = TextAlign.Center,
                                    )

                                    // AI Summary button and result
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TextButton(
                                            onClick = onGenerateAiSummary,
                                            enabled = !isLoadingAiSummary,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (aiSummary != null) "AI Summary" else "Generate AI Summary")
                                        }

                                        if (isLoadingAiSummary) {
                                            ExpressiveCircularLoading(size = 16.dp)
                                        }
                                    }

                                    // Show AI summary if available
                                    aiSummary?.let { summary ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Text(
                                                text = summary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Playback capability badge
                        playbackAnalysis?.let { analysis ->
                            PlaybackStatusBadge(analysis = analysis)
                        }
                    }
                }

                // "Why You'll Love This" AI Card
                if (whyYoullLoveThis != null || isLoadingWhyYoullLoveThis) {
                    item {
                        WhyYoullLoveThisCard(
                            pitch = whyYoullLoveThis,
                            isLoading = isLoadingWhyYoullLoveThis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Play Button and Action Row
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Primary Play Button
                        Surface(
                            onClick = { onPlayClick(movie, selectedSubtitleIndex) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Play Movie",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }

                        // Action Buttons Row (Favorite, Watched, Share, Delete)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ActionButton(
                                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                label = if (isFavorite) "Favorited" else "Favorite",
                                onClick = {
                                    isFavorite = !isFavorite
                                    onFavoriteClick(movie)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ActionButton(
                                icon = if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                label = if (isWatched) "Watched" else "Mark",
                                onClick = {
                                    isWatched = !isWatched
                                    onMarkWatchedClick(movie)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ActionButton(
                                icon = Icons.Rounded.Share,
                                label = "Share",
                                onClick = { onShareClick(movie) },
                                modifier = Modifier.weight(1f),
                            )
                            ActionButton(
                                icon = Icons.Rounded.Delete,
                                label = "Delete",
                                onClick = { showDeleteConfirmation = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Movie Info Card (Details)
                item {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        ImmersiveMovieInfoCard(
                            movie = movie,
                            getImageUrl = getImageUrl,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            onSubtitleSelect = { selectedSubtitleIndex = it },
                        )
                    }
                }

                // Cast & Crew Section
                movie.people?.takeIf { it.isNotEmpty() }?.let { people ->
                    val directors = people.filter { it.type.toString().equals("Director", ignoreCase = true) }
                    val writers = people.filter {
                        val type = it.type.toString()
                        type.equals("Writer", ignoreCase = true) ||
                            type.equals("Screenplay", ignoreCase = true)
                    }
                    val producers = people.filter {
                        val type = it.type.toString()
                        type.equals("Producer", ignoreCase = true) ||
                            type.equals("Executive Producer", ignoreCase = true)
                    }
                    val cast = people.filter { it.type.toString().equals("Actor", ignoreCase = true) }

                    if (directors.isNotEmpty() || writers.isNotEmpty() || producers.isNotEmpty() || cast.isNotEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                                ImmersiveCastAndCrewSection(
                                    directors = directors,
                                    writers = writers,
                                    producers = producers,
                                    cast = cast,
                                    getPersonImageUrl = getPersonImageUrl,
                                    onPersonClick = onPersonClick,
                                )
                            }
                        }
                    }
                }

                // Genres Section
                movie.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Genres",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(genres.take(perfConfig.maxRowItems), key = { it }) { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = JellyfinTeal80.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, JellyfinTeal80.copy(alpha = 0.3f)),
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = JellyfinTeal80,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Themes Section
                if (themes.isNotEmpty() || isLoadingThemes) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Themes",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Generated",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (isLoadingThemes) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(themes, key = { it }) { theme ->
                                        AssistChip(
                                            onClick = { /* TODO: Navigate to search with theme */ },
                                            label = {
                                                Text(
                                                    text = theme.replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                leadingIconContentColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Related Movies Section (Jellyfin metadata-based)
                if (relatedItems.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "More Like This",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )

                            PerformanceOptimizedLazyRow(
                                items = relatedItems,
                                horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                                maxVisibleItems = perfConfig.maxRowItems,
                            ) { relatedMovie, _, _ ->
                                ImmersiveMediaCard(
                                    title = relatedMovie.name ?: stringResource(id = R.string.unknown),
                                    subtitle = relatedMovie.productionYear?.toString() ?: "",
                                    imageUrl = getImageUrl(relatedMovie) ?: "",
                                    rating = relatedMovie.communityRating,
                                    onCardClick = {
                                        onRelatedMovieClick(relatedMovie.id.toString())
                                    },
                                    cardSize = ImmersiveCardSize.SMALL,
                                )
                            }
                        }
                    }
                }

                // AI Recommendations Section (AI-powered thematic matches)
                if (aiRecommendations.isNotEmpty() || isAiRecommendationsLoading) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "You Might Also Like",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Powered",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (isAiRecommendationsLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExpressiveCircularLoading(size = 32.dp)
                                        Text(
                                            text = "Finding recommendations...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                PerformanceOptimizedLazyRow(
                                    items = aiRecommendations,
                                    horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                                    maxVisibleItems = perfConfig.maxRowItems,
                                ) { recommendedMovie, _, _ ->
                                    ImmersiveMediaCard(
                                        title = recommendedMovie.name ?: stringResource(id = R.string.unknown),
                                        subtitle = recommendedMovie.productionYear?.toString() ?: "",
                                        imageUrl = getImageUrl(recommendedMovie) ?: "",
                                        rating = recommendedMovie.communityRating,
                                        onCardClick = {
                                            onRelatedMovieClick(recommendedMovie.id.toString())
                                        },
                                        cardSize = ImmersiveCardSize.SMALL,
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }

        // Floating Back and More Options Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Back Button
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.navigate_up),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }

            // More Options Button with Dropdown
            Box {
                Surface(
                    onClick = { showMoreOptions = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }

                DropdownMenu(
                    expanded = showMoreOptions,
                    onDismissRequest = { showMoreOptions = false },
                ) {
                    // Open in Browser
                    if (!serverUrl.isNullOrBlank()) {
                        val movieId = movie.id.toString()
                        DropdownMenuItem(
                            text = { Text("Open in Browser") },
                            onClick = {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        "$serverUrl/web/index.html#!/details?id=$movieId".toUri(),
                                    )
                                    context.startActivity(intent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "No browser found to open the URL",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                showMoreOptions = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInBrowser,
                                    contentDescription = null,
                                )
                            },
                        )
                    }

                    // Share
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            onShareClick(movie)
                            showMoreOptions = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                            )
                        },
                    )

                    // Delete
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showDeleteConfirmation = true
                            showMoreOptions = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Movie?") },
            text = {
                Text(
                    "Are you sure you want to delete \"${movie.name}\"? " +
                        "This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteClick(movie)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MovieHeroContent(
    movie: BaseItemDto,
    getLogoUrl: (BaseItemDto) -> String?,
) {
    val logoUrl = getLogoUrl(movie)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ImmersiveDimens.HeroHeightPhone)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo or Title
            if (!logoUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(120.dp)
                        .fillMaxWidth(0.8f),
                )
            } else {
                Text(
                    text = movie.name ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            // Metadata Row (Rating, Year, Runtime) - with proper spacing
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Community Rating with Yellow Star
                movie.communityRating?.takeIf { it > 0f }?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFD700), // Yellow Star
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.ROOT, "%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Critic Rating with Trophy (Stars fallback)
                movie.criticRating?.takeIf { it > 0f }?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star, // Trophy icon fallback
                            contentDescription = "Critic Rating",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${rating.roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Official Rating Badge (Color Coded)
                movie.officialRating?.let { rating ->
                    val normalizedRating = normalizeOfficialRating(rating) ?: return@let
                    val tintColor = when (normalizedRating) {
                        "G", "TV-G" -> Color(0xFF4CAF50) // Green
                        "PG", "TV-PG" -> Color(0xFFFFC107) // Amber
                        "PG-13", "TV-14" -> Color(0xFFFF9800) // Orange
                        "R", "TV-MA", "NC-17" -> Color(0xFFF44336) // Red
                        else -> Color.White.copy(alpha = 0.5f)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tintColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = normalizedRating,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // Year
                movie.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }

                // Runtime
                movie.runTimeTicks?.let { ticks ->
                    val minutes = (ticks / 10_000_000 / 60).toInt()
                    val hours = minutes / 60
                    val remainingMinutes = minutes % 60
                    val runtime = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

                    Text(
                        text = runtime,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

// Rating badge data classes and builders (Immersive variants to avoid redeclaration)
private enum class ImmersiveRatingSource(
    val icon: ImageVector,
    val contentDescription: String,
) {
    Community(Icons.Rounded.People, "Community rating"),
    Critics(Icons.Rounded.RateReview, "Critic rating"),
    ImdbLink(Icons.Rounded.Movie, "IMDb link available"),
    TmdbLink(Icons.Rounded.Public, "TMDB link available"),
}

private data class ImmersiveExternalRating(
    val source: ImmersiveRatingSource,
    val value: String? = null,
)

private fun buildRatingBadges(movie: BaseItemDto): List<ImmersiveExternalRating> {
    val providerKeys = movie.providerIds?.keys?.map { it }?.toSet().orEmpty()
    val hasImdb = "imdb" in providerKeys
    val hasTmdb = "tmdb" in providerKeys
    val badges = mutableListOf<ImmersiveExternalRating>()

    movie.communityRating?.takeIf { it > 0f }?.let { rating ->
        badges.add(
            ImmersiveExternalRating(
                source = ImmersiveRatingSource.Community,
                value = String.format(Locale.ROOT, "%.1f", rating),
            ),
        )
    }

    movie.criticRating?.takeIf { it > 0f }?.let { rating ->
        badges.add(
            ImmersiveExternalRating(
                source = ImmersiveRatingSource.Critics,
                value = "${rating.roundToInt()}%",
            ),
        )
    }

    if (hasTmdb) {
        badges.add(ImmersiveExternalRating(source = ImmersiveRatingSource.TmdbLink))
    }
    if (hasImdb) {
        badges.add(ImmersiveExternalRating(source = ImmersiveRatingSource.ImdbLink))
    }

    return badges
}

@Composable
private fun ExternalRatingBadge(
    source: ImmersiveRatingSource,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = source.icon,
                contentDescription = source.contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Immersive Movie Info Card Component
@Composable
private fun ImmersiveMovieInfoCard(
    movie: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    selectedSubtitleIndex: Int?,
    onSubtitleSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mediaStreams = movie.mediaSources?.firstOrNull()?.mediaStreams
    val subtitleStreams = mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE } ?: emptyList()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Tagline
            movie.taglines?.firstOrNull()?.let { tagline ->
                if (tagline.isNotBlank()) {
                    Text(
                        text = "\"$tagline\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Director
            movie.people?.firstOrNull { it.type.toString() == "Director" }?.let { director ->
                DetailRow(label = "Director", value = director.name ?: "Unknown")
            }

            // Studio
            movie.studios?.firstOrNull()?.name?.let { studio ->
                DetailRow(label = "Studio", value = studio)
            }

            // Release Date
            movie.premiereDate?.let { date ->
                val formattedDate = date.toString().substringBefore('T')
                DetailRow(label = "Release Date", value = formattedDate)
            }

            // ✨ Beautiful Material 3 Expressive Media Info Cards
            movie.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
                val videoStream = streams.findDefaultVideoStream()
                val audioStream = streams.firstOrNull { it.type == MediaStreamType.AUDIO }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Premium Video Info Card
                    videoStream?.let { stream ->
                        val resolution = ResolutionQuality.fromResolution(stream.width, stream.height)

                        val codecText = when (stream.codec?.lowercase()) {
                            "hevc", "h265" -> "HEVC"
                            "h264", "avc" -> "H.264"
                            "av1" -> "AV1"
                            "vp9" -> "VP9"
                            "mpeg2" -> "MPEG-2"
                            "mpeg4" -> "MPEG-4"
                            else -> stream.codec?.uppercase() ?: "UNKNOWN"
                        }

                        val hdrType = HdrType.detect(
                            stream.videoRange.toString(),
                            stream.videoRangeType.toString(),
                        )

                        VideoInfoCard(
                            resolution = resolution,
                            codec = codecText,
                            bitDepth = stream.bitDepth,
                            frameRate = stream.averageFrameRate?.toDouble(),
                            isHdr = hdrType != null,
                            hdrType = hdrType ?: HdrType.HDR,
                            is3D = stream.videoDoViTitle?.contains("3D", ignoreCase = true) == true
                        )
                    }

                    // Premium Audio Info Card
                    audioStream?.let { stream ->
                        val channelText = when (stream.channels) {
                            8 -> "7.1"
                            6 -> "5.1"
                            2 -> "Stereo"
                            1 -> "Mono"
                            else -> stream.channels?.toString()?.let { "$it.0" } ?: ""
                        }

                        val codecText = when (stream.codec?.lowercase()) {
                            "truehd" -> "TrueHD"
                            "eac3" -> "DD+"
                            "aac" -> "AAC"
                            "ac3" -> "DD"
                            "dca", "dts" -> "DTS"
                            "dtshd" -> "DTS-HD"
                            "flac" -> "FLAC"
                            "opus" -> "Opus"
                            "vorbis" -> "Vorbis"
                            else -> stream.codec?.uppercase() ?: "UNKNOWN"
                        }

                        val isAtmos = stream.title?.contains("atmos", ignoreCase = true) == true ||
                            stream.codec?.contains("atmos", ignoreCase = true) == true ||
                            stream.profile?.contains("atmos", ignoreCase = true) == true

                        AudioInfoCard(
                            channels = channelText,
                            codec = codecText,
                            isAtmos = isAtmos,
                            language = stream.language?.let { lang ->
                                when (lang.lowercase()) {
                                    "eng" -> "EN"
                                    "spa" -> "ES"
                                    "fre", "fra" -> "FR"
                                    "ger", "deu" -> "DE"
                                    "ita" -> "IT"
                                    "jpn" -> "JA"
                                    "kor" -> "KO"
                                    "chi", "zho" -> "ZH"
                                    else -> lang.take(2).uppercase()
                                }
                            }
                        )
                    }

                    // Subtitles
                    if (subtitleStreams.isNotEmpty()) {
                        ImmersiveSubtitleRow(
                            subtitles = subtitleStreams,
                            selectedSubtitleIndex = selectedSubtitleIndex,
                            onSubtitleSelect = onSubtitleSelect,
                        )
                    }
                }
            }

            // File Size
            movie.mediaSources?.firstOrNull()?.size?.let { sizeBytes ->
                val sizeGB = sizeBytes / 1_073_741_824.0
                DetailRow(label = "File Size", value = String.format("%.2f GB", sizeGB))
            }

            // Container Format
            movie.mediaSources?.firstOrNull()?.container?.let { container ->
                DetailRow(label = "Container", value = container)
            }

            // Play progress
            movie.userData?.playedPercentage?.let { progress ->
                if (progress > 0.0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${progress.roundToInt()}% watched",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        LinearProgressIndicator(
                            progress = { (progress / 100.0).toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ImmersiveInfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(8.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CastMemberCard(
    person: org.jellyfin.sdk.model.api.BaseItemPerson,
    imageUrl: String?,
    onPersonClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable {
                onPersonClick(person.id.toString(), person.name ?: "Unknown")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Profile Image (Larger for immersive design)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(140.dp),
        ) {
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = person.name,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }

        // Actor Name
        Text(
            text = person.name ?: "Unknown",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        // Role/Character
        person.role?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ImmersiveCastAndCrewSection(
    directors: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    writers: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    producers: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    cast: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    getPersonImageUrl: (org.jellyfin.sdk.model.api.BaseItemPerson) -> String?,
    onPersonClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perfConfig = rememberImmersivePerformanceConfig()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Section Title
        Text(
            text = "Cast & Crew",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        // Key Crew Information (Compact)
        if (directors.isNotEmpty() || writers.isNotEmpty() || producers.isNotEmpty()) {
            androidx.compose.material3.ElevatedCard(
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Directors
                    if (directors.isNotEmpty()) {
                        CrewInfoRow(
                            label = if (directors.size == 1) "Director" else "Directors",
                            people = directors,
                            onPersonClick = onPersonClick,
                        )
                    }

                    // Writers
                    if (writers.isNotEmpty()) {
                        CrewInfoRow(
                            label = if (writers.size == 1) "Writer" else "Writers",
                            people = writers,
                            onPersonClick = onPersonClick,
                        )
                    }

                    // Producers
                    if (producers.isNotEmpty()) {
                        CrewInfoRow(
                            label = if (producers.size == 1) "Producer" else "Producers",
                            people = producers,
                            onPersonClick = onPersonClick,
                        )
                    }
                }
            }
        }

        // Cast Section
        if (cast.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Cast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                ) {
                    items(cast.take(perfConfig.maxRowItems), key = { it.id.toString() }) { person ->
                        CastMemberCard(
                            person = person,
                            imageUrl = getPersonImageUrl(person),
                            onPersonClick = onPersonClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewInfoRow(
    label: String,
    people: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    onPersonClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            people.forEachIndexed { index, person ->
                Text(
                    text = (person.name ?: "Unknown") + if (index < people.size - 1) "," else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        onPersonClick(person.id.toString(), person.name ?: "Unknown")
                    },
                )
            }
        }
    }
}

@Composable
private fun ImmersiveVideoInfoRow(
    label: String,
    codec: String?,
    icon: ImageVector,
    resolutionBadge: Triple<ImageVector, String, Color>? = null,
    is3D: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(8.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                codec?.let { codecText ->
                    Text(
                        text = codecText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } ?: run {
                    Text(
                        text = stringResource(id = R.string.unknown),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 3D Badge
                if (is3D) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier,
                    ) {
                        Text(
                            text = "3D",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }

                // Quality badge (4K, FHD, HD, SD) - Using Material Symbols icons
                resolutionBadge?.let { (icon, label, color) ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = color,
                        modifier = Modifier,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "$label quality",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveSubtitleRow(
    subtitles: List<org.jellyfin.sdk.model.api.MediaStream>,
    selectedSubtitleIndex: Int?,
    onSubtitleSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSubtitle = if (selectedSubtitleIndex != null) {
        subtitles.find { it.index == selectedSubtitleIndex }
    } else {
        null
    }

    val labelText = currentSubtitle?.let {
        val lang = it.language ?: "UNK"
        val title = it.title ?: it.displayTitle
        if (title != null && title != lang) "$lang - $title" else lang
    } ?: "None"

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ClosedCaption,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(8.dp),
            )
        }

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { expanded = true },
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // None option
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        onSubtitleSelect(null)
                        expanded = false
                    },
                )

                subtitles.forEach { stream ->
                    DropdownMenuItem(
                        text = {
                            val lang = stream.language ?: "UNK"
                            val title = stream.title ?: stream.displayTitle
                            Text(if (title != null && title != lang) "$lang - $title" else lang)
                        },
                        onClick = {
                            onSubtitleSelect(stream.index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataTag(
    text: String,
    icon: ImageVector? = null,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getResolutionIcon(width: Int?, height: Int?): ImageVector {
    return Icons.Rounded.Movie
}

private fun getResolutionBadge(width: Int?, height: Int?): Triple<ImageVector, String, Color>? {
    val w = width ?: 0
    val h = height ?: 0

    return when {
        h >= 2160 || w >= 3840 -> Triple(Icons.Rounded.HighQuality, "4K", Quality4K) // ✅ Use HighQuality for 4K
        h >= 1440 || w >= 2560 -> Triple(Icons.Rounded.HighQuality, "1440p", Quality1440)
        h >= 1080 || w >= 1920 -> Triple(Icons.Rounded.Hd, "FHD", QualityHD)
        h >= 720 || w >= 1280 -> Triple(Icons.Rounded.Hd, "HD", QualityHD)
        h > 0 -> Triple(Icons.Rounded.Sd, "SD", QualitySD)
        else -> null
    }
}

/**
 * "Why You'll Love This" AI-powered personalized pitch card
 */
@Composable
private fun WhyYoullLoveThisCard(
    pitch: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Generated",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Why You'll Love This",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            // Content
            when {
                isLoading -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                pitch != null -> {
                    Text(
                        text = pitch,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.times(1.5f)
                    )
                }
            }
        }
    }
}
