package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.*
import com.rpeters.jellyfin.ui.components.immersive.*
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonState
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import com.rpeters.jellyfin.utils.getItemKey
import com.rpeters.jellyfin.utils.isCompletelyWatched
import com.rpeters.jellyfin.utils.normalizeOfficialRating
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import java.util.Locale

private enum class ImmersiveShowDetailState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}

/**
 * Immersive TV Show/Series Detail screen.
 * High-end cinematic view for series overview, seasons, and discovery.
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveTVShowDetailScreen(
    seriesId: String,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String? = { null },
    onSeriesClick: (String) -> Unit,
    onEpisodeClick: (BaseItemDto) -> Unit,
    onPlayEpisode: (BaseItemDto) -> Unit,
    onPersonClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: TVSeasonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    LaunchedEffect(seriesId) {
        viewModel.loadSeriesData(seriesId)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = when {
                state.isLoading && state.seriesDetails == null -> ImmersiveShowDetailState.LOADING
                !state.errorMessage.isNullOrEmpty() -> ImmersiveShowDetailState.ERROR
                state.seriesDetails == null -> ImmersiveShowDetailState.EMPTY
                else -> ImmersiveShowDetailState.CONTENT
            },
            transitionSpec = {
                fadeIn(MotionTokens.expressiveEnter) togetherWith fadeOut(MotionTokens.expressiveExit)
            },
            label = "show_detail_content",
        ) { screenState ->
            when (screenState) {
                ImmersiveShowDetailState.LOADING -> {
                    ExpressiveFullScreenLoading(
                        message = stringResource(id = R.string.loading_tv_show),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ImmersiveShowDetailState.ERROR -> {
                    ExpressiveErrorState(
                        title = stringResource(id = R.string.error_loading_tv_show),
                        message = state.errorMessage ?: stringResource(id = R.string.unknown_error),
                        icon = Icons.Default.Tv,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ImmersiveShowDetailState.EMPTY -> {
                    ExpressiveEmptyState(
                        icon = Icons.Default.Tv,
                        title = stringResource(id = R.string.no_data_available),
                        subtitle = stringResource(id = R.string.check_connection_and_try_again),
                        iconTint = SeriesBlue,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ImmersiveShowDetailState.CONTENT -> {
                    ImmersiveShowDetailContent(
                        state = state,
                        getImageUrl = getImageUrl,
                        getBackdropUrl = getBackdropUrl,
                        getLogoUrl = getLogoUrl,
                        onSeriesClick = onSeriesClick,
                        onSeasonExpand = viewModel::loadSeasonEpisodes,
                        onEpisodeClick = onEpisodeClick,
                        onPlayEpisode = onPlayEpisode,
                        onRefresh = { viewModel.refresh() },
                        onPersonClick = onPersonClick,
                    )
                }
            }
        }

        // Floating Header Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
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

@OptInAppExperimentalApis
@Composable
private fun ImmersiveShowDetailContent(
    state: TVSeasonState,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    onSeriesClick: (String) -> Unit,
    onSeasonExpand: (String) -> Unit,
    onEpisodeClick: (BaseItemDto) -> Unit,
    onPlayEpisode: (BaseItemDto) -> Unit,
    onRefresh: () -> Unit,
    onPersonClick: (String, String) -> Unit,
) {
    val perfConfig = rememberImmersivePerformanceConfig()
    var expandedSeasonId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = remember(state.seriesDetails?.id?.toString()) { LazyListState() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Static Hero Background (Fixed)
        state.seriesDetails?.let { series ->
            StaticHeroSection(
                imageUrl = getBackdropUrl(series),
                height = ImmersiveDimens.HeroHeightPhone,
                content = {}, // Content moved to LazyColumn
            )
        }

        // 2. Scrollable Layer
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = 40.dp,
            ),
        ) {
            // Hero Content (Logo, Title, Buttons) - Now scrolls
            item(key = "hero_content") {
                state.seriesDetails?.let { series ->
                    ShowHeroContent(
                        series = series,
                        getLogoUrl = getLogoUrl,
                        nextEpisode = state.nextEpisode,
                        onPlayEpisode = onPlayEpisode,
                    )
                }
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
            // 1. Overview & Metadata (now first scrollable item)
            state.seriesDetails?.let { series ->
                item {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        ShowMetadataSection(series = series)
                    }
                }
            }

            // 3. Seasons & Episodes
            if (state.seasons.isNotEmpty()) {
                item {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }

                items(state.seasons, key = { it.getItemKey() }) { season ->
                    val seasonId = season.id.toString()
                    val isExpanded = seasonId != null && expandedSeasonId == seasonId

                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        SeasonItem(
                            season = season,
                            isExpanded = isExpanded,
                            episodes = seasonId?.let { state.episodesBySeasonId[it] }.orEmpty(),
                            isLoadingEpisodes = seasonId != null && seasonId in state.loadingSeasonIds,
                            getImageUrl = getImageUrl,
                            onExpand = {
                                if (seasonId != null) {
                                    expandedSeasonId = if (isExpanded) null else seasonId
                                    if (!isExpanded) onSeasonExpand(seasonId)
                                }
                            },
                            onEpisodeClick = onEpisodeClick,
                        )
                    }
                }
            }

            // 4. Cast & Crew
            state.seriesDetails?.people?.takeIf { it.isNotEmpty() }?.let { people ->
                item {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        ImmersiveCastSection(
                            people = people,
                            getImageUrl = getImageUrl,
                            onPersonClick = onPersonClick,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }

            // 5. Similar Shows (aligned with Movies implementation)
            if (state.similarSeries.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "More Like This",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )

                        PerformanceOptimizedLazyRow(
                            items = state.similarSeries,
                            horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                            maxVisibleItems = perfConfig.maxRowItems,
                        ) { similarShow, _, _ ->
                            ImmersiveMediaCard(
                                title = similarShow.name ?: "Unknown",
                                subtitle = buildYearRangeText(
                                    startYear = similarShow.productionYear,
                                    endYear = similarShow.endDate?.year,
                                    status = similarShow.status,
                                ),
                                imageUrl = getImageUrl(similarShow) ?: "",
                                rating = similarShow.communityRating,
                                onCardClick = {
                                    onSeriesClick(similarShow.id.toString())
                                },
                                cardSize = ImmersiveCardSize.SMALL,
                            )
                        }
                    }
                }
            }
        } // End LazyColumn
    } // End Box
}

@Composable
private fun ShowHeroContent(
    series: BaseItemDto,
    getLogoUrl: (BaseItemDto) -> String?,
    nextEpisode: BaseItemDto?,
    onPlayEpisode: (BaseItemDto) -> Unit,
) {
    val logoUrl = getLogoUrl(series)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ImmersiveDimens.HeroHeightPhone)
            .padding(16.dp)
            .padding(bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo or Title
            if (!logoUrl.isNullOrBlank()) {
                JellyfinAsyncImage(
                    model = logoUrl,
                    contentDescription = series.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(120.dp).fillMaxWidth(0.8f),
                )
            } else {
                Text(
                    text = series.name ?: "Unknown",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            // Quick Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Rating
                series.communityRating?.let { rating ->
                    RatingBadge(rating)
                }

                // Year Range (e.g., "2020-2024" or "2020-Present")
                val yearText = buildYearRangeText(
                    startYear = series.productionYear,
                    endYear = series.endDate?.year,
                    status = series.status,
                )
                if (yearText.isNotEmpty()) {
                    Text(
                        text = yearText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }

                // Season Count
                series.childCount?.let {
                    Text(
                        text = "$it Seasons",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            // Watch Button
            ExpressiveFilledButton(
                onClick = { nextEpisode?.let { onPlayEpisode(it) } },
                enabled = nextEpisode != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (series.isCompletelyWatched()) "Rewatch" else "Watch Next")
            }
        }
    }
}

@Composable
private fun ShowMetadataSection(series: BaseItemDto) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Official Rating & Status (larger, centered)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            series.officialRating?.let { rating ->
                normalizeOfficialRating(rating)?.let { normalized ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = normalized,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (series.status == "Continuing") {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF4CAF50),
                ) {
                    Text(
                        text = "Ongoing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Overview (centered, 3 lines)
        series.overview?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4,
                textAlign = TextAlign.Center,
            )
        }

        // Genres (FlowRow with buttons, no horizontal scroll)
        series.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                genres.forEach { genre ->
                    SuggestionChip(
                        onClick = { /* TODO: Filter by genre */ },
                        label = {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = SeriesBlue.copy(alpha = 0.15f),
                            labelColor = SeriesBlue,
                        ),
                        border = BorderStroke(1.dp, SeriesBlue.copy(alpha = 0.3f)), // ✅ Fixed: Use BorderStroke directly
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonItem(
    season: BaseItemDto,
    isExpanded: Boolean,
    episodes: List<BaseItemDto>,
    isLoadingEpisodes: Boolean,
    getImageUrl: (BaseItemDto) -> String?,
    onExpand: () -> Unit,
    onEpisodeClick: (BaseItemDto) -> Unit,
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Surface(
            onClick = onExpand,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Season Poster
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 60.dp, height = 90.dp),
                ) {
                    JellyfinAsyncImage(
                        model = getImageUrl(season),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = season.name ?: "Season",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    // ✅ Fixed: Only show episode count when we have reliable data
                    // If episodes are loaded, use that count. Otherwise try metadata.
                    // Don't show "0 Episodes" if we simply haven't loaded yet.
                    val episodeCount = if (episodes.isNotEmpty()) {
                        episodes.size
                    } else {
                        season.childCount?.takeIf { it > 0 }
                    }

                    episodeCount?.let { count ->
                        Text(
                            text = "$count Episode${if (count != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isLoadingEpisodes) {
                    repeat(2) { ExpressiveLoadingCard(modifier = Modifier.fillMaxWidth().height(80.dp)) }
                } else {
                    episodes.forEach { episode ->
                        EpisodeRow(episode = episode, getImageUrl = getImageUrl, onClick = { onEpisodeClick(episode) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Surface(
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(width = 100.dp, height = 56.dp),
            ) {
                JellyfinAsyncImage(
                    model = getImageUrl(episode),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.indexNumber}. ${episode.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episode.overview ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RatingBadge(rating: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
        Text(
            text = String.format(Locale.ROOT, "%.1f", rating),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun ImmersiveCastSection(
    people: List<BaseItemPerson>,
    getImageUrl: (BaseItemDto) -> String?,
    onPersonClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perfConfig = rememberImmersivePerformanceConfig()
    val cast = people.filter { it.type.toString().lowercase() in listOf("actor", "gueststar") }.take(perfConfig.maxRowItems)
    if (cast.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cast) { person ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable {
                            person.id?.let { id ->
                                onPersonClick(id.toString(), person.name ?: "Unknown")
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.size(120.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        val personItem = BaseItemDto(
                            id = person.id,
                            type = org.jellyfin.sdk.model.api.BaseItemKind.PERSON,
                            imageTags = person.primaryImageTag?.let { mapOf(org.jellyfin.sdk.model.api.ImageType.PRIMARY to it) },
                        )
                        JellyfinAsyncImage(
                            model = getImageUrl(personItem),
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = person.name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Builds year range text for TV shows.
 * Examples: "2024", "2020-2024", "2020-Present"
 */
private fun buildYearRangeText(startYear: Int?, endYear: Int?, status: String?): String {
    if (startYear == null) return ""

    return when {
        // Ongoing show (no end year)
        status == "Continuing" -> "$startYear-Present"
        // Ended show with different end year
        endYear != null && endYear != startYear -> "$startYear-$endYear"
        // Single year or same start/end
        else -> startYear.toString()
    }
}
