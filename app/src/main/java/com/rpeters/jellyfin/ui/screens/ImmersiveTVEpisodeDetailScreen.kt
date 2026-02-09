package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.isWatched
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale

/**
 * Immersive TV Episode Detail screen.
 * Cinematic view for individual episodes with parallax hero and season context.
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveTVEpisodeDetailScreen(
    episode: BaseItemDto,
    seriesInfo: BaseItemDto? = null,
    seasonEpisodes: List<BaseItemDto> = emptyList(),
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onEpisodeClick: (BaseItemDto) -> Unit = {},
    onViewSeriesClick: () -> Unit = {},
    onPlayClick: (BaseItemDto, Int?) -> Unit = { _, _ -> },
    onDownloadClick: (BaseItemDto) -> Unit = {},
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onMarkWatchedClick: (BaseItemDto) -> Unit = {},
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    onGenerateAiSummary: () -> Unit = {},
    aiSummary: String? = null,
    isLoadingAiSummary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val perfConfig = rememberImmersivePerformanceConfig()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val mainAppViewModel: MainAppViewModel = hiltViewModel()

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    val heroImage = getBackdropUrl(episode).takeIf { !it.isNullOrBlank() }
        ?: seriesInfo?.let { getBackdropUrl(it) }
        ?: getImageUrl(episode).orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        // Static Hero Background (Fixed - doesn't scroll)
        StaticHeroSection(
            imageUrl = heroImage.takeIf { it.isNotBlank() },
            height = ImmersiveDimens.HeroHeightPhone,
            contentScale = ContentScale.Crop,
            content = {}, // Content moved to LazyColumn
        )

        // Scrollable Content Layer
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // 1. Hero Content (overlays hero initially, scrolls away)
            item(key = "hero_content") {
                EpisodeHeroContent(
                    episode = episode,
                    seriesInfo = seriesInfo,
                    onPlayClick = { onPlayClick(episode, null) },
                )
            }

            // Background spacer to cover hero when scrolled
            item(key = "background_spacer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.background),
                )
            }

            // 2. Overview & AI Summary
            item(key = "overview") {
                Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    EpisodeOverviewSection(
                        episode = episode,
                        onGenerateAiSummary = onGenerateAiSummary,
                        aiSummary = aiSummary,
                        isLoadingAiSummary = isLoadingAiSummary,
                        playbackAnalysis = playbackAnalysis,
                    )
                }
            }

            // 3. Quick Actions
            item(key = "actions") {
                Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    EpisodeActionRow(
                        episode = episode,
                        onFavoriteClick = { onFavoriteClick(episode) },
                        onMarkWatchedClick = { onMarkWatchedClick(episode) },
                        onDownloadClick = { onDownloadClick(episode) },
                        onShareClick = {
                            com.rpeters.jellyfin.ui.utils.ShareUtils.shareMedia(context, episode)
                        },
                    )
                }
            }

            // 4. Season Context / Navigation
            if (seasonEpisodes.isNotEmpty()) {
                item(key = "season_title") {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        Text(
                            text = "More from this Season",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }

                item(key = "season_episodes") {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        PerformanceOptimizedLazyRow(
                            items = seasonEpisodes,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            maxVisibleItems = perfConfig.maxRowItems,
                        ) { item, _, _ ->
                            val isCurrent = item.id == episode.id
                            ImmersiveMediaCard(
                                title = item.name ?: "Episode ${item.indexNumber}",
                                subtitle = "Episode ${item.indexNumber}",
                                imageUrl = getImageUrl(item) ?: "",
                                onCardClick = { if (!isCurrent) onEpisodeClick(item) },
                                cardSize = ImmersiveCardSize.SMALL,
                                isWatched = item.isWatched(),
                                modifier = Modifier.width(200.dp).then(
                                    if (isCurrent) {
                                        Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            // 5. Series Info Card
            seriesInfo?.let { series ->
                item(key = "series_info") {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Series Info",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            SeriesInfoCard(
                                series = series,
                                getImageUrl = getImageUrl,
                                onClick = onViewSeriesClick,
                            )
                        }
                    }
                }
            }
        }

        // Floating Back Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .zIndex(10f),
        ) {
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeHeroContent(
    episode: BaseItemDto,
    seriesInfo: BaseItemDto?,
    onPlayClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ImmersiveDimens.HeroHeightPhone),
    ) {
        // Gradient overlay for text readability
        OverlayGradientScrim(
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Series Title Context
            seriesInfo?.name?.let { seriesName ->
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            // Episode Title
            Text(
                text = episode.name ?: "Unknown Episode",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            // Metadata Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // S# E# Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SeriesBlue.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = "S${episode.parentIndexNumber} E${episode.indexNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Rating
                episode.communityRating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Text(
                            text = String.format(Locale.ROOT, "%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Runtime
                episode.runTimeTicks?.let { ticks ->
                    val minutes = (ticks / 10_000_000 / 60).toInt()
                    Text(
                        text = "${minutes}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            // Play Button
            ExpressiveFilledButton(
                onClick = onPlayClick,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Play Episode")
            }
        }
    }
}

@Composable
private fun EpisodeOverviewSection(
    episode: BaseItemDto,
    onGenerateAiSummary: () -> Unit,
    aiSummary: String?,
    isLoadingAiSummary: Boolean,
    playbackAnalysis: PlaybackCapabilityAnalysis?,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Synopsis Header with AI Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Synopsis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            TextButton(onClick = onGenerateAiSummary, enabled = !isLoadingAiSummary) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (aiSummary != null) "AI Summary" else "Generate AI Summary")
            }
        }

        // Overview Text
        episode.overview?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4,
            )
        }

        // AI Summary Display
        if (aiSummary != null || isLoadingAiSummary) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            ) {
                if (isLoadingAiSummary) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    Text(
                        text = aiSummary ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // Playback Capability
        playbackAnalysis?.let { PlaybackStatusBadge(analysis = it) }
    }
}

@Composable
private fun EpisodeActionRow(
    episode: BaseItemDto,
    onFavoriteClick: () -> Unit,
    onMarkWatchedClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val isFavorite = episode.userData?.isFavorite == true
        val isWatched = episode.userData?.played == true

        DetailActionButton(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = if (isFavorite) "Liked" else "Favorite",
            onClick = onFavoriteClick,
            modifier = Modifier.weight(1f),
            active = isFavorite,
        )
        DetailActionButton(
            icon = if (isWatched) Icons.Default.CheckCircle else Icons.Default.VisibilityOff,
            label = if (isWatched) "Watched" else "Mark",
            onClick = onMarkWatchedClick,
            modifier = Modifier.weight(1f),
            active = isWatched,
        )
        DetailActionButton(
            icon = Icons.Default.Download,
            label = "Download",
            onClick = onDownloadClick,
            modifier = Modifier.weight(1f),
        )
        DetailActionButton(
            icon = Icons.Default.Share,
            label = "Share",
            onClick = onShareClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SeriesInfoCard(
    series: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(width = 60.dp, height = 90.dp),
            ) {
                JellyfinAsyncImage(
                    model = getImageUrl(series),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name ?: "Series",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = series.productionYear?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                series.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
