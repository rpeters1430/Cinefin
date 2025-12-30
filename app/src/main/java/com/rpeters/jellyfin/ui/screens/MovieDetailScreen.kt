package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.ShimmerBox
import com.rpeters.jellyfin.ui.theme.JellyfinBlue80
import com.rpeters.jellyfin.ui.theme.JellyfinTeal80
import com.rpeters.jellyfin.ui.theme.RatingGold
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import kotlin.math.roundToInt

private val GENRE_BADGE_MAX_WIDTH = 100.dp

@OptInAppExperimentalApis
@Composable
fun MovieDetailScreen(
    movie: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onPlayClick: (BaseItemDto) -> Unit = {},
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onShareClick: (BaseItemDto) -> Unit = {},
    relatedItems: List<BaseItemDto> = emptyList(),
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    modifier: Modifier = Modifier,
) {
    var isFavorite by remember { mutableStateOf(movie.userData?.isFavorite == true) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Full-bleed Hero Section - Google TV style
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(600.dp),
                ) {
                    // Backdrop Image - Full bleed
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(getBackdropUrl(movie))
                            .crossfade(true)
                            .build(),
                        contentDescription = "${movie.name} backdrop",
                        loading = {
                            ShimmerBox(
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 0,
                            )
                        },
                        error = {
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
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Gradient Scrim - Darker at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY,
                                ),
                            ),
                    )

                    // Content overlaid on bottom portion
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // Title
                        Text(
                            text = movie.name ?: stringResource(R.string.unknown),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Metadata Row (Rating, Year, Runtime)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Rating with star icon
                            movie.communityRating?.let { rating ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = RatingGold,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        text = "${(rating * 10).roundToInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            // Official Rating Badge (if available)
                            movie.officialRating?.let { rating ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    ),
                                ) {
                                    Text(
                                        text = rating,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            // Year
                            movie.productionYear?.let { year ->
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                )
                            }
                        }

                        // Overview
                        movie.overview?.let { overview ->
                            if (overview.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = overview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2,
                                )
                            }
                        }

                        // Playback capability badge
                        playbackAnalysis?.let { analysis ->
                            Spacer(modifier = Modifier.height(12.dp))
                            PlaybackStatusBadge(analysis = analysis)
                        }
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
                        onClick = { onPlayClick(movie) },
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
                                imageVector = Icons.Default.PlayArrow,
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

                    // Action Buttons Row (Favorite, Watched, etc.)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ActionButton(
                            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = if (isFavorite) "Favorited" else "Favorite",
                            onClick = {
                                isFavorite = !isFavorite
                                onFavoriteClick(movie)
                            },
                        )
                        ActionButton(
                            icon = Icons.Default.Share,
                            label = "Share",
                            onClick = { onShareClick(movie) },
                        )
                    }
                }
            }

            // Movie Info Card
            item {
                ExpressiveMovieInfoCard(
                    movie = movie,
                    getImageUrl = getImageUrl,
                )
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
                                    onCardClick = { /* Navigate to related movie */ },
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

            // More Options Button
            Surface(
                onClick = { /* Show more options */ },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp).size(24.dp),
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
    modifier: Modifier = Modifier,
) {
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

            // Media info with resolution and codecs
            movie.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
                val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
                val audioStream = streams.firstOrNull { it.type == MediaStreamType.AUDIO }
                val resolution = getMovieResolution(videoStream, stringResource(id = R.string.unknown))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    videoStream?.let { stream ->
                        ExpressiveInfoRow(
                            label = stringResource(id = R.string.video),
                            value = listOfNotNull(resolution, stream.codec?.uppercase()).joinToString(" • "),
                            icon = Icons.Default.HighQuality,
                        )
                    }

                    audioStream?.codec?.let { codec ->
                        ExpressiveInfoRow(
                            label = stringResource(id = R.string.audio),
                            value = codec.uppercase(),
                            icon = Icons.Default.Audiotrack,
                        )
                    }
                }
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

// Helper function for movie resolution detection
private fun getMovieResolution(
    videoStream: org.jellyfin.sdk.model.api.MediaStream?,
    unknownLabel: String,
): String {
    return when (videoStream?.height) {
        null -> unknownLabel
        in 1..719 -> "SD"
        720 -> "720p"
        in 721..1079 -> "HD"
        1080 -> "1080p"
        in 1081..2159 -> "QHD"
        else -> "4K"
    }
}
