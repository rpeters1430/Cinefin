package com.rpeters.jellyfin.ui.screens

import android.Manifest
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AddCircle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.rpeters.jellyfin.ui.components.immersive.HdrType
import com.rpeters.jellyfin.ui.components.immersive.ResolutionQuality
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.components.immersive.rememberScrollCollapseFraction
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.screens.details.components.ActionButton
import com.rpeters.jellyfin.ui.screens.details.components.ChapterListSection
import com.rpeters.jellyfin.ui.screens.details.components.DetailCastAndCrewSection
import com.rpeters.jellyfin.ui.screens.details.components.MovieHeroContent
import com.rpeters.jellyfin.ui.screens.details.components.WhyYoullLoveThisCard
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
    onTrailerClick: (String) -> Unit = {},
    onSearchRequests: (String) -> Unit = {},
    isDownloaded: Boolean,
    isOffline: Boolean,
    downloadInfo: com.rpeters.jellyfin.data.offline.OfflineDownload? = null,
    onDeleteOfflineCopy: () -> Unit,
    onRelatedMovieClick: (String) -> Unit,
    onPersonClick: (String, String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    onGenerateWhyYoullLoveThis: () -> Unit = {},
    whyYoullLoveThis: String? = null,
    isLoadingWhyYoullLoveThis: Boolean = false,
    contentWarnings: List<String> = emptyList(),
    isLoadingContentWarnings: Boolean = false,
    aiChapterMarkers: List<org.jellyfin.sdk.model.api.ChapterInfo> = emptyList(),
    isLoadingAiChapterMarkers: Boolean = false,
    getImageUrl: (BaseItemDto) -> String?,
    getChapterImageUrl: (chapterIndex: Int, imageTag: String?) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
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
        onSearchRequests = onSearchRequests,
        isDownloaded = isDownloaded,
        isOffline = isOffline,
        downloadInfo = downloadInfo,
        onDeleteOfflineCopy = onDeleteOfflineCopy,
        onRelatedMovieClick = onRelatedMovieClick,
        onPersonClick = onPersonClick,
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
        playbackAnalysis = playbackAnalysis,
        onGenerateWhyYoullLoveThis = onGenerateWhyYoullLoveThis,
        whyYoullLoveThis = whyYoullLoveThis,
        isLoadingWhyYoullLoveThis = isLoadingWhyYoullLoveThis,
        contentWarnings = contentWarnings,
        isLoadingContentWarnings = isLoadingContentWarnings,
        aiChapterMarkers = aiChapterMarkers,
        isLoadingAiChapterMarkers = isLoadingAiChapterMarkers,
        getImageUrl = getImageUrl,
        getChapterImageUrl = getChapterImageUrl,
        getBackdropUrl = getBackdropUrl,
        getPersonImageUrl = getPersonImageUrl,
        serverUrl = serverUrl,
        onGenerateAiSummary = onGenerateAiSummary,
        aiSummary = aiSummary,
        isLoadingAiSummary = isLoadingAiSummary,
        animatedVisibilityScope = animatedVisibilityScope,
        downloadsViewModel = downloadsViewModel,
    )
}

/** Overlap of the poster over the bottom edge of the collapsing backdrop (density pass). */
private val DetailPosterOverlap = 58.dp

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
    onTrailerClick: (String) -> Unit = {},
    onSearchRequests: (String) -> Unit = {},
    isDownloaded: Boolean,
    isOffline: Boolean,
    downloadInfo: com.rpeters.jellyfin.data.offline.OfflineDownload? = null,
    onDeleteOfflineCopy: () -> Unit,
    onRelatedMovieClick: (String) -> Unit,
    onPersonClick: (String, String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    onGenerateWhyYoullLoveThis: () -> Unit = {},
    whyYoullLoveThis: String? = null,
    isLoadingWhyYoullLoveThis: Boolean = false,
    contentWarnings: List<String> = emptyList(),
    isLoadingContentWarnings: Boolean = false,
    aiChapterMarkers: List<org.jellyfin.sdk.model.api.ChapterInfo> = emptyList(),
    isLoadingAiChapterMarkers: Boolean = false,
    getImageUrl: (BaseItemDto) -> String?,
    getChapterImageUrl: (chapterIndex: Int, imageTag: String?) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
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

        // Density pass: collapsing backdrop (200dp -> 56dp) tied to scroll position.
        val backdropCollapseRangePx = with(LocalDensity.current) {
            (ImmersiveDimens.DetailBackdropHeight - ImmersiveDimens.DetailBackdropCollapsed).toPx()
        }
        val backdropCollapseFraction by rememberScrollCollapseFraction(
            listState = listState,
            collapseRangePx = backdropCollapseRangePx,
        )
        val backdropHeight = ImmersiveDimens.DetailBackdropHeight -
            (ImmersiveDimens.DetailBackdropHeight - ImmersiveDimens.DetailBackdropCollapsed) * backdropCollapseFraction
        // The poster/title header sits just below the backdrop, overlapping its bottom edge.
        val heroContentTopOffset = (backdropHeight - DetailPosterOverlap).coerceAtLeast(0.dp)

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
                // 1. Background Backdrop (Hero) - collapses from 200dp to 56dp as the user scrolls.
                StaticHeroSection(
                    imageUrl = getBackdropUrl(movie),
                    height = backdropHeight,
                    itemId = movie.id.toString(),
                    animatedVisibilityScope = animatedVisibilityScope,
                )

                // 2. Main Content
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    // Header (poster overlapping backdrop + left-aligned title block)
                    item {
                        MovieHeroContent(
                            movie = movie,
                            posterUrl = getImageUrl(movie),
                            modifier = Modifier.padding(top = heroContentTopOffset),
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
                                onRequestClick = {
                                    movie.name?.takeIf { it.isNotBlank() }?.let(onSearchRequests)
                                },
                                onTrailerClick = onTrailerClick,
                                onMoreClick = { showMoreOptions = true },
                            )
                        }
                    }

                    movieAiFeaturesItem(
                        isLoadingWhyYoullLoveThis = isLoadingWhyYoullLoveThis,
                        whyYoullLoveThis = whyYoullLoveThis,
                        onGenerateWhyYoullLoveThis = onGenerateWhyYoullLoveThis,
                        onGenerateAiSummary = onGenerateAiSummary,
                        isLoadingAiSummary = isLoadingAiSummary,
                        aiSummary = aiSummary,
                        isLoadingContentWarnings = isLoadingContentWarnings,
                        contentWarnings = contentWarnings,
                    )

                    movieSynopsisItem(
                        movie = movie,
                        playbackAnalysis = playbackAnalysis,
                    )

                    // Tech Specs / Detailed Info
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            MovieTechSpecsSection(movie, playbackAnalysis)
                        }
                    }

                    movieChaptersItem(
                        movie = movie,
                        aiChapterMarkers = aiChapterMarkers,
                        onPlayClick = onPlayClick,
                        getChapterImageUrl = getChapterImageUrl,
                    )

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

                    movieRelatedItem(
                        relatedItems = relatedItems,
                        getImageUrl = getImageUrl,
                        onRelatedMovieClick = onRelatedMovieClick,
                    )
                }

                MovieDetailTopBar(
                    isDownloaded = isDownloaded,
                    showMoreOptions = showMoreOptions,
                    onShowMoreOptionsChange = { showMoreOptions = it },
                    onBackClick = onBackClick,
                    onShareClick = { onShareClick(movie) },
                    onDeleteOfflineCopy = onDeleteOfflineCopy,
                    onDeleteRequested = { showDeleteDialog = true },
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }

        MovieDetailDialogs(
            movie = movie,
            showDownloadQualityDialog = showDownloadQualityDialog,
            onDownloadQualityDialogDismiss = { showDownloadQualityDialog = false },
            onQualitySelected = { quality ->
                onDownloadClick(movie, quality)
                showDownloadQualityDialog = false
            },
            downloadsViewModel = downloadsViewModel,
            showDeleteDialog = showDeleteDialog,
            onDeleteDialogDismiss = { showDeleteDialog = false },
            onDeleteConfirmed = {
                onDeleteClick(movie)
                showDeleteDialog = false
            },
        )
    }
}

/** AI-powered "Why You'll Love This" / AI Summary / content-warnings section. */
private fun LazyListScope.movieAiFeaturesItem(
    isLoadingWhyYoullLoveThis: Boolean,
    whyYoullLoveThis: String?,
    onGenerateWhyYoullLoveThis: () -> Unit,
    onGenerateAiSummary: () -> Unit,
    isLoadingAiSummary: Boolean,
    aiSummary: String?,
    isLoadingContentWarnings: Boolean,
    contentWarnings: List<String>,
) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isLoadingWhyYoullLoveThis || !whyYoullLoveThis.isNullOrBlank()) {
                    WhyYoullLoveThisCard(
                        pitch = whyYoullLoveThis,
                        isLoading = isLoadingWhyYoullLoveThis,
                    )
                } else {
                    TextButton(onClick = onGenerateWhyYoullLoveThis) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Why You'll Love This")
                    }
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

                if (isLoadingContentWarnings || contentWarnings.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Content Warnings",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Content Warnings",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (isLoadingContentWarnings) {
                            com.rpeters.jellyfin.ui.components.ExpressiveWavyLinearLoading(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                contentWarnings.forEach { warning ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(warning) },
                                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        border = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Synopsis, playback-capability breakdown and genre chips section. */
private fun LazyListScope.movieSynopsisItem(
    movie: BaseItemDto,
    playbackAnalysis: PlaybackCapabilityAnalysis?,
) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                var synopsisExpanded by remember(movie.id) { mutableStateOf(false) }
                var synopsisOverflowing by remember(movie.id) { mutableStateOf(false) }
                Text(
                    text = movie.overview ?: "No synopsis available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.4f),
                    textAlign = TextAlign.Center,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!synopsisExpanded) {
                            synopsisOverflowing = result.hasVisualOverflow
                        }
                    },
                )
                if (synopsisOverflowing || synopsisExpanded) {
                    TextButton(onClick = { synopsisExpanded = !synopsisExpanded }) {
                        Text(if (synopsisExpanded) "Less" else "More")
                    }
                }

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
}

/** Chapter list section, with an "AI generated" note when chapters were estimated. */
private fun LazyListScope.movieChaptersItem(
    movie: BaseItemDto,
    aiChapterMarkers: List<org.jellyfin.sdk.model.api.ChapterInfo>,
    onPlayClick: (BaseItemDto, Int?, Long?) -> Unit,
    getChapterImageUrl: (chapterIndex: Int, imageTag: String?) -> String?,
) {
    val movieChapters = movie.chapters ?: emptyList()
    val displayChapters = movieChapters.ifEmpty { aiChapterMarkers }
    if (displayChapters.isEmpty()) return

    item(key = "chapters") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column {
                if (movieChapters.isEmpty() && aiChapterMarkers.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Generated",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AI Generated Chapters",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = "This movie has no chapter markers from the server, " +
                                "so these approximate scene breaks were estimated from its runtime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                ChapterListSection(
                    chapters = displayChapters,
                    onChapterClick = { positionMs -> onPlayClick(movie, null, positionMs) },
                    getChapterImageUrl = { chapter, index ->
                        getChapterImageUrl(index, chapter.imageTag)
                    },
                )
            }
        }
    }
}

/** "More Like This" related-movies row. */
private fun LazyListScope.movieRelatedItem(
    relatedItems: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onRelatedMovieClick: (String) -> Unit,
) {
    if (relatedItems.isEmpty()) return

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
                            imageUrl = getImageUrl(relatedMovie).orEmpty(),
                            rating = relatedMovie.communityRating,
                            onCardClick = { onRelatedMovieClick(relatedMovie.id.toString()) },
                            cardSize = com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize.SMALL,
                        )
                    }
                }
            }
        }
    }
}

/** Floating back / more-options top bar overlaid on the collapsing backdrop. */
@Composable
private fun MovieDetailTopBar(
    isDownloaded: Boolean,
    showMoreOptions: Boolean,
    onShowMoreOptionsChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteOfflineCopy: () -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
                onClick = { onShowMoreOptionsChange(true) },
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
                onDismissRequest = { onShowMoreOptionsChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        onShareClick()
                        onShowMoreOptionsChange(false)
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
                            onShowMoreOptionsChange(false)
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete movie") },
                    onClick = {
                        onDeleteRequested()
                        onShowMoreOptionsChange(false)
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                    },
                )
            }
        }
    }
}

/** Download-quality picker and delete-confirmation dialogs for the movie detail screen. */
@Composable
private fun MovieDetailDialogs(
    movie: BaseItemDto,
    showDownloadQualityDialog: Boolean,
    onDownloadQualityDialogDismiss: () -> Unit,
    onQualitySelected: (com.rpeters.jellyfin.data.offline.VideoQuality) -> Unit,
    downloadsViewModel: DownloadsViewModel?,
    showDeleteDialog: Boolean,
    onDeleteDialogDismiss: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    if (showDownloadQualityDialog && downloadsViewModel != null) {
        QualitySelectionDialog(
            item = movie,
            onDismiss = onDownloadQualityDialogDismiss,
            onQualitySelected = onQualitySelected,
            downloadsViewModel = downloadsViewModel,
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDeleteDialogDismiss,
            title = { Text("Delete Movie") },
            text = { Text("Are you sure you want to delete ${movie.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = onDeleteConfirmed,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDialogDismiss) {
                    Text("Cancel")
                }
            },
        )
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
    onTrailerClick: (String) -> Unit,
    onShareClick: () -> Unit,
    onRequestClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val trailerUrl = movie.remoteTrailers?.firstOrNull()?.url

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val displayTrailerUrl = trailerUrl ?: "https://www.youtube.com/results?search_query=${Uri.encode("${movie.name.orEmpty()} trailer")}"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Play Button
            TextButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .weight(2f)
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

            // Trailer Button
            androidx.compose.material3.OutlinedButton(
                onClick = { onTrailerClick(displayTrailerUrl) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = if (trailerUrl.isNullOrBlank()) "Find Trailer" else "Trailer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
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

            if (trailerUrl.isNullOrBlank()) {
                ActionButton(
                    icon = Icons.Rounded.AddCircle,
                    label = "Request",
                    onClick = onRequestClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Density-pass tech-spec row: a single wrapping row of compact chips (replacing the previous
 * stacked VideoInfoCard/AudioInfoCard cards). The first chip reuses [PlaybackStatusBadge], which
 * already derives its label/color from the existing transcode-decision logic
 * ([playbackAnalysis]) - that decision is not reimplemented here.
 */
@Composable
private fun MovieTechSpecsSection(
    movie: BaseItemDto,
    playbackAnalysis: PlaybackCapabilityAnalysis?,
) {
    val mediaSource = movie.mediaSources?.firstOrNull()
    val videoStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.VIDEO }
    val audioStream = mediaSource?.mediaStreams?.find { it.type == MediaStreamType.AUDIO }
    val subtitles = mediaSource?.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Technical Specs",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (playbackAnalysis != null) {
                PlaybackStatusChip(playbackAnalysis)
            }
            VideoSpecChips(videoStream)
            AudioSpecChip(audioStream)
            SubtitleCountChip(subtitles.size)
        }
    }
}

/** Reuses the existing transcode-decision logic in [PlaybackStatusBadge]; only the call site moved. */
@Composable
private fun PlaybackStatusChip(playbackAnalysis: PlaybackCapabilityAnalysis) {
    PlaybackStatusBadge(analysis = playbackAnalysis)
}

/** Resolution + codec + HDR chips derived from the movie's primary video stream. */
@Composable
private fun VideoSpecChips(videoStream: org.jellyfin.sdk.model.api.MediaStream?) {
    val stream = videoStream ?: return

    val resolution = ResolutionQuality.fromResolution(stream.width, stream.height)
    val codecText = when (stream.codec?.lowercase()) {
        "hevc", "h265" -> "HEVC"
        "h264", "avc" -> "AVC"
        "av1" -> "AV1"
        "vp9" -> "VP9"
        else -> stream.codec?.uppercase().orEmpty()
    }
    val hdrType = HdrType.detect(
        stream.videoRange.toString(),
        stream.videoRangeType.toString(),
    )

    AssistChip(onClick = {}, enabled = false, label = { Text(resolution.name) })
    if (codecText.isNotBlank()) {
        AssistChip(onClick = {}, enabled = false, label = { Text(codecText) })
    }
    if (hdrType != null) {
        AssistChip(onClick = {}, enabled = false, label = { Text(hdrType.name) })
    }
}

/** Builds the audio-channel/codec/Atmos label chip from the movie's primary audio stream. */
private fun audioChannelText(channels: Int?): String = when (channels) {
    8 -> "7.1"
    6 -> "5.1"
    2 -> "Stereo"
    1 -> "Mono"
    else -> channels?.toString()?.let { "$it.0" }.orEmpty()
}

private fun audioCodecText(codec: String?): String = when (codec?.lowercase()) {
    "truehd" -> "TrueHD"
    "eac3" -> "DD+"
    "aac" -> "AAC"
    "ac3" -> "DD"
    "dca", "dts" -> "DTS"
    "dtshd" -> "DTS-HD"
    "flac" -> "FLAC"
    else -> codec?.uppercase().orEmpty()
}

@Composable
private fun AudioSpecChip(audioStream: org.jellyfin.sdk.model.api.MediaStream?) {
    val stream = audioStream ?: return

    val channelText = audioChannelText(stream.channels)
    val codecText = audioCodecText(stream.codec)
    val isAtmos = stream.title?.contains("atmos", ignoreCase = true) == true ||
        stream.codec?.contains("atmos", ignoreCase = true) == true

    val audioLabel = buildString {
        if (channelText.isNotBlank()) append(channelText)
        if (codecText.isNotBlank()) {
            if (isNotEmpty()) append(" ")
            append(codecText)
        }
        if (isAtmos) {
            if (isNotEmpty()) append(" ")
            append("Atmos")
        }
    }
    if (audioLabel.isNotBlank()) {
        AssistChip(onClick = {}, enabled = false, label = { Text(audioLabel) })
    }
}

@Composable
private fun SubtitleCountChip(subtitleCount: Int) {
    if (subtitleCount > 0) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("$subtitleCount subtitle${if (subtitleCount == 1) "" else "s"}") },
        )
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
            onTrailerClick = {},
            onShareClick = {},
            onRequestClick = {},
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
            getPersonImageUrl = { _ -> null },
            serverUrl = "http://localhost:8096",
            onGenerateAiSummary = {},
            onTrailerClick = {},
        )
    }
}
