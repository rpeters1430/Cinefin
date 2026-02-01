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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.ShimmerBox
import com.rpeters.jellyfin.ui.components.MaterialText
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
    onItemLongPress: ((BaseItemDto) -> Unit)? = null,
    onMoreClick: ((BaseItemDto) -> Unit)? = null,
    isCompact: Boolean,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Use adaptive card dimensions
    val cardWidth = when {
        isCompact && isTablet -> LibraryScreenDefaults.TabletCompactCardWidth
        isCompact -> LibraryScreenDefaults.CompactCardWidth
        else -> null
    }
    val cardImageHeight = when {
        isCompact && isTablet -> LibraryScreenDefaults.TabletCompactCardImageHeight
        isCompact -> LibraryScreenDefaults.CompactCardImageHeight
        else -> LibraryScreenDefaults.CompactCardImageHeight
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (libraryType == LibraryType.TV_SHOWS && item.type == BaseItemKind.SERIES) {
                    val seriesId = item.id.toString()
                    onTVShowClick?.invoke(seriesId) ?: onItemClick(item)
                } else {
                    onItemClick(item)
                }
            },
            onLongClick = { onItemLongPress?.invoke(item) },
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
                        loading = {
                            ShimmerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cardImageHeight),
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
                                    .height(cardImageHeight)
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
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardImageHeight)
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

                    // Top-right: Three-dot menu
                    if (onMoreClick != null) {
                        IconButton(
                            onClick = { onMoreClick(item) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(LibraryScreenDefaults.FavoriteIconPadding)
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    CircleShape,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Bottom-left: Watched indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(LibraryScreenDefaults.FavoriteIconPadding),
                    ) {
                        WatchedIndicatorBadge(item = item)
                    }

                    // Bottom: Watch progress bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = LibraryScreenDefaults.FavoriteIconPadding, vertical = LibraryScreenDefaults.FavoriteIconPadding),
                    ) {
                        WatchProgressBar(item = item)
                    }
                }

                Column(modifier = Modifier.padding(LibraryScreenDefaults.CompactCardPadding)) {
                    MaterialText(
                        text = item.name ?: stringResource(id = R.string.unknown),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = true,
                        minFontSize = 12.sp,
                        maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
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
                        contentScale = ContentScale.Fit,
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

                    // Top-right: Three-dot menu
                    if (onMoreClick != null) {
                        IconButton(
                            onClick = { onMoreClick(item) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(LibraryScreenDefaults.ListItemFavoriteIconPadding)
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    CircleShape,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Bottom-left: Watched indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(LibraryScreenDefaults.ListItemFavoriteIconPadding),
                    ) {
                        WatchedIndicatorBadge(item = item)
                    }

                    // Bottom: Watch progress bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = LibraryScreenDefaults.ListItemFavoriteIconPadding, vertical = LibraryScreenDefaults.ListItemFavoriteIconPadding),
                    ) {
                        WatchProgressBar(item = item)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LibraryScreenDefaults.ListItemFavoriteIconPadding),
                ) {
                    MaterialText(
                        text = item.name ?: stringResource(id = R.string.unknown),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = true,
                        minFontSize = 16.sp,
                        maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
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
                            Text(
                                text = item.type.toString(),
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
