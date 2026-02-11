package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.HeroImageWithLogo
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.ShimmerBox
import com.rpeters.jellyfin.ui.theme.JellyfinBlue80
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

private val GENRE_BADGE_MAX_WIDTH = 100.dp

@OptIn(ExperimentalMaterial3Api::class)
@OptInAppExperimentalApis
@Composable
fun MovieDetailScreen(
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
    onRefresh: () -> Unit = {},
    onGenerateAiSummary: () -> Unit = {},
    aiSummary: String? = null,
    isLoadingAiSummary: Boolean = false,
    relatedItems: List<BaseItemDto> = emptyList(),
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    isRefreshing: Boolean = false,
    serverUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var isFavorite by remember { mutableStateOf(movie.userData?.isFavorite == true) }
    var isWatched by remember { mutableStateOf(movie.userData?.played == true) }
    var selectedSubtitleIndex by remember { mutableStateOf<Int?>(null) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Full-bleed Hero Section - Extended to screen edges
                item {
                    HeroImageWithLogo(
                        imageUrl = getBackdropUrl(movie),
                        logoUrl = getLogoUrl(movie),
                        contentDescription = "${movie.name} backdrop",
                        logoContentDescription = "${movie.name} logo",
                        minHeight = 400.dp,
                        aspectRatio = 1.0f,
                        loadingContent = {
                            ShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 0,
                            )
                        },
                        errorContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                JellyfinBlue80.copy(alpha = 0.3f),
                                                JellyfinTeal80.copy(alpha = 0.2f),
                                            ),
                                        ),
                                    ),
                            )
                        },
                    )
                }

                // Title and Metadata Section (Below Image)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 20.dp)
                            .padding(top = 24.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Title
                        Text(
                            text = movie.name ?: stringResource(R.string.unknown),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Metadata Row (Rating, Year, Runtime)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val ratingBadges = remember(movie) { buildRatingBadges(movie) }
                            if (ratingBadges.isNotEmpty()) {
                                ratingBadges.forEach { rating ->
                                    ExternalRatingBadge(
                                        source = rating.source,
                                        value = rating.value,
                                    )
                                }
                            }

                            // Official Rating Badge (if available)
                            movie.officialRating?.let { rating ->
                                val normalizedRating = normalizeOfficialRating(rating) ?: return@let
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Text(
                                        text = normalizedRating,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            // Year
                            movie.productionYear?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
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
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                )
                            }
                        }

                        // Overview
                        movie.overview?.let { overview ->
                            if (overview.isNotBlank()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = overview,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3,
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
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
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

                // Play Button and Action Row
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 20.dp)
                            .padding(top = 24.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
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

                        // Action Buttons Row (Favorite, Watched, Delete, Share)
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
                                label = if (isWatched) "Watched" else "Mark Watched",
                                onClick = {
                                    isWatched = !isWatched
                                    onMarkWatchedClick(movie)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Secondary Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
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

                // Movie Info Card
                item {
                    ExpressiveMovieInfoCard(
                        movie = movie,
                        getImageUrl = getImageUrl,
                        selectedSubtitleIndex = selectedSubtitleIndex,
                        onSubtitleSelect = { selectedSubtitleIndex = it },
                    )
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
                            EnhancedCastAndCrewSection(
                                directors = directors,
                                writers = writers,
                                producers = producers,
                                cast = cast,
                                getPersonImageUrl = getPersonImageUrl,
                            )
                        }
                    }
                }

                // Genres Section
                movie.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
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
                                items(genres, key = { it }) { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = JellyfinTeal80.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, JellyfinTeal80.copy(alpha = 0.3f)),
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

                // Related Movies Section
                if (relatedItems.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "More Like This",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(relatedItems.take(10), key = { it.id.toString() }) { relatedMovie ->
                                    ExpressiveMediaCard(
                                        title = relatedMovie.name ?: stringResource(id = R.string.unknown),
                                        subtitle = relatedMovie.productionYear?.toString() ?: "",
                                        imageUrl = getImageUrl(relatedMovie) ?: "",
                                        rating = relatedMovie.communityRating,
                                        onCardClick = {
                                            onRelatedMovieClick(relatedMovie.id.toString())
                                        },
                                        modifier = Modifier.width(140.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // Floating Action Buttons - Overlaid on top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Back Button
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
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

private enum class RatingSource(
    val icon: ImageVector,
    val contentDescription: String,
) {
    Community(Icons.Rounded.People, "Community rating"),
    Critics(Icons.Rounded.RateReview, "Critic rating"),
    ImdbLink(Icons.Rounded.Movie, "IMDb link available"),
    TmdbLink(Icons.Rounded.Public, "TMDB link available"),
}

private data class ExternalRating(
    val source: RatingSource,
    val value: String? = null,
)

private fun buildRatingBadges(movie: BaseItemDto): List<ExternalRating> {
    val providerKeys = movie.providerIds?.keys?.map { it.lowercase(Locale.ROOT) }?.toSet().orEmpty()
    val hasImdb = "imdb" in providerKeys
    val hasTmdb = "tmdb" in providerKeys
    val badges = mutableListOf<ExternalRating>()

    movie.communityRating?.takeIf { it > 0f }?.let { rating ->
        badges.add(
            ExternalRating(
                source = RatingSource.Community,
                value = String.format(Locale.ROOT, "%.1f", rating),
            ),
        )
    }

    movie.criticRating?.takeIf { it > 0f }?.let { rating ->
        badges.add(
            ExternalRating(
                source = RatingSource.Critics,
                value = "${rating.roundToInt()}%",
            ),
        )
    }

    if (hasTmdb) {
        badges.add(ExternalRating(source = RatingSource.TmdbLink))
    }
    if (hasImdb) {
        badges.add(ExternalRating(source = RatingSource.ImdbLink))
    }

    return badges
}

@Composable
private fun ExternalRatingBadge(
    source: RatingSource,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Expressive Movie Info Card Component
@Composable
private fun ExpressiveMovieInfoCard(
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
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            // Media info with resolution and codecs
            movie.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
                val videoStream = streams.findDefaultVideoStream()
                val audioStream = streams.firstOrNull { it.type == MediaStreamType.AUDIO }
                // Check for 3D format from media source
                val mediaSource = movie.mediaSources?.firstOrNull()
                val is3D = mediaSource?.video3DFormat != null

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    videoStream?.let { stream ->
                        val icon = getResolutionIcon(stream.width, stream.height)
                        val resolutionBadge = getResolutionBadge(stream.width, stream.height)
                        ExpressiveVideoInfoRow(
                            label = stringResource(id = R.string.video),
                            codec = stream.codec?.uppercase(),
                            icon = icon,
                            resolutionBadge = resolutionBadge,
                            is3D = is3D,
                        )
                    }

                    audioStream?.codec?.let { codec ->
                        ExpressiveInfoRow(
                            label = stringResource(id = R.string.audio),
                            value = codec.uppercase(),
                            icon = Icons.Rounded.MusicNote,
                        )
                    }

                    // Subtitles
                    if (subtitleStreams.isNotEmpty()) {
                        ExpressiveSubtitleRow(
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
                DetailRow(label = "Container", value = container.uppercase())
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
private fun ExpressiveInfoRow(
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
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Profile Image
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(100.dp),
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
                                modifier = Modifier.size(48.dp),
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
                                modifier = Modifier.size(48.dp),
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
                        modifier = Modifier.size(48.dp),
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        // Role/Character
        person.role?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EnhancedCastAndCrewSection(
    directors: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    writers: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    producers: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    cast: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    getPersonImageUrl: (org.jellyfin.sdk.model.api.BaseItemPerson) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
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
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Directors
                    if (directors.isNotEmpty()) {
                        CrewInfoRow(
                            label = if (directors.size == 1) "Director" else "Directors",
                            people = directors,
                        )
                    }

                    // Writers
                    if (writers.isNotEmpty()) {
                        CrewInfoRow(
                            label = if (writers.size == 1) "Writer" else "Writers",
                            people = writers,
                        )
                    }

                    // Producers
                    if (producers.isNotEmpty()) {
                        CrewInfoRow(
                            label = if (producers.size == 1) "Producer" else "Producers",
                            people = producers,
                        )
                    }
                }
            }
        }

        // Cast Section
        if (cast.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Cast",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(cast.take(15), key = { it.id.toString() }) { person ->
                        CastMemberCard(
                            person = person,
                            imageUrl = getPersonImageUrl(person),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = people.joinToString(", ") { it.name ?: "Unknown" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ExpressiveVideoInfoRow(
    label: String,
    codec: String?,
    icon: ImageVector,
    resolutionBadge: Pair<String, Color>? = null,
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
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp),
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

                // Quality badge (4K, FHD, HD, SD)
                resolutionBadge?.let { (text, color) ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = color,
                        modifier = Modifier,
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                // 3D badge
                if (is3D) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier,
                    ) {
                        Text(
                            text = "3D",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveSubtitleRow(
    subtitles: List<org.jellyfin.sdk.model.api.MediaStream>,
    selectedSubtitleIndex: Int?,
    onSubtitleSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Default to None unless a subtitle is explicitly selected
    val currentSubtitle = if (selectedSubtitleIndex != null) {
        subtitles.find { it.index == selectedSubtitleIndex }
    } else {
        null
    }

    val labelText = currentSubtitle?.let {
        val lang = it.language?.uppercase(java.util.Locale.ROOT) ?: "UNK"
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
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ClosedCaption,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp),
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
                            val lang = stream.language?.uppercase(java.util.Locale.ROOT) ?: "UNK"
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

private fun getResolutionIcon(width: Int?, height: Int?): ImageVector {
    // Using generic video icon as Material Icons doesn't include specific resolution icons
    return Icons.Outlined.Movie
}

private fun getResolutionBadge(width: Int?, height: Int?): Pair<String, Color>? {
    val w = width ?: 0
    val h = height ?: 0

    return when {
        h >= 2160 || w >= 3840 -> "4K" to Quality4K
        h >= 1440 || w >= 2560 -> "1440p" to Quality1440
        h >= 1080 || w >= 1920 -> "FHD" to QualityHD
        h >= 720 || w >= 1280 -> "HD" to QualityHD
        h > 0 -> "SD" to QualitySD
        else -> null
    }
}
