package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Media card wrapper built from official Material 3 card primitives.
 *
 * The expressive treatment here comes from the app's styling, motion, and layout choices layered
 * on top of standard `Card` / `ElevatedCard` / `OutlinedCard` components.
 */
@Composable
fun ExpressiveMediaCard(
    title: String,
    subtitle: String = "",
    imageUrl: String,
    rating: Float? = null,
    isFavorite: Boolean = false, // Restored for compatibility
    isWatched: Boolean = false,
    watchProgress: Float = 0f,
    unwatchedEpisodeCount: Int? = null,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {}, // Restored for compatibility
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

    val glowColor = if (cardType == ExpressiveCardType.ELEVATED) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        Color.Black.copy(alpha = 0.2f)
    }

    val cardModifier = modifier
        .width(200.dp)
        .height(320.dp)
        .scale(scale)
        .primaryExpressiveGlow(
            color = glowColor,
            alpha = if (cardType == ExpressiveCardType.ELEVATED) 0.12f else 0.08f,
            borderRadius = 24.dp, // Matching large shape token
        )

    when (cardType) {
        ExpressiveCardType.ELEVATED -> {
            ElevatedCard(
                modifier = cardModifier,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp,
                    hoveredElevation = 12.dp,
                ),
                shape = MaterialTheme.shapes.large,
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
                shape = MaterialTheme.shapes.large,
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
                shape = MaterialTheme.shapes.large,
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
    onMoreClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChange(isPressed)
    }

    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Open $title",
            ) {
                haptics.lightClick()
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
                        shape = MaterialTheme.shapes.medium,
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
                                text = String.format(java.util.Locale.ROOT, "%.1f", rating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                } else if (unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                    // Unwatched episode count badge for series
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        val countText = if (unwatchedEpisodeCount > 99) "99+" else unwatchedEpisodeCount.toString()
                        Box(
                            modifier = Modifier.defaultMinSize(minWidth = 28.dp, minHeight = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = countText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
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
                androidx.compose.material3.LinearWavyProgressIndicator(
                    progress = { watchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    amplitude = { 0.12f },
                    wavelength = 32.dp,
                    waveSpeed = 0.dp, // Static for cards to save battery/performance
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
        ) {
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
    }
}

/**
 * Compact card wrapper for lists and grids built from official Material 3 `Card`.
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
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon (shown before the thumbnail image)
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp),
                )
            }

            // Image
            OptimizedImage(
                imageUrl = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                size = ImageSize.THUMBNAIL,
                quality = ImageQuality.MEDIUM,
                modifier = Modifier
                    .size(60.dp)
                    .clip(MaterialTheme.shapes.small),
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

            // Trailing content
            trailingContent?.invoke()
        }
    }
}

/**
 * Generic content card wrapper built from official Material 3 `ElevatedCard`.
 */
@Composable
fun ExpressiveContentCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    elevation: androidx.compose.material3.CardElevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "content_card_scale",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isInteractionPressed) {
        isPressed = isInteractionPressed
    }

    ElevatedCard(
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = elevation,
        shape = shape,
        content = content,
    )
}

enum class ExpressiveCardType {
    ELEVATED,
    FILLED,
    OUTLINED,
}
