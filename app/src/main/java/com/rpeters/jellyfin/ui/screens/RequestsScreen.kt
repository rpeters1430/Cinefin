@file:OptIn(ExperimentalLayoutApi::class)

package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveTextButton
import com.rpeters.jellyfin.ui.components.ExpressiveTonalButton
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ShimmerBox
import com.rpeters.jellyfin.ui.components.expressiveGlow
import com.rpeters.jellyfin.ui.theme.JellyfinExpressiveTheme
import com.rpeters.jellyfin.ui.viewmodel.PendingMovieRequest
import com.rpeters.jellyfin.ui.viewmodel.PendingTvRequest
import com.rpeters.jellyfin.ui.viewmodel.RequestsViewModel
import com.rpeters.jellyfin.ui.viewmodel.TvAvailability
import com.rpeters.jellyfin.ui.viewmodel.TvEpisodeAvailability
import com.rpeters.jellyfin.ui.viewmodel.TvSeasonAvailability

private enum class SeasonRequestMode { LATEST, ALL, CUSTOM }

@OptInAppExperimentalApis
@Composable
fun RequestsScreen(
    initialQuery: String? = null,
    onNavigateToSettings: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && initialQuery != uiState.query) {
            viewModel.onQueryChange(initialQuery)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    uiState.pendingMovieRequest?.let { pending ->
        MovieQualityDialog(
            pending = pending,
            onConfirm = viewModel::confirmMovieRequest,
            onDismiss = viewModel::dismissPendingRequest,
        )
    }

    uiState.pendingTvRequest?.let { pending ->
        TvSeasonRequestDialog(
            pending = pending,
            onConfirm = viewModel::confirmTvRequest,
            onDismiss = viewModel::dismissPendingRequest,
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Media Requests",
                actions = {
                    ExpressiveTextButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Config")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (!uiState.isConfigured) {
            UnconfiguredState(
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Search bar
                item(key = "search_bar") {
                    SearchSection(
                        query = uiState.query,
                        recentSearches = uiState.recentSearches,
                        onQueryChange = viewModel::onQueryChange,
                        onDismissRecent = viewModel::dismissRecentSearch,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                    )
                }

                // Loading shimmer (initial load only)
                if (uiState.isLoading && uiState.results.isEmpty()) {
                    items(3, key = { "shimmer_$it" }) {
                        RequestShimmerCard(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                // Section header
                if (uiState.results.isNotEmpty() && !uiState.isLoading) {
                    item(key = "section_header") {
                        Text(
                            text = if (uiState.query.isBlank()) "Trending" else "Results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                // Results
                items(uiState.results, key = { it.id }) { item ->
                    RequestMediaHeroCard(
                        item = item,
                        tvAvailability = uiState.tvAvailabilityByMediaId[item.id],
                        isLoadingAvailability = item.id in uiState.loadingAvailabilityIds,
                        isTvChecked = item.id in uiState.checkedTvItemIds,
                        isRequesting = uiState.requestingMediaId == item.id,
                        requestingSeasonKey = uiState.requestingSeasonKey,
                        isPluginConfigured = uiState.isPluginConfigured,
                        pluginCapabilities = uiState.pluginCapabilities,
                        isSonarrConfigured = uiState.isSonarrConfigured,
                        onRequest = { viewModel.requestMedia(item) },
                        onRequestSeason = { seasonNumber -> viewModel.requestSeason(item, seasonNumber) },
                        onRequestEpisode = { seasonNumber, episodeNumber ->
                            viewModel.requestMissingEpisode(item, seasonNumber, episodeNumber)
                        },
                        onRequestMissingSeasons = { viewModel.requestMissingSeasons(item) },
                        onCheckAvailability = { viewModel.checkTvAvailability(item) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                // Empty search state
                if (uiState.results.isEmpty() && uiState.query.isNotBlank() && !uiState.isLoading) {
                    item(key = "empty_state") {
                        EmptySearchState(query = uiState.query)
                    }
                }

                item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}

// ─── Search Section ────────────────────────────────────────────────────────────

@OptInAppExperimentalApis
@Composable
private fun SearchSection(
    query: String,
    recentSearches: List<String>,
    onQueryChange: (String) -> Unit,
    onDismissRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies or shows to request…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onQueryChange("") },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        AnimatedVisibility(
            visible = query.isBlank() && recentSearches.isNotEmpty(),
            enter = fadeIn(tween(160)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentSearches) { recent ->
                        InputChip(
                            selected = false,
                            onClick = { onQueryChange(recent) },
                            label = { Text(recent, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onDismissRecent(recent) },
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = JellyfinExpressiveTheme.colors.sectionContainerHigh,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ─── Hero Backdrop Card ────────────────────────────────────────────────────────

@OptInAppExperimentalApis
@Composable
private fun RequestMediaHeroCard(
    item: SeerrMediaItem,
    tvAvailability: TvAvailability?,
    isLoadingAvailability: Boolean,
    isTvChecked: Boolean,
    isRequesting: Boolean,
    requestingSeasonKey: String?,
    isPluginConfigured: Boolean,
    pluginCapabilities: List<String>,
    isSonarrConfigured: Boolean,
    onRequest: () -> Unit,
    onRequestSeason: (Int) -> Unit,
    onRequestEpisode: (Int, Int) -> Unit,
    onRequestMissingSeasons: () -> Unit,
    onCheckAvailability: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdropUrl = item.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    val posterUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }

    val mediaStatus = item.mediaInfo?.status ?: 1
    val isAvailable = mediaStatus == 5
    val isPending = mediaStatus in 2..3
    val isPartial = mediaStatus == 4

    ExpressiveContentCard(
        modifier = modifier
            .fillMaxWidth()
            .expressiveGlow(
                color = MaterialTheme.colorScheme.primary,
                alpha = 0.06f,
                borderRadius = 28.dp,
            ),
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
        shape = JellyfinExpressiveTheme.shapes.section,
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        // ── Hero image with gradient overlay ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    ),
                ),
        ) {
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (posterUrl != null) {
                // No backdrop — center the poster on a tinted background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }

            // Gradient scrim from bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )

            // Info overlay — bottom-aligned
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Badges row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaTypeBadge(mediaType = item.mediaType, year = item.displayDate.take(4))
                    if (isAvailable || isPending || isPartial) {
                        MediaStatusBadge(status = mediaStatus, isRequesting = isRequesting)
                    }
                }
                // Title
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Overview
                if (!item.overview.isNullOrBlank()) {
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── Action row ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                isAvailable -> {
                    MediaStatusBadge(status = 5, isRequesting = false)
                }
                isPending -> {
                    MediaStatusBadge(status = mediaStatus, isRequesting = false)
                }
                else -> {
                    ExpressiveFilledButton(
                        onClick = onRequest,
                        enabled = !isRequesting,
                        modifier = Modifier.height(36.dp),
                    ) {
                        if (isRequesting && requestingSeasonKey == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Request", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // TV availability summary chip
            if (item.mediaType == "tv") {
                when {
                    isLoadingAvailability -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                "Checking…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    isTvChecked && tvAvailability != null -> {
                        AvailabilitySummaryChip(tvAvailability)
                    }
                    isTvChecked -> {
                        Text(
                            "Not on server",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        ExpressiveTonalButton(
                            onClick = onCheckAvailability,
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check library", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // ── TV availability detail ────────────────────────────────────────────
        if (item.mediaType == "tv" && isTvChecked && tvAvailability != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            val canRequestEpisode = (isPluginConfigured && pluginCapabilities.contains("sonarr")) || isSonarrConfigured

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (tvAvailability.missingSeasons.isNotEmpty()) {
                    ExpressiveTonalButton(
                        onClick = onRequestMissingSeasons,
                        enabled = !isRequesting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                    ) {
                        if (isRequesting && requestingSeasonKey?.startsWith("${item.id}:") == true) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Request Missing Seasons", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                tvAvailability.seasons.forEach { season ->
                    SeasonGridBlock(
                        season = season,
                        mediaId = item.id,
                        isRequesting = isRequesting,
                        requestingSeasonKey = requestingSeasonKey,
                        canRequestEpisode = canRequestEpisode,
                        onRequestSeason = { onRequestSeason(season.seasonNumber) },
                        onRequestEpisode = onRequestEpisode,
                    )
                }
            }
        }
    }
}

// ─── Media Badges ─────────────────────────────────────────────────────────────

@Composable
private fun MediaTypeBadge(mediaType: String, year: String) {
    val label = buildString {
        append(if (mediaType == "tv") "TV" else "Movie")
        if (year.isNotBlank()) {
            append(" • ")
            append(year)
        }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MediaStatusBadge(status: Int, isRequesting: Boolean) {
    if (isRequesting) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1565C0))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Color.White)
            Text("Requesting", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        return
    }
    val (bgColor, label) = when (status) {
        5 -> Color(0xFF1B5E20) to "Available"
        4 -> Color(0xFF0D47A1) to "Partial"
        3 -> Color(0xFFE65100) to "Processing"
        2 -> Color(0xFFE65100) to "Pending"
        else -> return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (status == 5) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AvailabilitySummaryChip(availability: TvAvailability) {
    val total = availability.totalEpisodes
    val available = availability.totalAvailableEpisodes
    val allAvailable = available == total
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (allAvailable) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = if (allAvailable) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.primary,
        )
        Text(
            "$available/$total eps",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (allAvailable) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Season Grid Block ────────────────────────────────────────────────────────

@OptInAppExperimentalApis
@Composable
private fun SeasonGridBlock(
    season: TvSeasonAvailability,
    mediaId: Int,
    isRequesting: Boolean,
    requestingSeasonKey: String?,
    canRequestEpisode: Boolean,
    onRequestSeason: () -> Unit,
    onRequestEpisode: (Int, Int) -> Unit,
) {
    var expanded by rememberSaveable(mediaId, season.seasonNumber) { mutableStateOf(false) }
    val progress = if (season.totalCount > 0) season.availableCount.toFloat() / season.totalCount else 0f
    val seasonKey = "$mediaId:${season.seasonNumber}"
    val isRequestingSeason = isRequesting && requestingSeasonKey == seasonKey

    Column {
        // Season header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    season.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = when {
                        progress >= 1f -> MaterialTheme.colorScheme.primary
                        progress > 0f -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${season.availableCount}/${season.totalCount} episodes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                season.canRequestMissingEpisodes -> {
                    ExpressiveTextButton(
                        onClick = onRequestSeason,
                        enabled = !isRequesting,
                        modifier = Modifier.height(34.dp),
                    ) {
                        if (isRequestingSeason) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Season", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                season.hasMissingEpisodes -> {
                    Text(
                        text = if (season.isPendingRequest) "Pending" else stringResource(R.string.seerr_not_requestable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Animated episode grid
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, bottom = 10.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                season.episodes.forEach { episode ->
                    val episodeKey = "$mediaId:${season.seasonNumber}:${episode.episodeNumber}"
                    EpisodePill(
                        episode = episode,
                        canRequestEpisode = canRequestEpisode && !episode.isAvailable,
                        isRequestingEpisode = isRequesting && requestingSeasonKey == episodeKey,
                        isRequestingAny = isRequesting,
                        onClick = { onRequestEpisode(episode.seasonNumber, episode.episodeNumber) },
                    )
                }
            }
        }
    }
}

// ─── Episode Pill ─────────────────────────────────────────────────────────────

@Composable
private fun EpisodePill(
    episode: TvEpisodeAvailability,
    canRequestEpisode: Boolean,
    isRequestingEpisode: Boolean,
    isRequestingAny: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        episode.isAvailable -> MaterialTheme.colorScheme.primaryContainer
        canRequestEpisode -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        episode.isAvailable -> MaterialTheme.colorScheme.onPrimaryContainer
        canRequestEpisode -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val clickable = canRequestEpisode && !isRequestingAny && !episode.isAvailable

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text("E${episode.episodeNumber}: ${episode.title}")
            }
        },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .defaultMinSize(minWidth = 38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isRequestingEpisode) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Text(
                    text = "E${episode.episodeNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
        }
    }
}

// ─── Shimmer Loading ──────────────────────────────────────────────────────────

@Composable
private fun RequestShimmerCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ShimmerBox(modifier = Modifier.fillMaxSize(), cornerRadius = 28)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(modifier = Modifier.width(60.dp).height(18.dp), cornerRadius = 6)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(22.dp), cornerRadius = 6)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp), cornerRadius = 4)
        }
    }
}

// ─── Movie Quality Dialog ─────────────────────────────────────────────────────

@Composable
private fun MovieQualityDialog(
    pending: PendingMovieRequest,
    onConfirm: (SeerrMediaItem, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedProfileId by remember {
        mutableIntStateOf(pending.qualityProfiles.firstOrNull()?.id ?: 1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Quality") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = pending.item.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (pending.qualityProfiles.isEmpty()) {
                    Text(
                        "No quality profiles found — Radarr default will be used.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    pending.qualityProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProfileId = profile.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { selectedProfileId = profile.id },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pending.item, selectedProfileId) }) {
                Text("Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─── TV Season Request Dialog ─────────────────────────────────────────────────

@OptInAppExperimentalApis
@Composable
private fun TvSeasonRequestDialog(
    pending: PendingTvRequest,
    onConfirm: (SeerrMediaItem, List<Int>, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(SeasonRequestMode.ALL) }
    var selectedSeasons by remember { mutableStateOf(pending.seasons.toSet()) }
    var selectedProfileId by remember {
        mutableIntStateOf(pending.qualityProfiles.firstOrNull()?.id ?: 1)
    }

    val latestSeason = pending.seasons.lastOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Seasons") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = pending.item.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                listOf(
                    SeasonRequestMode.ALL to "All seasons",
                    SeasonRequestMode.LATEST to if (latestSeason != null) "Latest season only (S$latestSeason)" else "Latest season",
                    SeasonRequestMode.CUSTOM to "Select specific seasons",
                ).forEach { (m, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = m }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                AnimatedVisibility(
                    visible = mode == SeasonRequestMode.CUSTOM && pending.seasons.isNotEmpty(),
                    enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
                ) {
                    FlowRow(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pending.seasons.forEach { season ->
                            FilterChip(
                                selected = season in selectedSeasons,
                                onClick = {
                                    selectedSeasons = if (season in selectedSeasons) {
                                        selectedSeasons - season
                                    } else {
                                        selectedSeasons + season
                                    }
                                },
                                label = { Text("S$season") },
                            )
                        }
                    }
                }

                if (pending.isUsingSonarrDirect && pending.qualityProfiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Quality",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    pending.qualityProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProfileId = profile.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { selectedProfileId = profile.id },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val seasons = when (mode) {
                        SeasonRequestMode.LATEST -> listOfNotNull(latestSeason)
                        SeasonRequestMode.ALL -> pending.seasons
                        SeasonRequestMode.CUSTOM -> selectedSeasons.sorted()
                    }
                    val profileId = if (pending.isUsingSonarrDirect) selectedProfileId else null
                    onConfirm(pending.item, seasons, profileId)
                },
                enabled = mode != SeasonRequestMode.CUSTOM || selectedSeasons.isNotEmpty(),
            ) {
                Text("Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─── Empty & Unconfigured States ──────────────────────────────────────────────

@OptInAppExperimentalApis
@Composable
private fun UnconfiguredState(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Requests Not Configured",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Connect Seerr, Overseerr, or Jellyseerr — or install the Cinefin server plugin — to start requesting media.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        ExpressiveFilledButton(onClick = onNavigateToSettings) {
            Text("Open Settings")
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                "No results for \"$query\"",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
