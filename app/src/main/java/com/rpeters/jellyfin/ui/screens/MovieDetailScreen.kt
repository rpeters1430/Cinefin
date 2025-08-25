package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ShimmerBox
import com.rpeters.jellyfin.ui.theme.JellyfinBlue80
import com.rpeters.jellyfin.ui.theme.JellyfinTeal80
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.MovieRed
import com.rpeters.jellyfin.ui.theme.MusicGreen
import com.rpeters.jellyfin.ui.theme.RatingGold
import com.rpeters.jellyfin.ui.theme.getContentTypeColor
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale
import kotlin.math.roundToInt

private val GENRE_BADGE_MAX_WIDTH = 100.dp

@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
) {
    var isFavorite by remember { mutableStateOf(movie.userData?.isFavorite == true) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 16.dp),
                    ) {
                        Text(
                            text = movie.name ?: "Movie Details",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBackClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                },
                actions = {
                    Surface(
                        onClick = {
                            isFavorite = !isFavorite
                            onFavoriteClick(movie)
                        },
                        shape = CircleShape,
                        color = if (isFavorite) MovieRed.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                        shadowElevation = if (isFavorite) 8.dp else 4.dp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) MovieRed else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Surface(
                        onClick = { onShareClick(movie) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 8.dp, start = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            val playButtonScale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "play_button_scale",
            )

            Surface(
                onClick = { onPlayClick(movie) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 16.dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = playButtonScale
                    scaleY = playButtonScale
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Play Movie",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Expressive Movie Hero Section
            item {
                ExpressiveMovieHero(
                    movie = movie,
                    getBackdropUrl = getBackdropUrl,
                )
            }

            // Expressive Movie Info Card
            item {
                ExpressiveMovieInfoCard(
                    movie = movie,
                    getImageUrl = getImageUrl,
                )
            }

            // Expressive Overview Section
            movie.overview?.let { overview ->
                item {
                    AnimatedContent(
                        targetState = overview.isNotBlank(),
                        transitionSpec = {
                            fadeIn() + slideInVertically { it / 2 } togetherWith fadeOut()
                        },
                        label = "overview_animation",
                    ) { hasOverview ->
                        if (hasOverview) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = 8.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    ) {
                                        Text(
                                            text = "Overview",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                    Text(
                                        text = overview,
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Related Movies Section with Expressive Cards
            if (relatedItems.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = "You might also like",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            items(relatedItems.take(10)) { relatedMovie ->
                                val scale by animateFloatAsState(
                                    targetValue = 1.0f,
                                    animationSpec = MotionTokens.expressiveEnter,
                                    label = "related_movie_scale",
                                )

                                ExpressiveMediaCard(
                                    title = relatedMovie.name ?: "Unknown",
                                    subtitle = relatedMovie.productionYear?.toString() ?: "",
                                    imageUrl = getImageUrl(relatedMovie) ?: "",
                                    rating = relatedMovie.communityRating?.toFloat(),
                                    onCardClick = { /* Navigate to related movie */ },
                                    modifier = Modifier
                                        .width(160.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

// Expressive Movie Hero Component
@Composable
private fun ExpressiveMovieHero(
    movie: BaseItemDto,
    getBackdropUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    val heroScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "hero_scale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .graphicsLayer {
                scaleX = heroScale
                scaleY = heroScale
            },
    ) {
        // Enhanced backdrop with dynamic colors
        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(getBackdropUrl(movie))
                .crossfade(true)
                .build(),
            contentDescription = "${movie.name} backdrop",
            loading = {
                ShimmerBox(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 28,
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
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "No backdrop",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp)),
        )

        // Expressive gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.6f),
                        ),
                        radius = 800f,
                    ),
                )
                .clip(RoundedCornerShape(28.dp)),
        )

        // Rating badge with enhanced design
        movie.communityRating?.let { rating ->
            val ratingScale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "rating_scale",
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .graphicsLayer {
                        scaleX = ratingScale
                        scaleY = ratingScale
                    },
                shape = RoundedCornerShape(20.dp),
                color = RatingGold.copy(alpha = 0.95f),
                shadowElevation = 12.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = String.format(java.util.Locale.ROOT, "%.1f", rating),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
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
    val cardScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "info_card_scale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Enhanced Movie Poster
            coil.compose.SubcomposeAsyncImage(
                model = getImageUrl(movie),
                contentDescription = "${movie.name} poster",
                loading = {
                    ExpressiveLoadingCard(
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(2f / 3f),
                    )
                },
                error = {
                    Surface(
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(2f / 3f),
                        shape = RoundedCornerShape(16.dp),
                        color = getContentTypeColor(movie.type?.toString()).copy(alpha = 0.15f),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "No poster",
                                modifier = Modifier.size(40.dp),
                                tint = getContentTypeColor(movie.type?.toString()),
                            )
                        }
                    }
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp)),
            )

            // Movie Details with Expressive styling
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Title with dynamic emphasis
                Text(
                    text = movie.name ?: "Unknown Title",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Production year and runtime with enhanced styling
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    movie.productionYear?.let { year ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = JellyfinBlue80.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = JellyfinBlue80,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    movie.runTimeTicks?.let { ticks ->
                        val minutes = (ticks / 10_000_000 / 60).toInt()
                        val hours = minutes / 60
                        val remainingMinutes = minutes % 60
                        val runtime = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MusicGreen.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = runtime,
                                style = MaterialTheme.typography.labelMedium,
                                color = MusicGreen,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // Genres with colorful design
                movie.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(genres.take(3)) { genre ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = JellyfinTeal80.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JellyfinTeal80,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .widthIn(max = GENRE_BADGE_MAX_WIDTH),
                                )
                            }
                        }
                    }
                }

                // Media info with resolution and codecs
                movie.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
                    val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
                    val audioStream = streams.firstOrNull { it.type == MediaStreamType.AUDIO }
                    val resolution = getMovieResolution(videoStream)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        videoStream?.let { stream ->
                            ExpressiveInfoRow(
                                label = stringResource(id = R.string.video),
                                value = listOfNotNull(resolution, stream.codec?.uppercase()).joinToString(" \u2022 "),
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

                // Play progress with enhanced styling
                movie.userData?.playedPercentage?.let { progress ->
                    if (progress > 0.0) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = "${progress.roundToInt()}% watched",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium,
                            )
                            LinearProgressIndicator(
                                progress = { (progress / 100.0).toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .size(16.dp)
                    .padding(4.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
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
}

// Helper function for movie resolution detection
private fun getMovieResolution(videoStream: org.jellyfin.sdk.model.api.MediaStream?): String {
    return when (videoStream?.height) {
        null -> "Unknown"
        in 1..719 -> "SD"
        720 -> "720p"
        in 721..1079 -> "HD"
        1080 -> "1080p"
        in 1081..2159 -> "QHD"
        else -> "4K"
    }
}
