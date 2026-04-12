package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveContentCard
import com.rpeters.jellyfin.ui.components.PlaybackBreakdownDetails
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveWavyCircularLoading
import com.rpeters.jellyfin.ui.viewmodel.TranscodingDiagnosticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscodingDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    onItemClick: (org.jellyfin.sdk.model.api.BaseItemDto) -> Unit = {},
    viewModel: TranscodingDiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadLibraryVideos()
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(id = R.string.transcoding_diagnostics),
                navigationIcon = {
                    ExpressiveBackNavigationIcon(onClick = onNavigateBack)
                }
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is TranscodingDiagnosticsViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    ExpressiveWavyCircularLoading(
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            is TranscodingDiagnosticsViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                }
            }
            is TranscodingDiagnosticsViewModel.UiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Summary card
                    item {
                        SummaryCard(
                            totalVideos = state.videos.size,
                            directPlayCount = state.videos.count { !it.needsTranscoding },
                            transcodingCount = state.videos.count { it.needsTranscoding },
                        )
                    }

                    // Legend
                    item {
                        LegendCard()
                    }

                    // Video list
                    items(state.videos) { video ->
                        VideoAnalysisCard(
                            video = video,
                            onClick = { onItemClick(video.item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totalVideos: Int,
    directPlayCount: Int,
    transcodingCount: Int,
) {
    ExpressiveContentCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Library Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(stringResource(id = R.string.total_videos_label) + ":", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(id = R.string.direct_play_label) + ":", style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(id = R.string.needs_transcoding_label) + ":", style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$totalVideos", fontWeight = FontWeight.Bold)
                    Text("$directPlayCount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    Text("$transcodingCount", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LegendCard() {
    ExpressiveContentCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(id = R.string.test_scenarios), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(id = R.string.test_scenario_video_codec), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(id = R.string.test_scenario_audio_codec), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(id = R.string.test_scenario_container), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VideoAnalysisCard(
    video: TranscodingDiagnosticsViewModel.VideoAnalysis,
    onClick: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (video.needsTranscoding) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Title and playback method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        video.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        video.itemType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = if (video.needsTranscoding) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            video.methodLabel.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View details",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Technical specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = R.string.video_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.videoCodec, style = MaterialTheme.typography.bodySmall)

                    Text(stringResource(id = R.string.audio_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.audioCodec, style = MaterialTheme.typography.bodySmall)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = R.string.container_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.container, style = MaterialTheme.typography.bodySmall)

                    Text(stringResource(id = R.string.resolution_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.resolution, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Reasons for transcoding (only show if transcoding is needed)
            if (video.needsTranscoding && video.transcodingReasons.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Why transcoding needed:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                video.transcodingReasons.forEach { reason ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(id = R.string.bullet), style = MaterialTheme.typography.bodySmall)
                        Text(reason, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (video.breakdown.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                PlaybackBreakdownDetails(
                    breakdown = video.breakdown,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
