package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.components.expressiveGlow
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.ui.theme.ImmersiveShapes
import com.rpeters.jellyfin.ui.theme.MotionTokens

@Composable
fun ImmersiveMediaCard(
    title: String,
    imageUrl: String,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemId: String? = null,
    subtitle: String = "",
    rating: Float? = null,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    watchProgress: Float = 0f,
    unwatchedEpisodeCount: Int? = null,
    onPlayClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    loadImage: Boolean = true,
    imageQuality: ImageQuality = ImageQuality.HIGH,
    cardSize: ImmersiveCardSize = ImmersiveCardSize.MEDIUM,
) {
    val sharedTransitionScope = com.rpeters.jellyfin.ui.navigation.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.rpeters.jellyfin.ui.navigation.LocalAnimatedVisibilityScope.current

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = MotionTokens.expressiveEnter,
        label = "immersive_card_scale",
    )
    val density = LocalDensity.current
    val liftOffset by animateFloatAsState(
        targetValue = with(density) { if (isPressed) (-1).dp.toPx() else (-6).dp.toPx() },
        animationSpec = tween(durationMillis = 220),
        label = "immersive_card_lift",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.12f else 0.28f,
        animationSpec = tween(durationMillis = 220),
        label = "immersive_card_glow_alpha",
    )

    val (width, height) = when (cardSize) {
        ImmersiveCardSize.X_SMALL -> ImmersiveDimens.CardWidthXSmall to ImmersiveDimens.CardHeightXSmall
        ImmersiveCardSize.SMALL -> ImmersiveDimens.CardWidthSmall to ImmersiveDimens.CardHeightSmall
        ImmersiveCardSize.MEDIUM -> ImmersiveDimens.CardWidthMedium to ImmersiveDimens.CardHeightMedium
        ImmersiveCardSize.LARGE -> ImmersiveDimens.CardWidthLarge to ImmersiveDimens.CardHeightLarge
    }
    val cardShape = ImmersiveShapes.Card

    val sharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && itemId != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "media_$itemId"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                translationY = liftOffset
                scaleX = scale
                scaleY = scale
            }
            .expressiveGlow(
                color = MaterialTheme.colorScheme.primary,
                alpha = glowAlpha,
                borderRadius = ImmersiveDimens.CardCornerRadius,
                blurRadius = 24.dp,
                offsetY = 10.dp,
            )
            .then(sharedElementModifier)
            // Allow call sites to override default immersive size when needed (e.g. episode rows).
            .then(modifier),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shape = cardShape,
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
            loadImage = loadImage, // ✅ Pass through performance parameters
            imageQuality = imageQuality,
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
    loadImage: Boolean,
    imageQuality: ImageQuality,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChange(isPressed)
    }

    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds() // ✅ Ensure content (text/badges) doesn't bleed out
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Open $title",
            ) {
                haptics.lightClick()
                onCardClick()
            },
    ) {
        // ✅ Performance: Full-bleed background image with conditional loading
        OptimizedImage(
            imageUrl = if (loadImage) imageUrl else "", // Only load if visible
            contentDescription = title,
            contentScale = ContentScale.Crop,
            size = ImageSize.BANNER,
            quality = imageQuality, // Adaptive quality based on device tier
            modifier = Modifier
                .fillMaxSize()
                .clip(ImmersiveShapes.Card),
        )

        // Gradient overlay for text readability - Use black-based gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.95f),
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
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
            // Rating on the left
            if (rating != null) {
                Surface(
                    shape = ImmersiveShapes.RatingBadge,
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
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Watch status on the right
            if (unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    val countText = if (unwatchedEpisodeCount > 99) "99+" else unwatchedEpisodeCount.toString()
                    Box(
                        modifier = Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                }
            } else if (isWatched) {
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
        }

        // Watch progress bar
        if (watchProgress > 0f && watchProgress < 1f) {
            androidx.compose.material3.LinearWavyProgressIndicator(
                progress = { watchProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 60.dp)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f),
                amplitude = { 0.12f },
                wavelength = 32.dp,
                waveSpeed = 0.dp, // Static for cards to save battery/performance
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

            // Subtitle text
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
        }
    }
}

enum class ImmersiveCardSize {
    X_SMALL,
    SMALL,
    MEDIUM,
    LARGE,
}
