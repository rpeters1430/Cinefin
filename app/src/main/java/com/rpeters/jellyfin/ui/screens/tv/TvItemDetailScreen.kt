package com.rpeters.jellyfin.ui.screens.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.image.rememberScreenWidthHeight
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.UserPreferencesViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.Locale
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
fun TvItemDetailScreen(
    itemId: String?,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    onPlay: (itemId: String, itemName: String, startPositionMs: Long) -> Unit,
) {
    val appState by viewModel.appState.collectAsState()
    val userPrefs: UserPreferencesViewModel = hiltViewModel()
    val context = LocalContext.current
    val playerVm: com.rpeters.jellyfin.ui.player.VideoPlayerViewModel = hiltViewModel()

    val item: BaseItemDto? = itemId?.let { id ->
        // Search common sources first; fall back to recently added and home videos
        appState.allMovies.firstOrNull { it.id?.toString() == id }
            ?: appState.allTVShows.firstOrNull { it.id?.toString() == id }
            ?: appState.recentlyAdded.firstOrNull { it.id?.toString() == id }
            ?: appState.itemsByLibrary.values.asSequence().flatten().firstOrNull { it.id?.toString() == id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(56.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TvText(
            text = when (item?.type) {
                BaseItemKind.EPISODE -> item.seriesName ?: (item.name ?: "Item")
                else -> item?.name ?: "Item"
            },
            style = TvMaterialTheme.typography.headlineLarge,
            color = TvMaterialTheme.colorScheme.onSurface,
        )

        // Subtitle for episodes (SxxEyy) or year/type
        val season = item?.parentIndexNumber
        val episode = item?.indexNumber
        val year = item?.productionYear
        val subtitle = when (item?.type) {
            BaseItemKind.EPISODE -> buildString {
                if (season != null && episode != null) append("S${season}E$episode")
                if (year != null) append("  •  $year")
            }
            else -> year?.toString() ?: ""
        }
        if (subtitle.isNotBlank()) {
            TvText(
                text = subtitle,
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Poster
            TvCard(
                onClick = {},
                modifier = Modifier.size(300.dp, 450.dp),
                colors = TvCardDefaults.colors(containerColor = TvMaterialTheme.colorScheme.surfaceVariant),
            ) {
                JellyfinAsyncImage(
                    data = item?.let { viewModel.getImageUrl(it) },
                    contentDescription = item?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = rememberCoilSize(300.dp, 450.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Quick metadata row: duration • content rating • community rating • progress
                val durationMs = ((item?.runTimeTicks ?: 0L) / 10_000L)
                val durationText = if (durationMs > 0) formatDuration(durationMs) else null
                val community = item?.communityRating?.let { String.format(Locale.ROOT, "%.1f★", it) }
                val official = item?.officialRating
                val progressPct = item?.userData?.playedPercentage?.let { p ->
                    if (p > 0.0) "${p.toInt()}% watched" else null
                }

                val metaPieces = listOfNotNull(durationText, official, community, progressPct)
                if (metaPieces.isNotEmpty()) {
                    TvText(
                        text = metaPieces.joinToString("  •  "),
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Genres row (if available)
                val genres = item?.genres?.takeIf { it.isNotEmpty() }?.joinToString(
                    separator = " • ",
                )
                if (!genres.isNullOrBlank()) {
                    TvText(
                        text = genres,
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Technical info (container / codecs)
                var techInfo by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(itemId) {
                    val id = itemId
                    if (!id.isNullOrBlank()) {
                        runCatching { playerVm.fetchPlaybackInfo(id) }
                            .onSuccess { info ->
                                val ms = info.mediaSources?.firstOrNull()
                                val container = ms?.container
                                val streams = ms?.mediaStreams
                                val v = streams?.firstOrNull { it.type?.toString()?.equals("Video", true) == true }
                                val a = streams?.firstOrNull { it.type?.toString()?.equals("Audio", true) == true }
                                val vCodec = v?.codec
                                val width = (v?.width as? Number)?.toInt()
                                val height = (v?.height as? Number)?.toInt()
                                val res = if (width != null && height != null) "${width}x$height" else null
                                val aCodec = a?.codec
                                val ch = (a?.channels as? Number)?.toInt()
                                val audioDesc = listOfNotNull(aCodec, ch?.let { "${it}ch" }).joinToString(" ")
                                val videoDesc = listOfNotNull(vCodec, res).joinToString(" ")
                                val parts = listOfNotNull(
                                    container?.uppercase(Locale.ROOT),
                                    if (videoDesc.isNotBlank()) "Video: $videoDesc" else null,
                                    if (audioDesc.isNotBlank()) "Audio: $audioDesc" else null,
                                )
                                techInfo = parts.joinToString("  •  ")
                            }
                    }
                }
                techInfo?.let { text ->
                    TvText(
                        text = text,
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Overview / metadata
                if (!item?.overview.isNullOrBlank()) {
                    TvText(
                        text = item?.overview ?: "",
                        style = TvMaterialTheme.typography.bodyLarge,
                        color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Play/Resume
                    val resumeMs = item?.userData?.playbackPositionTicks?.div(10_000) ?: 0L
                    val playLabel = if (resumeMs > 0) {
                        "Resume ${formatPosition(resumeMs)}"
                    } else {
                        "Play"
                    }
                    TvButton(onClick = {
                        val id = item?.id?.toString()
                        val title = item?.name ?: ""
                        if (!id.isNullOrBlank()) {
                            onPlay(id, title, resumeMs)
                        }
                    }) {
                        TvText(text = playLabel)
                    }

                    TvButton(onClick = {
                        item?.let {
                            val direct = viewModel.getDirectStreamUrl(it)
                            val url = direct ?: viewModel.getStreamUrl(it)
                            if (!url.isNullOrEmpty()) {
                                MediaPlayerUtils.playMedia(context, url, it)
                            }
                        }
                    }) { TvText(text = "Play (Direct)") }

                    // Toggle Favorite
                    TvButton(onClick = {
                        item?.let {
                            userPrefs.toggleFavorite(it) { ok, msg ->
                                val text = msg ?: if (ok) "Added to favorites" else "Removed from favorites"
                                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        val fav = item?.userData?.isFavorite == true
                        TvText(text = if (fav) "Unfavorite" else "Favorite")
                    }

                    // Mark watched/unwatched
                    TvButton(onClick = {
                        item?.let {
                            val watched = it.userData?.played == true
                            if (watched) {
                                userPrefs.markAsUnwatched(it) { ok, msg ->
                                    val text = msg ?: if (ok) "Marked as unwatched" else "Failed to update"
                                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                userPrefs.markAsWatched(it) { ok, msg ->
                                    val text = msg ?: if (ok) "Marked as watched" else "Failed to update"
                                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        val watched = item?.userData?.played == true
                        TvText(text = if (watched) "Mark Unwatched" else "Mark Watched")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Backdrop if available
        item?.let {
            val backdrop = viewModel.getBackdropUrl(it)
            if (backdrop != null) {
                TvCard(onClick = {}, modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    JellyfinAsyncImage(
                        data = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        requestSize = rememberScreenWidthHeight(220.dp),
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatPosition(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
