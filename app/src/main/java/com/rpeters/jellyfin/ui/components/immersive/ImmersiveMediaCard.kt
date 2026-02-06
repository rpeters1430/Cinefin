package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.MotionTokens

/**
 * Immersive media card with larger imagery and overlaid text for cinematic experiences.
 * Based on ExpressiveMediaCard but optimized for full-bleed immersive layouts.
 *
 * Key differences from ExpressiveMediaCard:
 * - Larger default size (280dp width vs 200dp)
 * - Full-bleed image with gradient overlay
 * - Text overlaid on image (not in separate section)
 * - Minimal chrome, more focus on imagery
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
 * @param modifier Optional modifier
 * @param cardSize Size variant for the card
 */
@Composable
fun ImmersiveMediaCard(
    title: String,
    imageUrl: String,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    rating: Float? = null,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    watchProgress: Float = 0f,
    unwatchedEpisodeCount: Int? = null,
    onPlayClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    cardSize: ImmersiveCardSize = ImmersiveCardSize.MEDIUM,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "immersive_card_scale",
    )

    val (width, height) = when (cardSize) {
        ImmersiveCardSize.SMALL -> ImmersiveDimens.CardWidthSmall to ImmersiveDimens.CardHeightSmall
        ImmersiveCardSize.MEDIUM -> ImmersiveDimens.CardWidthMedium to ImmersiveDimens.CardHeightMedium
        ImmersiveCardSize.LARGE -> ImmersiveDimens.CardWidthLarge to ImmersiveDimens.CardHeightLarge
    }

    Card(
        modifier = modifier
            .width(width)
            .height(height)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic),
    ) {
        ImmersiveCardContent(
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
            onPressedChange = { isPressed = it },
        )
    }
}

@Composable
private fun ImmersiveCardContent(
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
    onPressedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChange(isPressed)
    }

    Box(
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
        // Full-bleed background image
        OptimizedImage(
            imageUrl = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            size = ImageSize.BANNER,
            quality = ImageQuality.HIGH,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(ImmersiveDimens.CornerRadiusCinematic)),
        )

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.9f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    ),
                ),
        )

        // Top badges row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Rating or watch status badge
            if (rating != null) {
                Surface(
                    shape = RoundedCornerShape(ImmersiveDimens.CornerRadiusCard),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700), // Gold color
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = String.format(java.util.Locale.ROOT, "%.1f", rating),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            } else if (unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    val countText = if (unwatchedEpisodeCount > 99) "99+" else unwatchedEpisodeCount.toString()
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            } else if (isWatched) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(6.dp),
                    )
                }
            }

            // Favorite indicator
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Watch progress bar
        if (watchProgress > 0f && watchProgress < 1f) {
            LinearProgressIndicator(
                progress = { watchProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round,
            )
        }

        // Bottom content overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Play button
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Favorite button
                val favoriteColor by animateColorAsState(
                    targetValue = if (isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.White.copy(alpha = 0.7f)
                    },
                    label = "favorite_color",
                )

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(32.dp),
                ) {
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

enum class ImmersiveCardSize {
    SMALL,
    MEDIUM,
    LARGE
}
