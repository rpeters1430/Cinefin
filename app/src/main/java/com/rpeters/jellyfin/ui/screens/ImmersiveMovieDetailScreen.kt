package com.rpeters.jellyfin.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.AiSummaryCard
import com.rpeters.jellyfin.ui.components.PlaybackBreakdownDetails
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.QualitySelectionDialog
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.screens.details.components.ActionButton
import com.rpeters.jellyfin.ui.screens.details.components.ChapterListSection
import com.rpeters.jellyfin.ui.screens.details.components.DetailCastAndCrewSection
import com.rpeters.jellyfin.ui.screens.details.components.DetailSubtitleRow
import com.rpeters.jellyfin.ui.screens.details.components.DetailVideoInfoRow
import com.rpeters.jellyfin.ui.screens.details.components.MovieHeroContent
import com.rpeters.jellyfin.ui.screens.details.components.WhyYoullLoveThisCard
import com.rpeters.jellyfin.ui.screens.details.components.getResolutionBadge
import com.rpeters.jellyfin.ui.screens.details.components.getResolutionIcon
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.theme.JellyfinTeal80
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import java.util.UUID

@OptIn(UnstableApi::class)
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
    getChapterImageUrl: (chapterIndex: Int, imageTag: String?) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    getPersonImageUrl: (org.jellyfin.sdk.model.api.BaseItemPerson) -> String?,
    serverUrl: String?,
    onGenerateAiSummary: () -> Unit,
    aiSummary: String? = null,
    isLoadingAiSummary: Boolean = false,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()

    ImmersiveMovieDetailContent(
        movie = movie,
        relatedItems = relatedItems,
        playbackProgress = playbackProgress,
        onBackClick = onBackClick,
        onPlayClick = onPlayClick,
        onFavoriteClick = onFavoriteClick,
        onShareClick = onShareClick,
        onDeleteClick = onDeleteClick,
        onMarkWatchedClick = onMarkWatchedClick,
        onDownloadClick = onDownloadClick,
        isDownloaded = isDownloaded,
        isOffline = isOffline,
        downloadInfo = downloadInfo,
        onDeleteOfflineCopy = onDeleteOfflineCopy,
        onRelatedMovieClick = onRelatedMovieClick,
        onPersonClick = onPersonClick,
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
        playbackAnalysis = playbackAnalysis,
        whyYoullLoveThis = whyYoullLoveThis,
        isLoadingWhyYoullLoveThis = isLoadingWhyYoullLoveThis,
        getImageUrl = getImageUrl,
        getChapterImageUrl = getChapterImageUrl,
        getBackdropUrl = getBackdropUrl,
        getLogoUrl = getLogoUrl,
        getPersonImageUrl = getPersonImageUrl,
        serverUrl = serverUrl,
        onGenerateAiSummary = onGenerateAiSummary,
        aiSummary = aiSummary,
        isLoadingAiSummary = isLoadingAiSummary,
        animatedVisibilityScope = animatedVisibilityScope,
        downloadsViewModel = downloadsViewModel,
    )
}

@OptIn(UnstableApi::class)
@OptInAppExperimentalApis
@Composable
private fun ImmersiveMovieDetailContent(
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
    getChapterImageUrl: (chapterIndex: Int, imageTag: String?) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    getLogoUrl: (BaseItemDto) -> String?,
    getPersonImageUrl: (org.jellyfin.sdk.model.api.BaseItemPerson) -> String?,
    serverUrl: String?,
    onGenerateAiSummary: () -> Unit,
    aiSummary: String? = null,
    isLoadingAiSummary: Boolean = false,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    downloadsViewModel: DownloadsViewModel? = null,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        com.rpeters.jellyfin.ui.navigation.LocalAnimatedVisibilityScope provides animatedVisibilityScope,
    ) {
        val context = LocalContext.current
        var isFavorite by remember(movie.id) { mutableStateOf(movie.userData?.isFavorite == true) }
        var isWatched by remember(movie.id) { mutableStateOf(movie.userData?.played ?: false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var showDownloadQualityDialog by remember { mutableStateOf(false) }
        var showMoreOptions by remember { mutableStateOf(false) }

        val listState = remember(movie.id) { LazyListState() }

        // Permission launcher for downloads
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
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
                    height = ImmersiveDimens.HeroHeightPhone,
                    itemId = movie.id.toString(),
                    animatedVisibilityScope = animatedVisibilityScope,
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

                    item(key = "background_spacer") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.background),
                        )
                    }

                    // Action Row
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
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
                    }

                    // AI Features
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (isLoadingWhyYoullLoveThis || !whyYoullLoveThis.isNullOrBlank()) {
                                    WhyYoullLoveThisCard(
                                        pitch = whyYoullLoveThis,
                                        isLoading = isLoadingWhyYoullLoveThis,
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "AI Summary",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    TextButton(onClick = onGenerateAiSummary, enabled = !isLoadingAiSummary) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (aiSummary != null) "Regenerate" else "Generate")
                                    }
                                }

                                if (isLoadingAiSummary || !aiSummary.isNullOrBlank()) {
                                    AiSummaryCard(
                                        summary = aiSummary,
                                        isLoading = isLoadingAiSummary,
                                    )
                                }
                            }
                        }
                    }

                    // Synopsis
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
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

                                playbackAnalysis?.let { analysis ->
                                    Column(
                                        modifier = Modifier.padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        PlaybackStatusBadge(analysis = analysis)
                                        if (analysis.transcodeReasons.isNotEmpty()) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                analysis.transcodeReasons.distinct().forEach { reason ->
                                                    AssistChip(
                                                        onClick = {},
                                                        enabled = false,
                                                        label = { Text(reason) },
                                                    )
                                                }
                                            }
                                        }
                                        if (analysis.breakdown.isNotEmpty()) {
                                            PlaybackBreakdownDetails(
                                                breakdown = analysis.breakdown,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }

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
                                                shape = MaterialTheme.shapes.small,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tech Specs / Detailed Info
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            MovieTechSpecsSection(movie)
                        }
                    }

                    // Chapters
                    val movieChapters = movie.chapters ?: emptyList()
                    if (movieChapters.isNotEmpty()) {
                        item(key = "chapters") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                ChapterListSection(
                                    chapters = movieChapters,
                                    onChapterClick = { positionMs -> onPlayClick(movie, null, positionMs) },
                                    getChapterImageUrl = { chapter, index ->
                                        getChapterImageUrl(index, chapter.imageTag)
                                    },
                                )
                            }
                        }
                    }

                    // Cast & Crew
                    item {
                        val people = movie.people ?: emptyList()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            DetailCastAndCrewSection(
                                directors = people.filter { it.type == PersonKind.DIRECTOR },
                                writers = people.filter { it.type == PersonKind.WRITER },
                                producers = people.filter { it.type == PersonKind.PRODUCER },
                                cast = people.filter { it.type == PersonKind.ACTOR },
                                getPersonImageUrl = getPersonImageUrl,
                                onPersonClick = onPersonClick,
                            )
                        }
                    }

                    // Related Movies
                    if (relatedItems.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
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
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    androidx.compose.foundation.lazy.LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(relatedItems) { relatedMovie ->
                                            com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard(
                                                title = relatedMovie.name ?: "Unknown",
                                                imageUrl = getImageUrl(relatedMovie) ?: "",
                                                onCardClick = { onRelatedMovieClick(relatedMovie.id.toString()) },
                                                cardSize = com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize.SMALL,
                                                modifier = Modifier.width(ImmersiveDimens.CardWidthSmall),
                                            )
                                        }
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

        if (showDownloadQualityDialog && downloadsViewModel != null) {
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
    }
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
            shape = MaterialTheme.shapes.medium,
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
            shape = MaterialTheme.shapes.large,
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

private fun createPreviewMovie(): BaseItemDto {
    return BaseItemDto(
        id = UUID.randomUUID(),
        name = "Inception",
        overview = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
        genres = listOf("Action", "Sci-Fi", "Thriller"),
        runTimeTicks = 88800000000L, // 148 mins
        productionYear = 2010,
        type = BaseItemKind.MOVIE,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MovieActionRowPreview() {
    JellyfinAndroidTheme {
        MovieActionRow(
            movie = createPreviewMovie(),
            isFavorite = false,
            isWatched = false,
            onPlayClick = {},
            onFavoriteClick = {},
            onMarkWatchedClick = {},
            onDownloadClick = {},
            onShareClick = {},
            onMoreClick = {},
        )
    }
}

@OptIn(UnstableApi::class)
@Preview(showBackground = true)
@Composable
private fun ImmersiveMovieDetailScreenPreview() {
    JellyfinAndroidTheme {
        ImmersiveMovieDetailContent(
            movie = createPreviewMovie(),
            relatedItems = listOf(createPreviewMovie()),
            onBackClick = {},
            onPlayClick = { _, _, _ -> },
            onFavoriteClick = {},
            onShareClick = {},
            onDeleteClick = {},
            onMarkWatchedClick = {},
            onDownloadClick = { _, _ -> },
            isDownloaded = false,
            isOffline = false,
            onDeleteOfflineCopy = {},
            onRelatedMovieClick = {},
            onPersonClick = { _, _ -> },
            onRefresh = {},
            isRefreshing = false,
            getImageUrl = { _ -> null },
            getChapterImageUrl = { _, _ -> null },
            getBackdropUrl = { _ -> null },
            getLogoUrl = { _ -> null },
            getPersonImageUrl = { _ -> null },
            serverUrl = "http://localhost:8096",
            onGenerateAiSummary = {},
        )
    }
}
