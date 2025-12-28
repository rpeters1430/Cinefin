@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.LogCategory
import com.rpeters.jellyfin.core.Logger
import com.rpeters.jellyfin.ui.components.ExpressiveFullScreenLoading
import com.rpeters.jellyfin.ui.components.ExpressiveLoadingCard
import com.rpeters.jellyfin.ui.components.PosterMediaCard
import com.rpeters.jellyfin.ui.theme.MotionTokens
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonState
import com.rpeters.jellyfin.ui.viewmodel.TVSeasonViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import kotlin.math.roundToInt

@Composable
fun TVSeasonScreen(
    seriesId: String,
    onBackClick: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onSeasonClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
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
                        message = "Loading TV Show...",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SeasonScreenState.ERROR -> {
                    ExpressiveErrorState(
                        message = state.errorMessage ?: stringResource(id = R.string.unknown_error),
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SeasonScreenState.EMPTY -> {
                    ExpressiveEmptyState(
                        icon = Icons.Default.Tv,
                        title = stringResource(id = R.string.no_data_available),
                        subtitle = "Please check your connection and try again",
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SeasonScreenState.CONTENT -> {
                    TVSeasonContent(
                        state = state,
                        getImageUrl = getImageUrl,
                        getBackdropUrl = getBackdropUrl,
                        onSeasonClick = onSeasonClick,
                        onSeriesClick = onSeriesClick,
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
                color = Color.Black.copy(alpha = 0.5f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.navigate_up),
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }

            // Refresh Button
            Surface(
                onClick = { viewModel.refresh() },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(id = R.string.refresh),
                        tint = Color.White,
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
    onSeasonClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
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
                )
            }
        }

        // Seasons Section
        if (state.seasons.isNotEmpty()) {
            item {
                Text(
                    text = "Seasons",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            items(state.seasons, key = { it.id?.hashCode() ?: it.name.hashCode() }) { season ->
                ExpressiveSeasonCard(
                    season = season,
                    getImageUrl = getImageUrl,
                    onClick = { onSeasonClick(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                        text = "No seasons available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Cast and Crew Section (if available)
        state.seriesDetails?.people?.takeIf { it.isNotEmpty() }?.let { people ->
            val cast = people.filter { it.type?.name == "Actor" }
            val crew = people.filter {
                val typeName = it.type?.name
                typeName in listOf("Director", "Producer", "Writer", "Executive Producer")
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

@Composable
private fun SeriesDetailsHeader(
    series: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
) {
    // Full-bleed hero section - Google TV style
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp),
    ) {
        // Background Image - Full bleed
        SubcomposeAsyncImage(
            model = getBackdropUrl(series).takeIf { !it.isNullOrBlank() } ?: getImageUrl(series),
            contentDescription = series.name,
            loading = {
                ExpressiveLoadingCard(
                    modifier = Modifier.fillMaxSize(),
                    showTitle = false,
                    showSubtitle = false,
                    imageHeight = 500.dp,
                )
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp),
                    )
                }
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Gradient Scrim - Darker at bottom for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.95f),
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
                text = series.name ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            tint = Color(0xFFFFD700), // Gold color
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "${(rating * 10).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Official Rating Badge (if available)
                series.officialRating?.let { rating ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
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

            // Overview
            series.overview?.let { overview ->
                if (overview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveSeasonCard(
    season: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "season_card_scale",
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable {
                season.id?.let { seasonId ->
                    onClick(seasonId.toString())
                }
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Season Poster with enhanced styling
            Box {
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
                                    text = season.name ?: "Season",
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

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    // Favorite indicator
                    if (season.userData?.isFavorite == true) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = Color.Yellow,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(4.dp),
                            )
                        }
                    }

                    val unplayedCount = season.userData?.unplayedItemCount ?: 0
                    val played = season.userData?.played == true

                    when {
                        unplayedCount > 0 -> {
                            Badge(
                                modifier = Modifier.align(Alignment.TopEnd),
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
                                modifier = Modifier.align(Alignment.TopEnd),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Season watched",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Season Details with enhanced typography
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = buildString {
                        val seasonName = season.name ?: stringResource(R.string.unknown)
                        append(seasonName)

                        // Add episode count in parentheses for more compact display
                        season.childCount?.let { count ->
                            // Extract season number from name if possible, otherwise use full name
                            val seasonDisplay = if (seasonName.startsWith("Season ", ignoreCase = true)) {
                                "S${seasonName.substring(7)}"
                            } else {
                                seasonName
                            }
                            // Replace the basic name with enhanced format
                            clear()
                            append("$seasonDisplay ($count)")
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    season.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Rating if available
                season.communityRating?.let { rating ->
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
                        )
                    }
                }

                season.overview?.let { overview ->
                    if (overview.isNotBlank()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
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
            text = "Error",
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

        androidx.compose.material3.Button(
            onClick = onRetry,
        ) {
            Text("Retry")
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
                    items(cast.take(12), key = { it.id?.hashCode() ?: it.name.hashCode() }) { person ->
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
                    items(crew.take(12), key = { it.id?.hashCode() ?: it.name.hashCode() }) { person ->
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
                items(5) { index ->
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
                items(items, key = { it.id?.hashCode() ?: it.name.hashCode() }) { show ->
                    PosterMediaCard(
                        item = show,
                        getImageUrl = getImageUrl,
                        onClick = { show.id?.let { onSeriesClick(it.toString()) } },
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
    Card(
        modifier = modifier.width(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Actor photo or initials
            if (person.primaryImageTag != null) {
                AsyncImage(
                    model = getImageUrl(person.id, person.primaryImageTag),
                    contentDescription = person.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = person.name?.take(2)?.uppercase() ?: "??",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = person.name ?: stringResource(id = R.string.unknown),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // Show role for actors or type for crew - more compact display
            val displayText = when {
                !person.role.isNullOrBlank() -> {
                    // Truncate long character names with ellipsis for better fit
                    val role = person.role!!
                    if (role.length > 20) "${role.take(17)}..." else role
                }
                person.type?.name?.isNotBlank() == true -> person.type.name
                else -> null
            }

            displayText?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

// Expressive Error State component
@Composable
private fun ExpressiveErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = "Error Loading TV Show",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

// Expressive Empty State component
@Composable
private fun ExpressiveEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            val scale by animateFloatAsState(
                targetValue = 1.0f,
                animationSpec = MotionTokens.expressiveEnter,
                label = "empty_icon_scale",
            )

            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .padding(24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    tint = iconTint,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
