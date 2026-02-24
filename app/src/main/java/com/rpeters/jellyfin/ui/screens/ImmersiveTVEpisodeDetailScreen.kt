package com.rpeters.jellyfin.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.repeatOnLifecycle
import coil3.request.crossfade
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.PerformanceOptimizedLazyRow
import com.rpeters.jellyfin.ui.components.PlaybackStatusBadge
import com.rpeters.jellyfin.ui.components.QualitySelectionDialog
import com.rpeters.jellyfin.ui.components.WatchStatusBanner
import com.rpeters.jellyfin.ui.components.immersive.AudioInfoCard
import com.rpeters.jellyfin.ui.components.immersive.HdrType
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard
import com.rpeters.jellyfin.ui.components.immersive.OverlayGradientScrim
import com.rpeters.jellyfin.ui.components.immersive.ResolutionQuality
import com.rpeters.jellyfin.ui.components.immersive.StaticHeroSection
import com.rpeters.jellyfin.ui.components.immersive.VideoInfoCard
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.SeriesBlue
import com.rpeters.jellyfin.ui.utils.PlaybackCapabilityAnalysis
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.isWatched
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale

/**
 * Immersive TV Episode Detail screen.
 * Cinematic view for individual episodes with parallax hero and season context.
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveTVEpisodeDetailScreen(
    episode: BaseItemDto,
    seriesInfo: BaseItemDto? = null,
    seasonEpisodes: List<BaseItemDto> = emptyList(),
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onEpisodeClick: (BaseItemDto) -> Unit = {},
    onViewSeriesClick: () -> Unit = {},
    onPlayClick: (BaseItemDto, Int?, Long?) -> Unit = { _, _, _ -> },
    onDownloadClick: (BaseItemDto, com.rpeters.jellyfin.data.offline.VideoQuality) -> Unit = { _, _ -> },
    onDeleteClick: (BaseItemDto) -> Unit = {},
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onMarkWatchedClick: (BaseItemDto) -> Unit = {},
    onRefresh: () -> Unit = {},
    onPersonClick: (String, String) -> Unit = { _, _ -> },
    playbackAnalysis: PlaybackCapabilityAnalysis? = null,
    playbackProgress: com.rpeters.jellyfin.ui.player.PlaybackProgress? = null,
    onGenerateAiSummary: () -> Unit = {},
    aiSummary: String? = null,
    isLoadingAiSummary: Boolean = false,
    isDownloaded: Boolean = false,
    isOffline: Boolean = false,
    onDeleteOfflineCopy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val perfConfig = rememberImmersivePerformanceConfig()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()
    val listState = remember(episode.id.toString()) { LazyListState() }
    val context = LocalContext.current
    val mainAppViewModel: MainAppViewModel = hiltViewModel()
    var showQualityDialog by remember { mutableStateOf(false) }
    var showDeleteOfflineConfirmation by remember { mutableStateOf(false) }
    var pendingQuality by remember { mutableStateOf<com.rpeters.jellyfin.data.offline.VideoQuality?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        SecureLogger.i(
            "DownloadsFlow",
            "POST_NOTIFICATIONS result via ImmersiveEpisodeDetail: granted=$granted, itemId=${episode.id}",
        )
        val quality = pendingQuality
        if (quality != null) {
            SecureLogger.i(
                "DownloadsFlow",
                "Proceeding with download after permission result via ImmersiveEpisodeDetail: itemId=${episode.id}, quality=${quality.id}",
            )
            onDownloadClick(episode, quality)
            pendingQuality = null
        }
    }

    fun requestPermissionAndDownload(quality: com.rpeters.jellyfin.data.offline.VideoQuality) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            SecureLogger.i(
                "DownloadsFlow",
                "Requesting POST_NOTIFICATIONS via ImmersiveEpisodeDetail: itemId=${episode.id}, quality=${quality.id}",
            )
            pendingQuality = quality
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SecureLogger.i(
                "DownloadsFlow",
                "POST_NOTIFICATIONS already granted (or not required) via ImmersiveEpisodeDetail: itemId=${episode.id}, quality=${quality.id}",
            )
            onDownloadClick(episode, quality)
        }
    }

    if (showQualityDialog) {
        QualitySelectionDialog(
            item = episode,
            onDismiss = { showQualityDialog = false },
            onQualitySelected = { quality ->
                requestPermissionAndDownload(quality)
                showQualityDialog = false
            },
            downloadsViewModel = downloadsViewModel,
        )
    }

    if (showDeleteOfflineConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteOfflineConfirmation = false },
            title = { Text("Remove offline copy?") },
            text = { Text("This only removes the local downloaded file from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteOfflineConfirmation = false
                    onDeleteOfflineCopy()
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteOfflineConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    // ✅ Fix: Scroll to top when episode changes
    LaunchedEffect(episode.id) {
        listState.scrollToItem(0)
    }

    // Refresh when screen is resumed to catch latest playback status
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            onRefresh()
        }
    }

    val heroImage = getBackdropUrl(episode).takeIf { !it.isNullOrBlank() }
        ?: seriesInfo?.let { getBackdropUrl(it) }
        ?: getImageUrl(episode).orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        // Static Hero Background (Fixed - doesn't scroll) - Extended to edges
        StaticHeroSection(
            imageUrl = heroImage.takeIf { it.isNotBlank() },
            height = ImmersiveDimens.HeroHeightPhone,
            modifier = Modifier
                .fillMaxWidth(),
            contentScale = ContentScale.Crop,
            content = {}, // Content moved to LazyColumn
        )

        // Scrollable Content Layer
        PullToRefreshBox(
            isRefreshing = false, // Placeholder, can be tied to a state if needed
            onRefresh = {},
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                // 1. Hero Content (overlays hero initially, scrolls away)
                item(key = "hero_content") {
                    EpisodeHeroContent(
                        episode = episode,
                        seriesInfo = seriesInfo,
                        onPlayClick = {
                            val resumePos = playbackProgress?.positionMs
                            onPlayClick(episode, null, resumePos)
                        },
                    )
                }

                // Background spacer to cover hero when scrolled
                item(key = "background_spacer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.background),
                    )
                }

                item(key = "watch_status") {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        WatchStatusBanner(
                            item = episode,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // Live Playback Progress Indicator
                playbackProgress?.let { progress ->
                    item(key = "playback_progress") {
                        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                            com.rpeters.jellyfin.ui.components.PlaybackProgressIndicator(
                                progress = progress,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // 2. Overview & AI Summary
                item(key = "overview") {
                    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        EpisodeOverviewSection(
                            episode = episode,
                            onGenerateAiSummary = onGenerateAiSummary,
                            aiSummary = aiSummary,
                            isLoadingAiSummary = isLoadingAiSummary,
                            playbackAnalysis = playbackAnalysis,
                        )
                    }
                }

                // 3. Quick Actions
                item(key = "actions") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isDownloaded) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Downloaded") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    },
                                )
                            }
                            if (isOffline) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Offline") },
                                    leadingIcon = {
                                        Icon(Icons.Default.WifiOff, contentDescription = null)
                                    },
                                )
                            }
                        }

                        if (isDownloaded) {
                            TextButton(
                                onClick = { showDeleteOfflineConfirmation = true },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("Delete offline copy")
                            }
                        }

                        EpisodeActionRow(
                            episode = episode,
                            onFavoriteClick = { onFavoriteClick(episode) },
                            onMarkWatchedClick = { onMarkWatchedClick(episode) },
                            onDownloadClick = { showQualityDialog = true },
                            onDeleteClick = { onDeleteClick(episode) },
                            onShareClick = {
                                com.rpeters.jellyfin.ui.utils.ShareUtils.shareMedia(context, episode)
                            },
                        )
                    }
                }

                // 4. Season Context / Navigation
                if (seasonEpisodes.isNotEmpty()) {
                    item(key = "season_title") {
                        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                            Text(
                                text = "More From This Season",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                        }
                    }

                    item(key = "season_episodes") {
                        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                            PerformanceOptimizedLazyRow(
                                items = seasonEpisodes,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                maxVisibleItems = perfConfig.maxRowItems,
                            ) { item, _, _ ->
                                val isCurrent = item.id == episode.id
                                ImmersiveMediaCard(
                                    title = item.name ?: "Episode ${item.indexNumber}",
                                    subtitle = "Episode ${item.indexNumber}",
                                    imageUrl = getBackdropUrl(item) ?: getImageUrl(item) ?: "",
                                    onCardClick = { if (!isCurrent) onEpisodeClick(item) },
                                    cardSize = ImmersiveCardSize.MEDIUM,
                                    isWatched = item.isWatched(),
                                    modifier = Modifier.width(280.dp).height(160.dp).then(
                                        if (isCurrent) {
                                            Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        } else {
                                            Modifier
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }

                // 4.5 Cast & Crew
                episode.people?.takeIf { it.isNotEmpty() }?.let { people ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                            ImmersiveCastSection(
                                people = people,
                                getImageUrl = getImageUrl,
                                onPersonClick = onPersonClick,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    }
                }

                // 5. Series Info Card
                seriesInfo?.let { series ->
                    item(key = "series_info") {
                        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Series Info",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                                SeriesInfoCard(
                                    series = series,
                                    getImageUrl = getImageUrl,
                                    onClick = onViewSeriesClick,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Back Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .zIndex(10f),
        ) {
            Surface(
                onClick = onBackClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeHeroContent(
    episode: BaseItemDto,
    seriesInfo: BaseItemDto?,
    onPlayClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ImmersiveDimens.HeroHeightPhone),
    ) {
        // Gradient overlay for text readability
        OverlayGradientScrim(
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Series Title Context
            seriesInfo?.name?.let { seriesName ->
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            // Episode Title
            Text(
                text = episode.name ?: "Unknown Episode",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            // Metadata Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // S# E# Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SeriesBlue.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = "S${episode.parentIndexNumber} E${episode.indexNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Rating
                episode.communityRating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                        Text(
                            text = String.format(Locale.ROOT, "%.1f", rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Runtime
                episode.runTimeTicks?.let { ticks ->
                    val minutes = (ticks / 10_000_000 / 60).toInt()
                    Text(
                        text = "${minutes}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            // Play Button
            ExpressiveFilledButton(
                onClick = onPlayClick,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Play Episode")
            }
        }
    }
}

@Composable
private fun EpisodeOverviewSection(
    episode: BaseItemDto,
    onGenerateAiSummary: () -> Unit,
    aiSummary: String?,
    isLoadingAiSummary: Boolean,
    playbackAnalysis: PlaybackCapabilityAnalysis?,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, // ✅ Centered horizontally
    ) {
        // Synopsis Header with AI Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Synopsis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            TextButton(onClick = onGenerateAiSummary, enabled = !isLoadingAiSummary) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (aiSummary != null) "AI Summary" else "Generate AI Summary")
            }
        }

        // Overview Text
        episode.overview?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4,
                maxLines = 3, // ✅ Limit to 3 lines
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, // ✅ Centered
            )
        }

        // AI Summary Display
        if (aiSummary != null || isLoadingAiSummary) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            ) {
                if (isLoadingAiSummary) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        ExpressiveCircularLoading(size = 24.dp)
                    }
                } else {
                    Text(
                        text = aiSummary ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center, // ✅ Centered
                    )
                }
            }
        }

        // Playback Capability
        playbackAnalysis?.let { PlaybackStatusBadge(analysis = it) }

        // ✅ Enhanced Metadata Section
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Media Info Cards
                episode.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
                    val videoStream = streams.find { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
                    val audioStream = streams.find { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Video Info Card
                        videoStream?.let { stream ->
                            val resolution = ResolutionQuality.fromResolution(stream.width, stream.height)
                            val codecText = when (stream.codec?.lowercase()) {
                                "hevc", "h265" -> "HEVC"
                                "h264", "avc" -> "AVC"
                                "av1" -> "AV1"
                                "vp9" -> "VP9"
                                else -> stream.codec?.uppercase() ?: ""
                            }
                            val hdrType = HdrType.detect(
                                stream.videoRange.toString(),
                                stream.videoRangeType.toString(),
                            )

                            val codecIcon = if (codecText == "AVC") {
                                ImageVector.vectorResource(id = R.drawable.avc_24px)
                            } else {
                                null
                            }

                            VideoInfoCard(
                                resolution = resolution,
                                codec = codecText,
                                bitDepth = stream.bitDepth,
                                frameRate = stream.averageFrameRate?.toDouble(),
                                isHdr = hdrType != null,
                                hdrType = hdrType ?: HdrType.HDR,
                                codecIcon = codecIcon,
                            )
                        }

                        // Audio Info Card
                        audioStream?.let { stream ->
                            val channelText = when (stream.channels) {
                                8 -> "7.1"
                                6 -> "5.1"
                                2 -> "Stereo"
                                1 -> "Mono"
                                else -> stream.channels?.toString()?.let { "$it.0" } ?: ""
                            }

                            val codecText = when (stream.codec?.lowercase()) {
                                "truehd" -> "TrueHD"
                                "eac3" -> "DD+"
                                "aac" -> "AAC"
                                "ac3" -> "DD"
                                "dca", "dts" -> "DTS"
                                "dtshd" -> "DTS-HD"
                                "flac" -> "FLAC"
                                else -> stream.codec?.uppercase() ?: ""
                            }

                            val isAtmos = stream.title?.contains("atmos", ignoreCase = true) == true ||
                                stream.codec?.contains("atmos", ignoreCase = true) == true

                            AudioInfoCard(
                                channels = channelText,
                                codec = codecText,
                                isAtmos = isAtmos,
                                language = stream.language?.uppercase(),
                            )
                        }
                    }
                }

                // Air Date & Runtime (MM - DD - YYYY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    episode.premiereDate?.let { date ->
                        // Input format is typically 2024-01-23T00:00:00Z
                        val dateStr = date.toString().substringBefore('T')
                        val parts = dateStr.split("-")
                        if (parts.size == 3) {
                            val formattedDate = "${parts[1]} - ${parts[2]} - ${parts[0]}" // MM - DD - YYYY
                            DetailInfoRow(
                                label = "Aired",
                                value = formattedDate,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    episode.runTimeTicks?.let { ticks ->
                        val minutes = (ticks / 10_000_000 / 60).toInt()
                        DetailInfoRow(
                            label = "Duration",
                            value = "${minutes}m",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataTag(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun EpisodeActionRow(
    episode: BaseItemDto,
    onFavoriteClick: () -> Unit,
    onMarkWatchedClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val isFavorite = episode.userData?.isFavorite == true
        val isWatched = episode.userData?.played == true

        DetailActionButton(
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            label = if (isFavorite) "Liked" else "Favorite",
            onClick = onFavoriteClick,
            modifier = Modifier.weight(1f),
            active = isFavorite,
        )
        DetailActionButton(
            icon = if (isWatched) Icons.Default.CheckCircle else Icons.Default.VisibilityOff,
            label = if (isWatched) "Watched" else "Mark",
            onClick = onMarkWatchedClick,
            modifier = Modifier.weight(1f),
            active = isWatched,
        )
        DetailActionButton(
            icon = Icons.Default.Download,
            label = "Download",
            onClick = onDownloadClick,
            modifier = Modifier.weight(1f),
        )
        DetailActionButton(
            icon = Icons.Default.Delete,
            label = "Delete",
            onClick = onDeleteClick,
            modifier = Modifier.weight(1f),
        )
        DetailActionButton(
            icon = Icons.Default.Share,
            label = "Share",
            onClick = onShareClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SeriesInfoCard(
    series: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(width = 60.dp, height = 90.dp),
            ) {
                JellyfinAsyncImage(
                    model = getImageUrl(series),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name ?: "Series",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = series.productionYear?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                series.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun ImmersiveCastSection(
    people: List<org.jellyfin.sdk.model.api.BaseItemPerson>,
    getImageUrl: (BaseItemDto) -> String?,
    onPersonClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perfConfig = rememberImmersivePerformanceConfig()
    val cast = people.filter { it.type.toString() in listOf("actor", "gueststar") }.take(perfConfig.maxRowItems)
    if (cast.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "Cast",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cast, key = { it.id.toString() }) { person ->
                val personItem = BaseItemDto(
                    id = person.id,
                    type = org.jellyfin.sdk.model.api.BaseItemKind.PERSON,
                    imageTags = person.primaryImageTag?.let {
                        mapOf(org.jellyfin.sdk.model.api.ImageType.PRIMARY to it)
                    },
                )
                CastMemberCard(
                    person = person,
                    imageUrl = getImageUrl(personItem),
                    onPersonClick = onPersonClick,
                )
            }
        }
    }
}

@Composable
private fun CastMemberCard(
    person: org.jellyfin.sdk.model.api.BaseItemPerson,
    imageUrl: String?,
    onPersonClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable {
                onPersonClick(person.id.toString(), person.name ?: "Unknown")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Profile Image
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(120.dp),
        ) {
            if (imageUrl != null) {
                coil3.compose.SubcomposeAsyncImage(
                    model = coil3.request.ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = person.name,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }

        // Actor Name
        Text(
            text = person.name ?: "Unknown",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        // Role/Character
        person.role?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
