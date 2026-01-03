@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.MotionTokens

/**
 * Material 3 Expressive Carousel for hero content
 * Perfect for featuring movies, shows, and other media
 */
@Composable
fun ExpressiveHeroCarousel(
    items: List<CarouselItem>,
    onItemClick: (CarouselItem) -> Unit,
    onPlayClick: (CarouselItem) -> Unit,
    heroHeight: Dp = 280.dp,
    horizontalPadding: Dp = 16.dp,
    pageSpacing: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Hero Pager
        Box {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                pageSpacing = pageSpacing,
                modifier = Modifier.height(heroHeight),
                // Add key to prevent incorrect recomposition and state reuse
                key = { page -> items[page].id },
            ) { page ->
                val item = items[page]
                ExpressiveHeroCard(
                    item = item,
                    onItemClick = { onItemClick(item) },
                    onPlayClick = { onPlayClick(item) },
                    isActive = page == pagerState.currentPage,
                )
            }

            // Carousel indicators
            ExpressiveCarouselIndicators(
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
    }
}

/**
 * Compact carousel for media rows
 */
@Composable
fun ExpressiveMediaCarousel(
    title: String,
    items: List<CarouselItem>,
    onItemClick: (CarouselItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Add key to prevent incorrect recomposition and state reuse
            items(
                items = items,
                key = { it.id },
                contentType = { "expressive_carousel_item" },
            ) { item ->
                ExpressiveMediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun ExpressiveHeroCard(
    item: CarouselItem,
    onItemClick: () -> Unit,
    onPlayClick: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.95f,
        animationSpec = MotionTokens.carouselScroll,
        label = "hero_card_scale",
    )

    Card(
        modifier = modifier
            .fillMaxSize()
            .scale(scale.value)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 8.dp else 4.dp,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onItemClick() },
        ) {
            // Background Image
            OptimizedImage(
                imageUrl = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                size = ImageSize.BANNER,
                cornerRadius = 16.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
            )

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                            ),
                            startY = 200f,
                        ),
                    ),
            )

            // Content overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.subtitle.isNotEmpty()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveMediaCard(
    item: CarouselItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .height(240.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() },
        ) {
            // Image with proper poster aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Use weight instead of fixed height
            ) {
                OptimizedImage(
                    imageUrl = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    size = ImageSize.POSTER,
                    cornerRadius = 12.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.subtitle.isNotEmpty()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveCarouselIndicators(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pagerState.pageCount) { index ->
            val isActive = index == pagerState.currentPage
            Surface(
                modifier = Modifier.size(
                    width = if (isActive) 24.dp else 8.dp,
                    height = 8.dp,
                ),
                shape = CircleShape,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
            ) {}
        }
    }
}

/**
 * Data class for carousel items
 */
data class CarouselItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val imageUrl: String,
    val type: MediaType = MediaType.MOVIE,
)

enum class MediaType {
    MOVIE, TV_SHOW, MUSIC, BOOK, PHOTO, VIDEO
}
