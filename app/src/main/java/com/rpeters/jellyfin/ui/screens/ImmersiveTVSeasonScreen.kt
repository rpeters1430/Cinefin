@file:OptInAppExperimentalApis
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.rpeters.jellyfin.core.LogCategory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.ui.components.ExpressiveEmptyState
import com.rpeters.jellyfin.ui.components.ExpressiveErrorState
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard
import com.rpeters.jellyfin.ui.components.immersive.ParallaxHeroSection
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonState
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import com.rpeters.jellyfin.utils.getItemKey
import com.rpeters.jellyfin.utils.isCompletelyWatched
import com.rpeters.jellyfin.utils.normalizeOfficialRating
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import java.util.Locale

private enum class ImmersiveSeasonScreenState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}

@Composable
fun ImmersiveTVSeasonScreen(
    seriesId: String,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String? = { null },
    onSeriesClick: (String) -> Unit,
    onEpisodeClick: (BaseItemDto) -> Unit,
    onPlayEpisode: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TVSeasonViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(seriesId) {
        viewModel.loadSeriesData(seriesId)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            Logger.e(LogCategory.UI, "ImmersiveTVSeasonScreen", error)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = when {
                state.isLoading && state.seriesDetails == null -> ImmersiveSeasonScreenState.LOADING
                !state.errorMessage.isNullOrEmpty() -> ImmersiveSeasonScreenState.ERROR
                state.seriesDetails == null -> ImmersiveSeasonScreenState.EMPTY
                else -> ImmersiveSeasonScreenState.CONTENT
            },
            transitionSpec = {
                fadeIn(MotionTokens.expressiveEnter) + slideInVertically { it / 4 } togetherWith
                    fadeOut(MotionTokens.expressiveExit) + slideOutVertically { -it / 4 }
            },
            label = "immersive_season_screen_content",
        ) { screenState ->
            when (screenState) {
                ImmersiveSeasonScreenState.LOADING -> {
                    ExpressiveFullScreenLoading(
                        message = stringResource(id = R.string.loading_tv_show),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ImmersiveSeasonScreenState.ERROR -> {
                    ExpressiveErrorState(
                        title = stringResource(id = R.string.error_loading_tv_show),
                        message = state.errorMessage ?: stringResource(id = R.string.unknown_error),
                        icon = Icons.Default.Tv,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ImmersiveSeasonScreenState.EMPTY -> {
                    ExpressiveEmptyState(
                        icon = Icons.Default.Tv,
                        title = stringResource(id = R.string.no_data_available),
                        subtitle = stringResource(id = R.string.check_connection_and_try_again),
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ImmersiveSeasonScreenState.CONTENT -> {
                    ImmersiveTVSeasonContent(
                        state = state,
                        getImageUrl = getImageUrl,
                        getBackdropUrl = getBackdropUrl,
                        getLogoUrl = getLogoUrl,
                        onSeriesClick = onSeriesClick,
                        onSeasonExpand = viewModel::loadSeasonEpisodes,
                        onEpisodeClick = onEpisodeClick,
                        onPlayEpisode = onPlayEpisode,
                    )
                }
            }
        }

        // Floating Back Button - Overlaid on top
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
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.navigate_up),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }

            // Refresh Button
            Surface(
                onClick = { viewModel.refresh() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                if (state.isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        amplitude = 0.1f,
                        wavelength = 20.dp,
                        waveSpeed = 10.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(id = R.string.refresh),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveTVSeasonContent(
    state: TVSeasonState,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    onSeriesClick: (String) -> Unit,
    onSeasonExpand: (String) -> Unit,
    onEpisodeClick: (BaseItemDto) -> Unit,
    onPlayEpisode: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedSeasonId by rememberSaveable { mutableStateOf<String?>(null) }

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

    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        // Immersive Series Details Header with Parallax
        state.seriesDetails?.let { series ->
            item {
                ImmersiveSeriesDetailsHeader(
                    series = series,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getLogoUrl = getLogoUrl,
                    nextEpisode = state.nextEpisode,
                    onPlayEpisode = onPlayEpisode,
                    scrollOffset = scrollOffset,
                )
            }
        }

        // Seasons Section
        if (state.seasons.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.seasons),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }

            items(
                items = state.seasons,
                key = { it.getItemKey().ifEmpty { it.name ?: it.toString() } },
                contentType = { "season_item" },
            ) { season ->
                val seasonId = season.id.toString()
                val isExpanded = seasonId != null && expandedSeasonId == seasonId
                val seasonEpisodes = seasonId?.let { state.episodesBySeasonId[it].orEmpty() }.orEmpty()
                val isLoadingEpisodes = seasonId != null && seasonId in state.loadingSeasonIds
                val seasonErrorMessage = seasonId?.let { state.seasonEpisodeErrors[it] }

                // Animate chevron rotation
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = MotionTokens.fabMenuExpand,
                    label = "chevron_rotation",
                )

                ImmersiveSeasonListItem(
                    season = season,
                    getImageUrl = getImageUrl,
                    onClick = {
                        if (seasonId != null) {
                            if (isExpanded) {
                                expandedSeasonId = null
                            } else {
                                expandedSeasonId = seasonId
                                onSeasonExpand(seasonId)
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse season" else "Expand season",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(chevronRotation),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                // Expandable episode dropdown
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(
                        animationSpec = tween(
                            durationMillis = MotionTokens.DurationMedium1,
                            easing = MotionTokens.EmphasizedEasing,
                        ),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(MotionTokens.expressiveEnter),
                    exit = shrinkVertically(
                        animationSpec = tween(
                            durationMillis = MotionTokens.DurationShort4,
                            easing = MotionTokens.AccelerateEasing,
                        ),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(MotionTokens.expressiveExit),
                ) {
                    ImmersiveSeasonEpisodeDropdown(
                        episodes = seasonEpisodes,
                        isLoading = isLoadingEpisodes,
                        errorMessage = seasonErrorMessage,
                        getImageUrl = getImageUrl,
                        onEpisodeClick = onEpisodeClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.no_seasons_available),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Cast and Crew Section
        state.seriesDetails?.people?.takeIf { it.isNotEmpty() }?.let { people ->
            val cast = people.filter { person ->
                when (person.type.toString().lowercase()) {
                    "actor", "gueststar", "guest star" -> true
                    else -> false
                }
            }
            val crew = people.filter { person ->
                when (person.type.toString().lowercase()) {
                    "director", "producer", "writer", "executiveproducer", "executive producer", "creator" -> true
                    else -> false
                }
            }

            if (cast.isNotEmpty() || crew.isNotEmpty()) {
                item {
                    ImmersiveCastAndCrewSection(
                        cast = cast,
                        crew = crew,
                        getImageUrl = { id, tag ->
                            val personItem = BaseItemDto(
                                id = id,
                                type = org.jellyfin.sdk.model.api.BaseItemKind.PERSON,
                                imageTags = tag?.let { mapOf(org.jellyfin.sdk.model.api.ImageType.PRIMARY to it) },
                            )
                            getImageUrl(personItem)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }

        // More Like This Section
        if (state.isSimilarSeriesLoading || state.similarSeries.isNotEmpty()) {
            item {
                ImmersiveMoreLikeThisSection(
                    items = state.similarSeries,
                    isLoading = state.isSimilarSeriesLoading,
                    getImageUrl = getImageUrl,
                    onSeriesClick = onSeriesClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * Helper function to determine the watch button text based on series watch status
 */
private fun getWatchButtonText(series: BaseItemDto, nextEpisode: BaseItemDto?): String {
    if (series.isCompletelyWatched() && nextEpisode != null) {
        return "Rewatch Series"
    }

    if (nextEpisode == null) {
        return "Browse Series"
    }

    val playedPercentage = series.userData?.playedPercentage ?: 0.0
    val isFirstEpisode = nextEpisode.parentIndexNumber == 1 && nextEpisode.indexNumber == 1
    val hasStartedWatching = playedPercentage > 0.0 || !isFirstEpisode

    if (!hasStartedWatching) {
        return "Start Watching Series"
    }

    return if (nextEpisode.indexNumber != null) {
        "Watch Episode ${nextEpisode.indexNumber} Next"
    } else {
        "Continue Watching Series"
    }
}

@Composable
private fun ImmersiveSeriesDetailsHeader(
    series: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    nextEpisode: BaseItemDto?,
    onPlayEpisode: (BaseItemDto) -> Unit,
    scrollOffset: Float,
    modifier: Modifier = Modifier,
) {
    val heroImage = getBackdropUrl(series).takeIf { !it.isNullOrBlank() } ?: getImageUrl(series).orEmpty()

    // Parallax Hero Section with Overlaid Title/Metadata
    ParallaxHeroSection(
        imageUrl = heroImage.takeIf { it.isNotBlank() },
        scrollOffset = scrollOffset,
        height = ImmersiveDimens.HeroHeightPhone,
        parallaxFactor = 0.5f,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    ) {
        // Title and metadata overlaid on hero with gradient
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title
            Text(
                text = series.name ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Metadata Row (Rating, Year, Episode Count)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Rating with star
                series.communityRating?.let { rating ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700), // Gold color
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Official Rating
                series.officialRating?.let { rating ->
                    val normalizedRating = normalizeOfficialRating(rating) ?: return@let
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
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

                // Year Range
                series.productionYear?.let { year ->
                    val endYear = series.endDate?.year
                    val yearText = if (endYear != null && endYear != year) {
                        "$year - $endYear"
                    } else if (series.status == "Continuing") {
                        "$year - Present"
                    } else {
                        year.toString()
                    }
                    Text(
                        text = yearText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }

                // Episode count
                series.childCount?.let { count ->
                    Text(
                        text = "$count episodes",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }

    // Overview and Watch Button (Below Hero)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Overview
        series.overview?.let { overview ->
            if (overview.isNotBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3,
                )
            }
        }

        // Watch Next Episode Button
        ExpressiveFilledButton(
            onClick = {
                nextEpisode?.let { onPlayEpisode(it) }
            },
            enabled = nextEpisode != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = getWatchButtonText(series, nextEpisode))
        }
    }
}

@Composable
private fun ImmersiveSeasonListItem(
    season: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit,
    trailingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Season poster (smaller in immersive)
            val posterUrl = getImageUrl(season)
            Surface(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(width = 80.dp, height = 120.dp),
            ) {
                if (posterUrl != null) {
                    JellyfinAsyncImage(
                        model = posterUrl,
                        contentDescription = season.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            // Season info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = season.name ?: "Season",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                season.childCount?.let { count ->
                    Text(
                        text = "$count episodes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                season.userData?.let { userData ->
                    val unwatchedCount = userData.unplayedItemCount ?: 0
                    if (unwatchedCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text("$unwatchedCount unwatched")
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Watched",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            // Trailing chevron
            trailingContent()
        }
    }
}

@Composable
private fun ImmersiveSeasonEpisodeDropdown(
    episodes: List<BaseItemDto>,
    isLoading: Boolean,
    errorMessage: String?,
    getImageUrl: (BaseItemDto) -> String?,
    onEpisodeClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            isLoading -> {
                repeat(3) {
                    ExpressiveLoadingCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    )
                }
            }
            !errorMessage.isNullOrEmpty() -> {
                Text(
                    text = "Error: $errorMessage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
            episodes.isEmpty() -> {
                Text(
                    text = "No episodes available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                episodes.forEach { episode ->
                    ImmersiveEpisodeCard(
                        episode = episode,
                        getImageUrl = getImageUrl,
                        onClick = { onEpisodeClick(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveEpisodeCard(
    episode: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Episode thumbnail (16:9 aspect ratio)
            val thumbnailUrl = getImageUrl(episode)
            Surface(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(width = 140.dp, height = 79.dp),
            ) {
                if (thumbnailUrl != null) {
                    Box {
                        JellyfinAsyncImage(
                            model = thumbnailUrl,
                            contentDescription = episode.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Watch progress indicator
                        episode.userData?.playedPercentage?.let { progress ->
                            if (progress > 0.0 && progress < 100.0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((progress / 100.0).toFloat())
                                            .height(4.dp)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            } else if (progress >= 100.0) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Watched",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(20.dp)
                                        .background(
                                            MaterialTheme.colorScheme.tertiary,
                                            CircleShape,
                                        )
                                        .padding(2.dp),
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            // Episode info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Episode number
                episode.indexNumber?.let { episodeNum ->
                    Text(
                        text = "Episode $episodeNum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                // Episode title
                Text(
                    text = episode.name ?: "Unknown Episode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Overview
                episode.overview?.let { overview ->
                    if (overview.isNotBlank()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Runtime
                episode.runTimeTicks?.let { ticks ->
                    val minutes = (ticks / 10_000_000 / 60).toInt()
                    Text(
                        text = "${minutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveCastAndCrewSection(
    cast: List<BaseItemPerson>,
    crew: List<BaseItemPerson>,
    getImageUrl: (uuid: java.util.UUID, tag: String?) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Cast & Crew",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        // Cast
        if (cast.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
            ) {
                items(cast.take(15)) { person ->
                    ImmersiveCastMemberCard(
                        person = person,
                        imageUrl = person.id.let { id ->
                            getImageUrl(id, person.primaryImageTag)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImmersiveCastMemberCard(
    person: BaseItemPerson,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(120.dp),
        ) {
            if (imageUrl != null) {
                JellyfinAsyncImage(
                    model = imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        Text(
            text = person.name ?: "Unknown",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        person.role?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ImmersiveMoreLikeThisSection(
    items: List<BaseItemDto>,
    isLoading: Boolean,
    getImageUrl: (BaseItemDto) -> String?,
    onSeriesClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "More Like This",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        if (isLoading) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
            ) {
                items(5) {
                    ExpressiveLoadingCard(
                        modifier = Modifier.size(width = 200.dp, height = 300.dp),
                    )
                }
            }
        } else if (items.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
            ) {
                items(items.take(10), key = { it.id.toString() }) { series ->
                    ImmersiveMediaCard(
                        title = series.name ?: "Unknown",
                        subtitle = series.productionYear?.toString() ?: "",
                        imageUrl = getImageUrl(series) ?: "",
                        rating = series.communityRating,
                        onCardClick = {
                            onSeriesClick(series.id.toString())
                        },
                        cardSize = ImmersiveCardSize.SMALL,
                    )
                }
            }
        }
    }
}
