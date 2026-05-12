package com.rpeters.jellyfin.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveTextButton
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.theme.JellyfinExpressiveTheme
import com.rpeters.jellyfin.ui.viewmodel.RequestsViewModel
import com.rpeters.jellyfin.ui.viewmodel.TvAvailability
import com.rpeters.jellyfin.ui.viewmodel.TvEpisodeAvailability
import com.rpeters.jellyfin.ui.viewmodel.TvSeasonAvailability

@Composable
fun RequestsScreen(
    initialQuery: String? = null,
    onNavigateToSettings: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val seerrPrefs by viewModel.seerrPreferences.collectAsStateWithLifecycle()
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (!uiState.isConfigured) {
            UnconfiguredState(
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("Search movies or shows to request...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                if (uiState.isLoading && uiState.results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
                    EmptyState(query = uiState.query)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.query.isBlank() && uiState.results.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Trending",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        items(uiState.results) { item ->
                            RequestMediaItem(
                                item = item,
                                seerrBaseUrl = seerrPrefs.baseUrl,
                                tvAvailability = uiState.tvAvailabilityByMediaId[item.id],
                                isLoadingAvailability = item.id in uiState.loadingAvailabilityIds,
                                isTvChecked = item.id in uiState.checkedTvItemIds,
                                isRequesting = uiState.requestingMediaId == item.id,
                                requestingSeasonKey = uiState.requestingSeasonKey,
                                onRequest = { viewModel.requestMedia(item) },
                                onRequestSeason = { seasonNumber -> viewModel.requestSeason(item, seasonNumber) },
                                onRequestMissingSeasons = { viewModel.requestMissingSeasons(item) },
                                onCheckAvailability = { viewModel.checkTvAvailability(item) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestMediaItem(
    item: SeerrMediaItem,
    seerrBaseUrl: String,
    tvAvailability: TvAvailability?,
    isLoadingAvailability: Boolean,
    isTvChecked: Boolean,
    isRequesting: Boolean,
    requestingSeasonKey: String?,
    onRequest: () -> Unit,
    onRequestSeason: (Int) -> Unit,
    onRequestMissingSeasons: () -> Unit,
    onCheckAvailability: () -> Unit,
) {
    val posterUrl = item.posterPath?.let { path ->
        // Standard Seerr/Overseerr proxy for posters or TMDB direct if configured
        // Here we assume we can build the TMDB URL or Seerr proxy
        "https://image.tmdb.org/t/p/w500$path"
    }
    
    val status = item.mediaInfo?.status ?: 1
    val isAvailable = status == 5
    val isPending = status == 2 || status == 3

    ExpressiveContentCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = JellyfinExpressiveTheme.colors.sectionContainer,
        shape = JellyfinExpressiveTheme.shapes.section
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 80.dp, height = 120.dp)
                        .clip(JellyfinExpressiveTheme.shapes.control),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.mediaType.uppercase()} • ${item.displayDate.take(4)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isAvailable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (isPending) {
                        Text("Request Pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    } else {
                        ExpressiveFilledButton(
                            onClick = onRequest,
                            enabled = !isRequesting,
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (isRequesting && requestingSeasonKey == null) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Request", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            if (item.mediaType == "tv") {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    isLoadingAvailability -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Checking server episodes…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    isTvChecked && tvAvailability != null -> {
                        TvAvailabilitySection(
                            availability = tvAvailability,
                            isRequesting = isRequesting,
                            requestingSeasonKey = requestingSeasonKey,
                            mediaId = item.id,
                            onRequestSeason = onRequestSeason,
                            onRequestMissingSeasons = onRequestMissingSeasons
                        )
                    }
                    isTvChecked -> {
                        Text(
                            "Not found on your server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        ExpressiveTextButton(
                            onClick = onCheckAvailability,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check on server", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAvailabilitySection(
    availability: TvAvailability,
    isRequesting: Boolean,
    requestingSeasonKey: String?,
    mediaId: Int,
    onRequestSeason: (Int) -> Unit,
    onRequestMissingSeasons: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "${availability.totalAvailableEpisodes}/${availability.totalEpisodes} episodes on server",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (availability.missingSeasons.isNotEmpty()) {
            ExpressiveFilledButton(
                onClick = onRequestMissingSeasons,
                enabled = !isRequesting,
                modifier = Modifier.height(36.dp)
            ) {
                if (isRequesting && requestingSeasonKey?.startsWith("$mediaId:") == true) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Request Missing Seasons", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        availability.seasons.forEach { season ->
            TvSeasonAvailabilityBlock(
                season = season,
                isRequesting = isRequesting,
                requestingSeasonKey = requestingSeasonKey,
                mediaId = mediaId,
                onRequestSeason = onRequestSeason
            )
        }
    }
}

@Composable
private fun TvSeasonAvailabilityBlock(
    season: TvSeasonAvailability,
    isRequesting: Boolean,
    requestingSeasonKey: String?,
    mediaId: Int,
    onRequestSeason: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    season.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${season.availableCount}/${season.totalCount} episodes available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (season.canRequestMissingEpisodes) {
                val seasonKey = "$mediaId:${season.seasonNumber}"
                ExpressiveTextButton(
                    onClick = { onRequestSeason(season.seasonNumber) },
                    enabled = !isRequesting,
                    modifier = Modifier.height(34.dp)
                ) {
                    if (isRequesting && requestingSeasonKey == seasonKey) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Request Season")
                    }
                }
            } else if (season.hasMissingEpisodes) {
                Text(
                    "Not requestable in Jellyseerr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        season.episodes.forEach { episode ->
            TvEpisodeAvailabilityRow(
                episode = episode,
                canRequestSeason = season.canRequestMissingEpisodes,
                isRequesting = isRequesting && requestingSeasonKey == "$mediaId:${season.seasonNumber}",
                onRequestSeason = { onRequestSeason(season.seasonNumber) }
            )
        }
    }
}

@Composable
private fun TvEpisodeAvailabilityRow(
    episode: TvEpisodeAvailability,
    canRequestSeason: Boolean,
    isRequesting: Boolean,
    onRequestSeason: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (episode.isAvailable) Icons.Default.Check else Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (episode.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "E${episode.episodeNumber}. ${episode.title}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        when {
            episode.isAvailable -> {
                Text(
                    "On server",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            canRequestSeason -> {
                ExpressiveTextButton(
                    onClick = onRequestSeason,
                    enabled = !isRequesting,
                    modifier = Modifier.height(32.dp)
                ) {
                    if (isRequesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Request Season", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            else -> {
                Text(
                    "Missing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UnconfiguredState(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Seerr Not Configured",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "To request new media, please configure your Seerr, Overseerr, or Jellyseerr instance in settings.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        ExpressiveFilledButton(onClick = onNavigateToSettings) {
            Text("Go to Settings")
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No results for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
