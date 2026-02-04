package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.ui.viewmodel.TranscodingDiagnosticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscodingDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TranscodingDiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadLibraryVideos()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcoding Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
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
                    CircularProgressIndicator()
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
                        VideoAnalysisCard(video)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
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
                    Text("Total Videos:", style = MaterialTheme.typography.bodyMedium)
                    Text("Direct Play:", style = MaterialTheme.typography.bodyMedium)
                    Text("Needs Transcoding:", style = MaterialTheme.typography.bodyMedium)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Test Scenarios:", style = MaterialTheme.typography.labelLarge)
            Text("• Video Codec: H.264 ✓ | H.265/VP9/AV1 ✗", style = MaterialTheme.typography.bodySmall)
            Text("• Audio Codec: AAC ✓ | AC3/DTS/TrueHD ✗", style = MaterialTheme.typography.bodySmall)
            Text("• Container: MP4/TS ✓ | MKV/AVI ✗", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VideoAnalysisCard(video: TranscodingDiagnosticsViewModel.VideoAnalysis) {
    Card(
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
                Text(
                    video.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = if (video.needsTranscoding) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        if (video.needsTranscoding) "TRANSCODE" else "DIRECT PLAY",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }

            // Technical specs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Video:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.videoCodec, style = MaterialTheme.typography.bodySmall)

                    Text("Audio:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.audioCodec, style = MaterialTheme.typography.bodySmall)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Container:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.container, style = MaterialTheme.typography.bodySmall)

                    Text("Resolution:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(video.resolution, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Reasons for transcoding
            if (video.transcodingReasons.isNotEmpty()) {
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
                        Text("•", style = MaterialTheme.typography.bodySmall)
                        Text(reason, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
