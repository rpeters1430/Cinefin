package com.rpeters.jellyfin.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.AiSummaryCard
import com.rpeters.jellyfin.ui.components.QualitySelectionDialog
import com.rpeters.jellyfin.ui.components.sanitizedAiSummary
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.screens.details.components.ActionButton
import com.rpeters.jellyfin.ui.screens.details.components.DetailCastAndCrewSection
import com.rpeters.jellyfin.ui.screens.details.components.DetailSubtitleRow
import com.rpeters.jellyfin.ui.screens.details.components.DetailVideoInfoRow
import com.rpeters.jellyfin.ui.screens.details.components.MovieHeroContent
import com.rpeters.jellyfin.ui.screens.details.components.WhyYoullLoveThisCard
import com.rpeters.jellyfin.ui.screens.details.components.getResolutionBadge
import com.rpeters.jellyfin.ui.screens.details.components.getResolutionIcon
import com.rpeters.jellyfin.ui.theme.JellyfinTeal80
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind

@OptIn(ExperimentalMaterial3Api::class)
@OptInAppExperimentalApis
@Composable
fun ImmersiveMovieDetailScreen(
    movie: BaseItemDto,
    relatedItems: List<BaseItemDto> = emptyList(),
    playbackProgress: com.rpeters.jellyfin.ui.player.PlaybackProgress? = null,
    onBackClick: () -> Unit,
    onPlayClick: (BaseItemDto, Int?, Long?) -> Unit,
    onFavoriteClick: (BaseItemDto) -> Unit,
    onShareClick: (BaseItemDto) -> Unit,
    onDeleteClick: (BaseItemDto) -> Unit,
    onMarkWatchedClick: (BaseItemDto) -> Unit,
    onDownloadClick: (BaseItemDto, com.rpeters.jellyfin.data.offline.VideoQuality) -> Unit,
    isDownloaded: Boolean,
    isOffline: Boolean,
    downloadInfo: com.rpeters.jellyfin.data.offline.OfflineDownload? = null,
    onDeleteOfflineCopy: () -> Unit,
    onRelatedMovieClick: (String) -> Unit,
    onPersonClick: (String, String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    whyYoullLoveThis: String? = null,
    isLoadingWhyYoullLoveThis: Boolean = false,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    getPersonImageUrl: (org.jellyfin.sdk.model.api.BaseItemPerson) -> String?,
    serverUrl: String?,
    onGenerateAiSummary: () -> Unit,
    aiSummary: String? = null,
    isLoadingAiSummary: Boolean = false,
) {
    val context = LocalContext.current
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()
    var isFavorite by remember(movie.id) { mutableStateOf(movie.userData?.isFavorite == true) }
    var isWatched by remember(movie.id) { mutableStateOf(movie.userData?.played ?: false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    val listState = remember(movie.id) { LazyListState() }

    // Permission launcher for downloads
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showDownloadQualityDialog = true
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Background Backdrop (Hero)
            StaticHeroSection(
                imageUrl = getBackdropUrl(movie),
                modifier = Modifier.height(400.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 328.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    ),
            )

            // 2. Main Content
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // Header (Hero Overlay + Metadata)
                item {
                    MovieHeroContent(
                        movie = movie,
                        getLogoUrl = getLogoUrl,
                    )
                }

                // Action Row
                item {
                    MovieActionRow(
                        movie = movie,
                        isFavorite = isFavorite,
                        isWatched = isWatched,
                        onPlayClick = {
                            val resumePos = playbackProgress?.positionMs ?: 0L
                            onPlayClick(movie, null, resumePos)
                        },
                        onFavoriteClick = {
                            isFavorite = !isFavorite
                            onFavoriteClick(movie)
                        },
                        onMarkWatchedClick = {
                            isWatched = !isWatched
                            onMarkWatchedClick(movie)
                        },
                        onDownloadClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                    showDownloadQualityDialog = true
                                } else {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                showDownloadQualityDialog = true
                            }
                        },
                        onShareClick = { onShareClick(movie) },
                        onMoreClick = { showMoreOptions = true },
                    )
                }

                // AI Features
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        WhyYoullLoveThisCard(
                            pitch = whyYoullLoveThis,
                            isLoading = isLoadingWhyYoullLoveThis,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onGenerateAiSummary, enabled = !isLoadingAiSummary) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (aiSummary != null) "Regenerate" else "Generate")
                            }
                        }

                        AiSummaryCard(
                            summary = aiSummary,
                            isLoading = isLoadingAiSummary,
                        )
                    }
                }

                // Synopsis
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Synopsis",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = movie.overview ?: "No synopsis available.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.4f),
                        )

                        // Genres Tag Flow
                        movie.genres?.let { genres ->
                            FlowRow(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                genres.forEach { genre ->
                                    AssistChip(
                                        onClick = { /* Navigate to genre */ },
                                        label = { Text(genre) },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Tech Specs / Detailed Info
                item {
                    MovieTechSpecsSection(movie)
                }

                // Cast & Crew
                item {
                    val people = movie.people ?: emptyList()
                    DetailCastAndCrewSection(
                        directors = people.filter { it.type == PersonKind.DIRECTOR },
                        writers = people.filter { it.type == PersonKind.WRITER },
                        producers = people.filter { it.type == PersonKind.PRODUCER },
                        cast = people.filter { it.type == PersonKind.ACTOR },
                        getPersonImageUrl = getPersonImageUrl,
                        onPersonClick = onPersonClick,
                    )
                }

                // Related Movies
                if (relatedItems.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "More Like This",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            // Use PerformanceOptimizedLazyRow or similar here if available
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(relatedItems) { relatedMovie ->
                                    com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard(
                                        title = relatedMovie.name ?: "Unknown",
                                        imageUrl = getImageUrl(relatedMovie) ?: "",
                                        onCardClick = { onRelatedMovieClick(relatedMovie.id.toString()) },
                                        cardSize = com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize.SMALL,
                                        modifier = Modifier.width(
                                            com.rpeters.jellyfin.ui.theme.ImmersiveDimens.CardWidthSmall
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp),
                    onClick = onBackClick,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp),
                    )
                }

                Box {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp),
                        onClick = { showMoreOptions = true },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreOptions,
                        onDismissRequest = { showMoreOptions = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                onShareClick(movie)
                                showMoreOptions = false
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                            },
                        )
                        if (isDownloaded) {
                            DropdownMenuItem(
                                text = { Text("Delete offline copy") },
                                onClick = {
                                    onDeleteOfflineCopy()
                                    showMoreOptions = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete movie") },
                            onClick = {
                                showDeleteDialog = true
                                showMoreOptions = false
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDownloadQualityDialog) {
        QualitySelectionDialog(
            item = movie,
            onDismiss = { showDownloadQualityDialog = false },
            onQualitySelected = { quality ->
                onDownloadClick(movie, quality)
                showDownloadQualityDialog = false
            },
            downloadsViewModel = downloadsViewModel,
        )
    }

    // Dialogs
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Movie") },
            text = { Text("Are you sure you want to delete ${movie.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick(movie)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Reuse QualitySelectionDialog logic from nav graph if needed, 
    // or use a local one. Here we assume we need to provide a ViewModel for it.
    // However, QualitySelectionDialog requires a downloadsViewModel.
    // Since we don't have it here, we'll assume the parent handles it OR we inject it.
    // For now, let's assume it's passed or handled.
}

@Composable
private fun MovieActionRow(
    movie: BaseItemDto,
    isFavorite: Boolean,
    isWatched: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMarkWatchedClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Main Play Button
        TextButton(
            onClick = onPlayClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Watch Now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Liked" else "Like",
                onClick = onFavoriteClick,
                modifier = Modifier.weight(1f),
                contentColor = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ActionButton(
                icon = if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                label = if (isWatched) "Watched" else "Mark",
                onClick = onMarkWatchedClick,
                modifier = Modifier.weight(1f),
                contentColor = if (isWatched) JellyfinTeal80 else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ActionButton(
                icon = Icons.Rounded.Share,
                label = "Share",
                onClick = onShareClick,
                modifier = Modifier.weight(1f),
            )

            ActionButton(
                icon = Icons.Rounded.FileDownload,
                label = "Save",
                onClick = onDownloadClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MovieTechSpecsSection(movie: BaseItemDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Technical Specs",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        val mediaSource = movie.mediaSources?.firstOrNull()
        val videoStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.VIDEO }
        val audioStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.AUDIO }
        val subtitles = mediaSource?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE } ?: emptyList()

        androidx.compose.material3.ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Video Row
                DetailVideoInfoRow(
                    label = "Video",
                    codec = videoStream?.codec?.uppercase(),
                    icon = getResolutionIcon(videoStream?.width, videoStream?.height),
                    resolutionBadge = getResolutionBadge(videoStream?.width, videoStream?.height),
                )

                // Audio Row
                DetailVideoInfoRow(
                    label = "Audio",
                    codec = audioStream?.codec?.uppercase() ?: "Unknown",
                    icon = Icons.Rounded.Public, // Placeholder for audio icon
                )

                // Subtitles Row
                if (subtitles.isNotEmpty()) {
                    DetailSubtitleRow(
                        subtitles = subtitles,
                        selectedSubtitleIndex = null,
                        onSubtitleSelect = {},
                    )
                }
            }
        }
    }
}
