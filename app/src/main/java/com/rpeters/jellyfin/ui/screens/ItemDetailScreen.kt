package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.ui.components.InContextPlaybackRecommendation
import com.rpeters.jellyfin.ui.components.PlaybackCapabilityDetails
import com.rpeters.jellyfin.ui.utils.EnhancedPlaybackUtils
import com.rpeters.jellyfin.ui.viewmodel.PlaybackRecommendationViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import javax.inject.Inject

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
    private val enhancedPlaybackUtils: EnhancedPlaybackUtils,
) : ViewModel() {
    var item = mutableStateOf<BaseItemDto?>(null)
        private set
    var error = mutableStateOf<String?>(null)
        private set
    var playbackAnalysis = mutableStateOf<com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis?>(null)
        private set

    fun load(itemId: String) {
        viewModelScope.launch {
            when (val result = mediaRepository.getItemDetails(itemId)) {
                is ApiResult.Success -> {
                    item.value = result.data
                    result.data?.let { analyzePlayback(it) }
                }
                is ApiResult.Error -> error.value = result.message
                else -> {}
            }
        }
    }

    private suspend fun analyzePlayback(item: BaseItemDto) {
        try {
            playbackAnalysis.value = enhancedPlaybackUtils.analyzePlaybackCapabilities(item)
        } catch (e: Exception) {
            // Silently handle analysis failures
            playbackAnalysis.value = null
        }
    }
}

@Deprecated("Use HomeVideoDetailScreen or PhotoDetailScreen for media items")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: String,
    onBackClick: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
    recommendationViewModel: PlaybackRecommendationViewModel = hiltViewModel(),
) {
    val itemState = viewModel.item
    val errorState = viewModel.error
    val playbackAnalysis = viewModel.playbackAnalysis
    val recommendations by recommendationViewModel.recommendations.collectAsState()

    LaunchedEffect(itemId) {
        viewModel.load(itemId)
    }

    LaunchedEffect(itemState.value) {
        itemState.value?.let { item ->
            recommendationViewModel.analyzeItem(item)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = itemState.value?.name ?: "Item",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                errorState.value?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }

            itemState.value?.let { item ->
                // Playback Recommendations Section
                item {
                    if (recommendations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Playback Recommendations",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            recommendations.forEach { recommendation ->
                                InContextPlaybackRecommendation(
                                    recommendation = recommendation,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                }

                // Playback Capabilities Section
                item {
                    playbackAnalysis.value?.let { analysis ->
                        PlaybackCapabilityDetails(
                            analysis = analysis,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                }

                // Media Details Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Media Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Text(text = "Name: ${item.name}")
                        item.runTimeTicks?.let { ticks ->
                            val minutes = (ticks / 10_000_000L) / 60
                            Text(text = "Runtime: $minutes min")
                        }
                        item.productionYear?.let { year -> Text(text = "Year: $year") }
                        item.dateCreated?.let { date -> Text(text = "Date: $date") }
                    }
                }

                // Technical Details Section
                item {
                    val videoStream = item.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
                    val audioStream = item.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Technical Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        videoStream?.codec?.let { Text(text = "Video codec: $it") }
                        audioStream?.codec?.let { Text(text = "Audio codec: $it") }
                        item.container?.let { Text(text = "Container: $it") }
                        audioStream?.channels?.let { Text(text = "Audio channels: $it") }
                        videoStream?.bitRate?.let { Text(text = "Video bitrate: ${it / 1000} kbps") }
                        audioStream?.bitRate?.let { Text(text = "Audio bitrate: ${it / 1000} kbps") }
                        videoStream?.width?.let { width ->
                            videoStream.height?.let { height ->
                                Text(text = "Resolution: ${width}x$height")
                            }
                        }
                        videoStream?.aspectRatio?.let {
                            Text(text = "Aspect ratio: $it")
                        }
                        videoStream?.averageFrameRate?.let {
                            Text(text = "Frame rate: ${"%.2f".format(it)} fps")
                        }
                    }
                }
            }
        }
    }
}
