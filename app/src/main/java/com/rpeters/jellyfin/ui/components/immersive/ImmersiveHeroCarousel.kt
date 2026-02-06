@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.components.CarouselItem
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens

/**
 * Immersive full-screen hero carousel for cinematic media experiences.
 * Based on ExpressiveHeroCarousel but optimized for immersive layouts.
 *
 * Key differences:
 * - Larger hero height (480dp on phone vs 280dp)
 * - Full-bleed design with no horizontal padding
 * - Stronger gradient overlay
 * - Larger typography
 * - Auto-scrolling enabled by default
 *
 * @param items List of carousel items to display
 * @param onItemClick Click handler for carousel items
 * @param onPlayClick Click handler for play button
 * @param heroHeight Height of the carousel (defaults to phone height, adjust for tablet/TV)
 * @param pageSpacing Spacing between carousel items
 * @param autoScrollEnabled Enable auto-scrolling through items
 * @param autoScrollIntervalMillis Time between auto-scroll transitions (default 15s)
 */
@Composable
fun ImmersiveHeroCarousel(
    items: List<CarouselItem>,
    onItemClick: (CarouselItem) -> Unit,
    onPlayClick: (CarouselItem) -> Unit,
    modifier: Modifier = Modifier,
    heroHeight: Dp = ImmersiveDimens.HeroHeightPhone,
    pageSpacing: Dp = 0.dp,
    autoScrollEnabled: Boolean = true,
    autoScrollIntervalMillis: Long = 15000L,
) {
    if (items.isEmpty()) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Full-screen items for immersive experience
    val itemWidth = remember(screenWidth) { screenWidth }

    val carouselState = rememberCarouselState { items.size }

    // Track current item for indicators
    val currentItem by remember {
        derivedStateOf {
            carouselState.currentItem
        }
    }

    // Auto-scroll effect
    LaunchedEffect(carouselState, autoScrollEnabled, autoScrollIntervalMillis, items.size) {
        if (!autoScrollEnabled || items.size <= 1) return@LaunchedEffect

        while (true) {
            kotlinx.coroutines.delay(autoScrollIntervalMillis)
            val nextPage = (carouselState.currentItem + 1) % items.size
            carouselState.scrollToItem(nextPage)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            HorizontalUncontainedCarousel(
                state = carouselState,
                itemWidth = itemWidth,
                itemSpacing = pageSpacing,
                contentPadding = PaddingValues(0.dp),
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = carouselState),
                modifier = Modifier.height(heroHeight),
            ) { index ->
                val item = items[index]
                val isActive = index == currentItem

                ImmersiveHeroCard(
                    item = item,
                    onItemClick = { onItemClick(item) },
                    onPlayClick = { onPlayClick(item) },
                    isActive = isActive,
                    modifier = Modifier
                        .height(heroHeight)
                        .fillMaxWidth(),
                )
            }

            // Carousel indicators
            ImmersiveCarouselIndicators(
                currentIndex = currentItem,
                itemCount = items.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun ImmersiveHeroCard(
    item: CarouselItem,
    onItemClick: () -> Unit,
    onPlayClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = if (isActive) 1.0f else 0.98f

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable { onItemClick() },
    ) {
        // Full-bleed background image
        OptimizedImage(
            imageUrl = item.imageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            size = ImageSize.BANNER,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(0.dp)),
        )

        // Strong gradient overlay for text readability
        val scrimColor = MaterialTheme.colorScheme.scrim
        val overlayTextColor = Color.White
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val gradientBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            scrimColor.copy(alpha = 0.3f),
                            scrimColor.copy(alpha = 0.7f),
                            scrimColor.copy(alpha = 0.9f)
                        ),
                        startY = size.height * 0.3f,
                        endY = size.height,
                    )
                    onDrawBehind {
                        drawRect(gradientBrush)
                    }
                },
        )

        // Content overlay at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = overlayTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.subtitle.isNotEmpty()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = overlayTextColor.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ImmersiveCarouselIndicators(
    currentIndex: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    if (itemCount <= 1) return

    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        repeat(itemCount) { index ->
            val isActive = index == currentIndex
            Surface(
                modifier = Modifier
                    .height(if (isActive) 8.dp else 6.dp)
                    .padding(horizontal = 2.dp),
                shape = CircleShape,
                color = if (isActive) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.4f)
                },
            ) {
                Box(
                    modifier = Modifier
                        .height(if (isActive) 8.dp else 6.dp)
                        .padding(horizontal = if (isActive) 16.dp else 6.dp),
                )
            }
        }
    }
}
