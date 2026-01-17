package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.MotionTokens

/**
 * Material 3 Expressive Media Card for displaying movies, shows, music, etc.
 *
 * @param title The title to display
 * @param subtitle Optional subtitle text
 * @param imageUrl URL of the image to display
 * @param rating Optional rating value (e.g., 8.5)
 * @param isFavorite Whether the item is marked as favorite
 * @param isWatched Whether the item has been watched (shows checkmark badge)
 * @param watchProgress Progress value from 0.0 to 1.0 (shows progress bar)
 * @param unwatchedEpisodeCount For series - shows count badge if > 0
 * @param onCardClick Click handler for the card
 * @param onPlayClick Click handler for play action
 * @param onFavoriteClick Click handler for favorite action
 * @param onMoreClick Click handler for more actions
 * @param modifier Optional modifier
 * @param cardType Card style variant
 */
@Composable
fun ExpressiveMediaCard(
    title: String,
    subtitle: String = "",
    imageUrl: String,
    rating: Float? = null,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    watchProgress: Float = 0f,
    unwatchedEpisodeCount: Int? = null,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    cardType: ExpressiveCardType = ExpressiveCardType.ELEVATED,
) {
    var isPressed by remember { mutableStateOf(false) }
    // Simplify animations to improve performance
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "card_scale",
    )

    val cardModifier = modifier
        .width(200.dp)
        .height(320.dp)
        .scale(scale)

    when (cardType) {
        ExpressiveCardType.ELEVATED -> {
            ElevatedCard(
                modifier = cardModifier,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                MediaCardContent(
                    title = title,
                    subtitle = subtitle,
                    imageUrl = imageUrl,
                    rating = rating,
                    isFavorite = isFavorite,
                    isWatched = isWatched,
                    watchProgress = watchProgress,
                    unwatchedEpisodeCount = unwatchedEpisodeCount,
                    onCardClick = onCardClick,
                    onPlayClick = onPlayClick,
                    onFavoriteClick = onFavoriteClick,
                    onMoreClick = onMoreClick,
                    onPressedChange = { isPressed = it },
                )
            }
        }
        ExpressiveCardType.FILLED -> {
            // Filled-style card with stronger container color for emphasis
            Card(
                modifier = cardModifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                MediaCardContent(
                    title = title,
                    subtitle = subtitle,
                    imageUrl = imageUrl,
                    rating = rating,
                    isFavorite = isFavorite,
                    isWatched = isWatched,
                    watchProgress = watchProgress,
                    unwatchedEpisodeCount = unwatchedEpisodeCount,
                    onCardClick = onCardClick,
                    onPlayClick = onPlayClick,
                    onFavoriteClick = onFavoriteClick,
                    onMoreClick = onMoreClick,
                    onPressedChange = { isPressed = it },
                )
            }
        }
        ExpressiveCardType.OUTLINED -> {
            OutlinedCard(
                modifier = cardModifier,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    width = 1.dp,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                MediaCardContent(
                    title = title,
                    subtitle = subtitle,
                    imageUrl = imageUrl,
                    rating = rating,
                    isFavorite = isFavorite,
                    isWatched = isWatched,
                    watchProgress = watchProgress,
                    unwatchedEpisodeCount = unwatchedEpisodeCount,
                    onCardClick = onCardClick,
                    onPlayClick = onPlayClick,
                    onFavoriteClick = onFavoriteClick,
                    onMoreClick = onMoreClick,
                    onPressedChange = { isPressed = it },
                )
            }
        }
    }
}

@Composable
private fun MediaCardContent(
    title: String,
    subtitle: String,
    imageUrl: String,
    rating: Float?,
    isFavorite: Boolean,
    isWatched: Boolean,
    watchProgress: Float,
    unwatchedEpisodeCount: Int?,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChange(isPressed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Open $title",
            ) {
                onCardClick()
            },
    ) {
        // Image with overlay controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            OptimizedImage(
                imageUrl = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                size = ImageSize.POSTER,
                quality = ImageQuality.MEDIUM,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            )

            // Gradient overlay for better text visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
                                Color.Transparent,
                            ),
                            startY = 120f,
                        ),
                    ),
            )

            // Top action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Rating or watch status badge
                if (rating != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = rating.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                } else if (unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                    // Unwatched episode count badge for series
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        val countText = if (unwatchedEpisodeCount > 99) "99+" else unwatchedEpisodeCount.toString()
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                } else if (isWatched) {
                    // Watched checkmark badge
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Watched",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                        )
                    }
                }

                // More actions
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Watch progress bar at bottom of image
            if (watchProgress > 0f && watchProgress < 1f) {
                LinearProgressIndicator(
                    progress = { watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    strokeCap = StrokeCap.Round,
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Bottom actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                val favoriteColor by animateColorAsState(
                    targetValue = if (isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "favorite_color",
                )

                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = favoriteColor,
                    )
                }
            }
        }
    }
}

/**
 * Compact expressive card for lists and grids
 *
 * @param title The title to display
 * @param subtitle Optional subtitle text
 * @param imageUrl URL of the image to display
 * @param onClick Click handler for the card
 * @param onLongClick Optional long click handler (for action menus)
 * @param leadingIcon Optional leading icon
 * @param trailingContent Optional trailing content slot
 * @param modifier Optional modifier
 */
@Composable
fun ExpressiveCompactCard(
    title: String,
    subtitle: String = "",
    imageUrl: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable { onClick() }
                },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Image
            OptimizedImage(
                imageUrl = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                size = ImageSize.THUMBNAIL,
                quality = ImageQuality.MEDIUM,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // Leading icon
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                )
            }

            // Trailing content
            trailingContent?.invoke()
        }
    }
}

enum class ExpressiveCardType {
    ELEVATED,
    FILLED,
    OUTLINED,
}
