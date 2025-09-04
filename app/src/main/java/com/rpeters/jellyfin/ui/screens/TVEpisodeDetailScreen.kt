package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveFloatingToolbar
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.ToolbarAction
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.Quality4K
import com.rpeters.jellyfin.ui.theme.QualityHD
import com.rpeters.jellyfin.ui.theme.QualitySD
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TVEpisodeDetailScreen(
    episode: BaseItemDto,
    seriesInfo: BaseItemDto? = null,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onPlayClick: (BaseItemDto) -> Unit = {},
    onDownloadClick: (BaseItemDto) -> Unit = {},
    onDeleteClick: (BaseItemDto) -> Unit = {},
    onMarkWatchedClick: (BaseItemDto) -> Unit = {},
    onMarkUnwatchedClick: (BaseItemDto) -> Unit = {},
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    modifier: Modifier = Modifier,
) {
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = episode.name ?: "Episode",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
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
    ) { paddingValues ->
        AnimatedContent(
            targetState = !isLoading,
            transitionSpec = {
                fadeIn(MotionTokens.expressiveEnter) + slideInVertically { it / 4 } togetherWith
                    fadeOut(MotionTokens.expressiveExit) + slideOutVertically { -it / 4 }
            },
            label = "episode_detail_content",
        ) { showContent ->
            if (showContent) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // Hero Section with Episode Image
                        item {
                            ExpressiveEpisodeHero(
                                episode = episode,
                                seriesInfo = seriesInfo,
                                getBackdropUrl = getBackdropUrl,
                                getImageUrl = getImageUrl,
                            )
                        }

                        // Episode Information Card
                        item {
                            ExpressiveEpisodeInfoCard(episode = episode)
                        }

                        // Action Buttons
                        item {
                            ExpressiveEpisodeActions(
                                episode = episode,
                                onDownloadClick = onDownloadClick,
                                onDeleteClick = onDeleteClick,
                                onMarkWatchedClick = onMarkWatchedClick,
                                onMarkUnwatchedClick = onMarkUnwatchedClick,
                                onFavoriteClick = onFavoriteClick,
                                playbackAnalysis = playbackAnalysis,
                            )
                        }

                        // Episode Details
                        item {
                            ExpressiveEpisodeOverview(episode = episode)
                        }

                        // Series Information (if available)
                        seriesInfo?.let { series ->
                            item {
                                ExpressiveSeriesInfo(
                                    series = series,
                                    getImageUrl = getImageUrl,
                                )
                            }
                        }

                        // Extra spacing for FAB
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }

                    // Add ExpressiveFloatingToolbar for episode details
                    ExpressiveFloatingToolbar(
                        isVisible = true,
                        onPlayClick = { onPlayClick(episode) },
                        onQueueClick = { /* TODO: Implement queue functionality */ },
                        onDownloadClick = { onDownloadClick(episode) },
                        onCastClick = { /* TODO: Implement cast functionality */ },
                        onFavoriteClick = { onFavoriteClick(episode) },
                        onShareClick = { /* TODO: Implement share functionality */ },
                        onMoreClick = { /* TODO: Implement more options functionality */ },
                        primaryAction = ToolbarAction.PLAY,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveEpisodeHero(
    episode: BaseItemDto,
    seriesInfo: BaseItemDto?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    val heroScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "hero_scale",
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .graphicsLayer {
                scaleX = heroScale
                scaleY = heroScale
            },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image (episode or series backdrop)
            val backdropUrl = getBackdropUrl(episode) ?: seriesInfo?.let { getBackdropUrl(it) }

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = episode.name,
                loading = {
                    ExpressiveLoadingCard(
                        modifier = Modifier.fillMaxSize(),
                        showTitle = false,
                        showSubtitle = false,
                        imageHeight = 360.dp,
                    )
                },
                error = {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(80.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = episode.name ?: "Episode",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp)),
            )

            // Enhanced gradient overlay for better text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.95f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            // Episode information overlay with enhanced typography
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                seriesInfo?.name?.let { seriesName ->
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                    )
                }

                Text(
                    text = episode.name ?: "Unknown Episode",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    episode.indexNumber?.let { episodeNum ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        ) {
                            Text(
                                text = "EP $episodeNum",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    episode.parentIndexNumber?.let { seasonNum ->
                        Text(
                            text = "S$seasonNum",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // Resolution badge
                    getEpisodeResolution(episode)?.let { (resolution, color) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = color.copy(alpha = 0.9f),
                        ) {
                            Text(
                                text = resolution,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    episode.communityRating?.let { rating ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = String.format(java.util.Locale.ROOT, "%.1f", rating),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Play progress with enhanced styling
                episode.userData?.playedPercentage?.let { progress ->
                    if (progress > 0.0) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = "${String.format(java.util.Locale.ROOT, "%.0f", progress)}% watched",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium,
                            )
                            LinearProgressIndicator(
                                progress = { (progress / 100.0).toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveEpisodeInfoCard(
    episode: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    val cardScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "info_card_scale",
    )

    val mediaStreams = episode.mediaSources?.firstOrNull()?.mediaStreams
    val videoStream = mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audioStream = mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Episode Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    episode.runTimeTicks?.let { ticks ->
                        val minutes = (ticks / 10_000_000 / 60).toInt()
                        val hours = minutes / 60
                        val remainingMinutes = minutes % 60
                        val runtime = if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"

                        ExpressiveInfoRow(
                            label = "Duration",
                            value = runtime,
                            icon = Icons.Default.PlayArrow,
                        )
                    }

                    episode.premiereDate?.let { date ->
                        val dateString = formatDate(date)
                        ExpressiveInfoRow(
                            label = "Air Date",
                            value = dateString,
                            icon = Icons.Default.Star,
                        )
                    }

                    episode.productionYear?.let { year ->
                        ExpressiveInfoRow(
                            label = "Year",
                            value = year.toString(),
                            icon = Icons.Default.Star,
                        )
                    }

                    // Video information
                    val videoResolution = getEpisodeResolution(episode)?.first
                    videoStream?.let { stream ->
                        ExpressiveInfoRow(
                            label = stringResource(id = R.string.video),
                            value = listOfNotNull(videoResolution, stream.codec?.uppercase()).joinToString(" \u2022 "),
                            icon = Icons.Default.HighQuality,
                        )
                    }

                    // Audio information
                    audioStream?.codec?.let { codec ->
                        ExpressiveInfoRow(
                            label = stringResource(id = R.string.audio),
                            value = codec.uppercase(),
                            icon = Icons.Default.Audiotrack,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    episode.userData?.let { userData ->
                        ExpressiveInfoRow(
                            label = "Watched",
                            value = if (userData.played == true) "Yes" else "No",
                            icon = if (userData.played == true) Icons.Default.CheckCircle else Icons.Default.VisibilityOff,
                        )

                        userData.playCount?.let { count ->
                            if (count > 0) {
                                ExpressiveInfoRow(
                                    label = "Play Count",
                                    value = count.toString(),
                                    icon = Icons.Default.Refresh,
                                )
                            }
                        }

                        userData.lastPlayedDate?.let { date ->
                            val dateString = formatDate(date)
                            ExpressiveInfoRow(
                                label = "Last Played",
                                value = dateString,
                                icon = Icons.Default.Star,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

private fun formatDate(date: Any): String {
    return try {
        when {
            date is java.time.LocalDate -> {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
                date.format(formatter)
            }
            date is java.time.OffsetDateTime -> {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
                date.toLocalDate().format(formatter)
            }
            date is java.time.LocalDateTime -> {
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
                date.toLocalDate().format(formatter)
            }
            else -> {
                date.toString().substringBefore('T').let { dateStr ->
                    try {
                        val localDate = java.time.LocalDate.parse(dateStr)
                        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
                        localDate.format(formatter)
                    } catch (e: Exception) {
                        dateStr
                    }
                }
            }
        }
    } catch (e: Exception) {
        date.toString().substringBefore('T')
    }
}

/**
 * Get episode resolution label and color based on video height
 * Requirements:
 * - Under 720p -> SD
 * - 720p -> 720p
 * - 1080p -> 1080p
 * - Over 1080p -> 4K
 */
private fun getEpisodeResolution(episode: BaseItemDto): Pair<String, Color>? {
    val mediaSource = episode.mediaSources?.firstOrNull() ?: return null
    val videoStream = mediaSource.mediaStreams?.firstOrNull {
        it.type == MediaStreamType.VIDEO
    } ?: return null

    val height = videoStream.height ?: return null

    return when {
        height > 1080 -> "4K" to Quality4K
        height == 1080 -> "1080p" to QualityHD
        height == 720 -> "720p" to QualityHD
        height < 720 -> "SD" to QualitySD
        else -> null
    }
}

@Composable
private fun ExpressiveEpisodeActions(
    episode: BaseItemDto,
    onDownloadClick: (BaseItemDto) -> Unit,
    onDeleteClick: (BaseItemDto) -> Unit,
    onMarkWatchedClick: (BaseItemDto) -> Unit,
    onMarkUnwatchedClick: (BaseItemDto) -> Unit,
    onFavoriteClick: (BaseItemDto) -> Unit,
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    modifier: Modifier = Modifier,
) {
    val actionsScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "actions_scale",
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = actionsScale
                scaleY = actionsScale
            },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                playbackAnalysis?.let { analysis ->
                    PlaybackStatusBadge(analysis = analysis)
                }
            }

            // Primary Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { onDownloadClick(episode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download",
                        fontWeight = FontWeight.Medium,
                    )
                }

                OutlinedButton(
                    onClick = { onDeleteClick(episode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Watch Status Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val isWatched = episode.userData?.played == true

                if (isWatched) {
                    OutlinedButton(
                        onClick = { onMarkUnwatchedClick(episode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mark Unwatched",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = { onMarkWatchedClick(episode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mark Watched",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                val isFavorite = episode.userData?.isFavorite == true
                val favoriteColor = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface

                OutlinedButton(
                    onClick = { onFavoriteClick(episode) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = favoriteColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFavorite) "Unfavorite" else "Favorite",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveEpisodeOverview(
    episode: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
        val overviewScale by animateFloatAsState(
            targetValue = 1.0f,
            animationSpec = MotionTokens.expressiveEnter,
            label = "overview_scale",
        )

        ElevatedCard(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = overviewScale
                    scaleY = overviewScale
                },
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(6.dp),
                        )
                    }

                    Text(
                        text = "Synopsis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSeriesInfo(
    series: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    val seriesScale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "series_scale",
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = seriesScale
                scaleY = seriesScale
            },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(6.dp),
                    )
                }

                Text(
                    text = "About the Series",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Series poster with enhanced styling
                SubcomposeAsyncImage(
                    model = getImageUrl(series),
                    contentDescription = series.name,
                    loading = {
                        ExpressiveLoadingCard(
                            modifier = Modifier
                                .width(90.dp)
                                .height(135.dp),
                            showTitle = false,
                            showSubtitle = false,
                            imageHeight = 135.dp,
                        )
                    },
                    error = {
                        Surface(
                            modifier = Modifier
                                .width(90.dp)
                                .height(135.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = series.name ?: "Series",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(90.dp)
                        .height(135.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                // Series details with enhanced layout
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = series.name ?: "Unknown Series",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        series.productionYear?.let { year ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = year.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        series.communityRating?.let { rating ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = String.format(java.util.Locale.ROOT, "%.1f", rating),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    series.childCount?.let { count ->
                        Text(
                            text = "$count episodes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    series.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.1,
                        )
                    }
                }
            }
        }
    }
}
