@file:OptInAppExperimentalApis
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.LogCategory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.ui.components.ExpressiveEmptyState
import com.rpeters.jellyfin.ui.components.ExpressiveErrorState
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.ExpressiveMediaListItem
import com.rpeters.jellyfin.ui.components.HeroImageWithLogo
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonState
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import com.rpeters.jellyfin.utils.getUnwatchedEpisodeCount
import com.rpeters.jellyfin.utils.getItemKey
import com.rpeters.jellyfin.utils.isCompletelyWatched
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import java.util.Locale

@Composable
fun TVSeasonScreen(
    seriesId: String,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String? = { null },
    onSeasonClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
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
            // For now, we'll just log the error
            // In a production app, you might want to show a Snackbar or other UI feedback
            Logger.e(LogCategory.UI, "TVSeasonScreen", error)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = when {
                state.isLoading && state.seriesDetails == null -> SeasonScreenState.LOADING
                !state.errorMessage.isNullOrEmpty() -> SeasonScreenState.ERROR
                state.seriesDetails == null -> SeasonScreenState.EMPTY
                else -> SeasonScreenState.CONTENT
            },
            transitionSpec = {
                fadeIn(MotionTokens.expressiveEnter) + slideInVertically { it / 4 } togetherWith
                    fadeOut(MotionTokens.expressiveExit) + slideOutVertically { -it / 4 }
            },
            label = "season_screen_content",
        ) { screenState ->
            when (screenState) {
                SeasonScreenState.LOADING -> {
                    ExpressiveFullScreenLoading(
                        message = stringResource(id = R.string.loading_tv_show),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SeasonScreenState.ERROR -> {
                    ExpressiveErrorState(
                        title = stringResource(id = R.string.error_loading_tv_show),
                        message = state.errorMessage ?: stringResource(id = R.string.unknown_error),
                        icon = Icons.Default.Tv,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SeasonScreenState.EMPTY -> {
                    ExpressiveEmptyState(
                        icon = Icons.Default.Tv,
                        title = stringResource(id = R.string.no_data_available),
                        subtitle = stringResource(id = R.string.check_connection_and_try_again),
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SeasonScreenState.CONTENT -> {
                    TVSeasonContent(
                        state = state,
                        getImageUrl = getImageUrl,
                        getBackdropUrl = getBackdropUrl,
                        getLogoUrl = getLogoUrl,
                        onSeasonClick = onSeasonClick,
                        onSeriesClick = onSeriesClick,
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
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
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
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
            ) {
                if (state.isLoading) {
                    // Expressive wavy loading indicator
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
private fun TVSeasonContent(
    state: TVSeasonState,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    onSeasonClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onPlayEpisode: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
    ) {
        // Series Details Header - Full bleed, no padding
        state.seriesDetails?.let { series ->
            item {
                SeriesDetailsHeader(
                    series = series,
                    getImageUrl = getImageUrl,
                    getBackdropUrl = getBackdropUrl,
                    getLogoUrl = getLogoUrl,
                    nextEpisode = state.nextEpisode,
                    onPlayEpisode = onPlayEpisode,
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            items(
                items = state.seasons,
                key = { it.getItemKey().ifEmpty { it.name ?: it.toString() } },
                contentType = { "season_item" },
            ) { season ->
                ExpressiveSeasonListItem(
                    season = season,
                    getImageUrl = getImageUrl,
                    onClick = { onSeasonClick(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
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

        // Cast and Crew Section (if available)
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

            // Only show the section if we have cast or crew after filtering
            if (cast.isNotEmpty() || crew.isNotEmpty()) {
                item {
                    CastAndCrewSection(
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
        }

        // Show "More Like This" section if loading or has data
        if (state.isSimilarSeriesLoading || state.similarSeries.isNotEmpty()) {
            item {
                MoreLikeThisSection(
                    items = state.similarSeries,
                    isLoading = state.isSimilarSeriesLoading,
                    getImageUrl = getImageUrl,
                    onSeriesClick = onSeriesClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
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
    val totalCount = series.childCount ?: 0
    val playedPercentage = series.userData?.playedPercentage ?: 0.0
    val unwatchedCount = series.getUnwatchedEpisodeCount()

    if (totalCount == 0) {
        return "Browse Series"
    }

    if (series.isCompletelyWatched()) {
        return "Rewatch Series"
    }

    val hasNotStarted = unwatchedCount == totalCount && playedPercentage == 0.0
    if (hasNotStarted) {
        return "Start Watching Series"
    }

    val nextEpisodeNumber = nextEpisode?.indexNumber
        ?: (totalCount - unwatchedCount + 1).coerceAtLeast(1)
    return "Watch Episode $nextEpisodeNumber Next"
}

@Composable
private fun SeriesDetailsHeader(
    series: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    nextEpisode: BaseItemDto?,
    onPlayEpisode: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heroImage = getBackdropUrl(series).takeIf { !it.isNullOrBlank() } ?: getImageUrl(series).orEmpty()

    if (heroImage.isNotBlank()) {
        // Full-bleed Hero Section - Extended to screen edges
        HeroImageWithLogo(
            imageUrl = heroImage,
            logoUrl = getLogoUrl(series),
            contentDescription = "${series.name} backdrop",
            logoContentDescription = "${series.name} logo",
            modifier = modifier,
            minHeight = 400.dp,
            aspectRatio = 1.0f,
            loadingContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            },
            errorContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp),
                    )
                }
            },
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
        }
    }

    // Title and Metadata Section (Below backdrop)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = series.name ?: stringResource(R.string.unknown),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        // Metadata Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rating with star icon
            series.communityRating?.let { rating ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = String.format(Locale.ROOT, "%.1f★", rating),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // Official Rating Badge (if available)
            series.officialRating?.let { rating ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Year or Year Range
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
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }

            // Episode count
            series.childCount?.let { count ->
                Text(
                    text = "$count episodes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
        }

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
        Spacer(modifier = Modifier.height(16.dp))
        ExpressiveFilledButton(
            onClick = {
                nextEpisode?.let { onPlayEpisode(it) }
            },
            enabled = nextEpisode != null && !series.isCompletelyWatched(),
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
private fun ExpressiveSeasonListItem(
    season: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seasonName = season.name ?: stringResource(R.string.unknown)
    val seasonLabel = if (seasonName.startsWith("Season ", ignoreCase = true)) {
        "S${seasonName.substring(7)}"
    } else {
        seasonName
    }
    val overline = buildString {
        append(seasonLabel)
        season.childCount?.let { count ->
            append(" • $count episodes")
        }
    }

    ExpressiveMediaListItem(
        title = seasonName,
        subtitle = season.overview?.takeIf { it.isNotBlank() },
        overline = overline,
        leadingContent = {
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(130.dp),
            ) {
                SubcomposeAsyncImage(
                    model = getImageUrl(season),
                    contentDescription = season.name,
                    loading = {
                        ExpressiveLoadingCard(
                            modifier = Modifier
                                .width(90.dp)
                                .height(130.dp),
                            showTitle = false,
                            showSubtitle = false,
                            imageHeight = 130.dp,
                        )
                    },
                    error = {
                        Surface(
                            modifier = Modifier
                                .width(90.dp)
                                .height(130.dp),
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
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = seasonLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(90.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                val unplayedCount = season.userData?.unplayedItemCount ?: 0
                val played = season.userData?.played == true

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        if (season.userData?.isFavorite == true) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = stringResource(id = R.string.favorites),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(4.dp),
                                )
                            }
                        }

                        when {
                            unplayedCount > 0 -> {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    val countText = when {
                                        unplayedCount > 99 -> "99+"
                                        else -> unplayedCount.toString()
                                    }
                                    Text(
                                        text = countText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                            unplayedCount == 0 && played -> {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(id = R.string.season_watched),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        trailingContent = {
            season.communityRating?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(id = R.string.rating),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = String.format(Locale.ROOT, "%.1f", rating),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = { onClick(season.id.toString()) },
        modifier = modifier,
    )
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(id = R.string.error),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExpressiveFilledButton(
            onClick = onRetry,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.retry))
        }
    }
}

@Composable
private fun CastAndCrewSection(
    cast: List<BaseItemPerson>,
    crew: List<BaseItemPerson>,
    getImageUrl: (java.util.UUID, String?) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Cast & Crew",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (cast.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Cast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(
                        items = cast.take(12),
                        key = { it.id.toString() },
                        contentType = { "cast_member" },
                    ) { person ->
                        PersonCard(
                            person = person,
                            getImageUrl = getImageUrl,
                        )
                    }
                }
            }
        }

        if (crew.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Crew",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    items(
                        items = crew.take(12),
                        key = { it.id.toString() },
                        contentType = { "crew_member" },
                    ) { person ->
                        PersonCard(
                            person = person,
                            getImageUrl = getImageUrl,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreLikeThisSection(
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
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (isLoading) {
            // Show loading skeleton cards
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(
                    count = 5,
                    key = { it },
                    contentType = { "more_like_this_loading" },
                ) {
                    ExpressiveLoadingCard(
                        modifier = Modifier.width(140.dp),
                        imageHeight = 210.dp,
                        showTitle = true,
                        showSubtitle = false,
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(
                    items = items,
                    key = { it.getItemKey().ifEmpty { it.name ?: it.toString() } },
                    contentType = { "more_like_this_item" },
                ) { show ->
                    PosterMediaCard(
                        item = show,
                        getImageUrl = getImageUrl,
                        onClick = { onSeriesClick(show.id.toString()) },
                        showMetadata = false,
                        cardWidth = 140.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: BaseItemPerson,
    getImageUrl: (java.util.UUID, String?) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Profile Image with background
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(100.dp),
        ) {
            if (person.primaryImageTag != null) {
                JellyfinAsyncImage(
                    model = getImageUrl(person.id, person.primaryImageTag),
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    requestSize = rememberCoilSize(100.dp),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = person.name?.take(2)?.uppercase() ?: "??",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Actor Name
        Text(
            text = person.name ?: stringResource(id = R.string.unknown),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        // Role/Character - with fallback to type for crew
        val roleText = person.role ?: person.type?.name?.takeIf { it.isNotBlank() }
        roleText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Content state enum for animated transitions
enum class SeasonScreenState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT,
}
