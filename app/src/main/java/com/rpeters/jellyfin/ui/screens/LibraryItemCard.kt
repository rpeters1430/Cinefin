package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.ShimmerBox
import com.rpeters.jellyfin.ui.components.UnwatchedEpisodeCountBadge
import com.rpeters.jellyfin.ui.components.WatchProgressBar
import com.rpeters.jellyfin.ui.components.WatchedIndicatorBadge
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** Card representation of a library item used in multiple views. */
@OptInAppExperimentalApis
@Composable
fun LibraryItemCard(
    item: BaseItemDto,
    libraryType: LibraryType,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit = {},
    onTVShowClick: ((String) -> Unit)? = null,
    onPlayClick: ((BaseItemDto) -> Unit)? = null,
    onDeleteClick: ((BaseItemDto) -> Unit)? = null,
    onItemLongPress: ((BaseItemDto) -> Unit)? = null,
    onMoreClick: ((BaseItemDto) -> Unit)? = null,
    isCompact: Boolean,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    // Use adaptive card dimensions
    val cardWidth = when {
        isCompact && isTablet -> LibraryScreenDefaults.TabletCompactCardWidth
        isCompact -> LibraryScreenDefaults.CompactCardWidth
        else -> null
    }

    val handleInfo = {
        if (libraryType == LibraryType.TV_SHOWS && item.type == BaseItemKind.SERIES) {
            val seriesId = item.id.toString()
            onTVShowClick?.invoke(seriesId) ?: onItemClick(item)
        } else {
            onItemClick(item)
        }
    }

    Box(modifier = modifier) {
        val cardModifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = handleInfo,
                onLongClick = {
                    showMenu = true
                    onItemLongPress?.invoke(item)
                },
            )
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)

        Card(
            modifier = cardModifier,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(LibraryScreenDefaults.CardCornerRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = LibraryScreenDefaults.CardElevation),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            if (isCompact) {
                Column {
                    Box {
                        SubcomposeAsyncImage(
                            model = getImageUrl(item),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    when {
                                        isTablet -> LibraryScreenDefaults.TabletCompactCardImageHeight
                                        else -> LibraryScreenDefaults.CompactCardImageHeight
                                    },
                                ),
                            loading = { ShimmerBox() },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(
                                            when {
                                                isTablet -> LibraryScreenDefaults.TabletCompactCardImageHeight
                                                else -> LibraryScreenDefaults.CompactCardImageHeight
                                            },
                                        )
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = libraryType.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                            },
                        )

                        // Indicators in top-right corner
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (item.userData?.isFavorite == true) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorite",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(20.dp),
                                )
                            }
                            UnwatchedEpisodeCountBadge(item = item)
                            WatchedIndicatorBadge(item = item)
                        }
                        WatchProgressBar(item = item, modifier = Modifier.align(Alignment.BottomCenter))
                    }

                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = item.name ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!item.productionYear.toString().isNullOrBlank() && item.productionYear != 0) {
                            Text(
                                text = item.productionYear.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                // List/Wide layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val imageHeight = if (isTablet) 100.dp else 80.dp
                    // Derive width from the server-reported aspect ratio so thumbnails
                    // (e.g. 16:9 YouTube thumbs) are displayed at their natural shape.
                    val imageWidth = item.primaryImageAspectRatio
                        ?.toFloat()
                        ?.let { imageHeight * it }
                        ?: imageHeight // fall back to square when ratio is unknown

                    SubcomposeAsyncImage(
                        model = getImageUrl(item),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(imageHeight)
                            .width(imageWidth)
                            .clip(MaterialTheme.shapes.small),
                        loading = { ShimmerBox() },
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!item.overview.isNullOrBlank()) {
                            Text(
                                text = item.overview ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(onClick = {
                        showMenu = true
                        onMoreClick?.invoke(item)
                    }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            }
        }

        // Quick Actions Popup Menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.width(180.dp),
        ) {
            DropdownMenuItem(
                text = { Text("Play") },
                onClick = {
                    showMenu = false
                    onPlayClick?.invoke(item)
                },
                leadingIcon = {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text("Info") },
                onClick = {
                    showMenu = false
                    handleInfo()
                },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
            )
            if (onDeleteClick != null) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDeleteClick(item)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    },
                )
            }
        }
    }
}
