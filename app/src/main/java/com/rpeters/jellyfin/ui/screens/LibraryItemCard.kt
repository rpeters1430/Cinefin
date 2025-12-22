package com.rpeters.jellyfin.ui.screens

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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.ShimmerBox
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
    onItemLongPress: ((BaseItemDto) -> Unit)? = null,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (libraryType == LibraryType.TV_SHOWS && item.type == BaseItemKind.SERIES) {
                    val seriesId = item.id?.toString()
                    if (seriesId != null) {
                        onTVShowClick?.invoke(seriesId) ?: onItemClick(item)
                    } else {
                        onItemClick(item)
                    }
                } else {
                    onItemClick(item)
                }
            },
            onLongClick = { onItemLongPress?.invoke(item) },
        )
        .then(if (isCompact) Modifier.width(LibraryScreenDefaults.CompactCardWidth) else Modifier)

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
                        loading = {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(LibraryScreenDefaults.CompactCardImageHeight),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                    topStart = LibraryScreenDefaults.CardCornerRadius,
                                    topEnd = LibraryScreenDefaults.CardCornerRadius,
                                ),
                            )
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(LibraryScreenDefaults.CompactCardImageHeight)
                                    .clip(
                                        androidx.compose.foundation.shape.RoundedCornerShape(
                                            topStart = LibraryScreenDefaults.CardCornerRadius,
                                            topEnd = LibraryScreenDefaults.CardCornerRadius,
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = libraryType.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(LibraryScreenDefaults.CardActionIconSize),
                                    tint = libraryType.color.copy(alpha = LibraryScreenDefaults.IconAlpha),
                                )
                            }
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LibraryScreenDefaults.CompactCardImageHeight)
                            .clip(
                                androidx.compose.foundation.shape.RoundedCornerShape(
                                    topStart = LibraryScreenDefaults.CardCornerRadius,
                                    topEnd = LibraryScreenDefaults.CardCornerRadius,
                                ),
                            ),
                    )

                    if (item.userData?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(LibraryScreenDefaults.FavoriteIconPadding),
                        )
                    }
                }

                Column(modifier = Modifier.padding(LibraryScreenDefaults.CompactCardPadding)) {
                    Text(
                        text = item.name ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(LibraryScreenDefaults.ListCardPadding),
                horizontalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ItemSpacing),
            ) {
                Box {
                    SubcomposeAsyncImage(
                        model = getImageUrl(item),
                        contentDescription = item.name,
                        loading = {
                            ShimmerBox(
                                modifier = Modifier
                                    .width(LibraryScreenDefaults.ListCardImageWidth)
                                    .height(LibraryScreenDefaults.ListCardImageHeight),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(LibraryScreenDefaults.ListCardImageRadius),
                            )
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .width(LibraryScreenDefaults.ListCardImageWidth)
                                    .height(LibraryScreenDefaults.ListCardImageHeight)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(LibraryScreenDefaults.ListCardImageRadius)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = libraryType.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(LibraryScreenDefaults.ListCardIconSize),
                                    tint = libraryType.color.copy(alpha = LibraryScreenDefaults.IconAlpha),
                                )
                            }
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(LibraryScreenDefaults.ListCardImageWidth)
                            .height(LibraryScreenDefaults.ListCardImageHeight)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(LibraryScreenDefaults.ListCardImageRadius)),
                    )

                    if (item.userData?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(LibraryScreenDefaults.ListItemFavoriteIconPadding),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ListItemFavoriteIconPadding),
                ) {
                    Text(
                        text = item.name ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item.overview?.let { overview ->
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(LibraryScreenDefaults.FilterChipSpacing))

                    when (libraryType) {
                        LibraryType.MOVIES -> {
                            item.runTimeTicks?.let { runtime ->
                                val minutes = (runtime / LibraryScreenDefaults.TicksToMinutesDivisor).toInt()
                                Text(
                                    text = "$minutes min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = libraryType.color,
                                )
                            }
                        }
                        LibraryType.TV_SHOWS -> {
                            if (item.type == BaseItemKind.SERIES) {
                                item.childCount?.let { count ->
                                    Text(
                                        text = "$count episodes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = libraryType.color,
                                    )
                                }
                            }
                        }
                        LibraryType.MUSIC -> {
                            item.artists?.firstOrNull()?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = libraryType.color,
                                )
                            }
                        }
                        LibraryType.STUFF -> {
                            item.type?.let { type ->
                                Text(
                                    text = type.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = libraryType.color,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
