package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ✅ PHASE 3: Advanced Loading States with Shimmer Effect
 * Provides sophisticated loading UI that improves perceived performance
 */

@Composable
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer",
    )

    val shimmerColorStops = arrayOf(
        0.0f to Color.Gray.copy(alpha = 0.6f),
        0.5f to Color.Gray.copy(alpha = alpha),
        1.0f to Color.Gray.copy(alpha = 0.6f),
    )

    background(
        brush = Brush.linearGradient(
            colorStops = shimmerColorStops,
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f),
        ),
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 8,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .shimmer(),
    )
}

/**
 * ✅ PHASE 3: Movie Card Skeleton Loading
 */
@Composable
fun SkeletonMovieCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            // Movie poster placeholder
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                cornerRadius = 8,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Title placeholder
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                cornerRadius = 4,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle placeholder
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp),
                cornerRadius = 4,
            )
        }
    }
}

/**
 * ✅ PHASE 3: TV Show Card Skeleton Loading
 */
@Composable
fun SkeletonTVShowCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            // Show poster placeholder
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                cornerRadius = 8,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Title placeholder
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                cornerRadius = 4,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Episode count placeholder
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp),
                cornerRadius = 4,
            )
        }
    }
}

/**
 * ✅ PHASE 3: List Item Skeleton Loading
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showThumbnail) {
            ShimmerBox(
                modifier = Modifier.size(60.dp),
                cornerRadius = 8,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                cornerRadius = 4,
            )

            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp),
                cornerRadius = 4,
            )
        }
    }
}

/**
 * ✅ PHASE 3: Grid Loading State
 */
@Composable
fun SkeletonGrid(
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Adaptive(minSize = 160.dp),
    itemCount: Int = 12,
    content: @Composable () -> Unit = { SkeletonMovieCard() },
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(itemCount) {
            content()
        }
    }
}

/**
 * ✅ PHASE 3: Home Screen Carousel Skeleton
 */
@Composable
fun SkeletonCarousel(
    modifier: Modifier = Modifier,
    title: String = "",
    itemCount: Int = 6,
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        if (title.isNotEmpty()) {
            // Title placeholder
            ShimmerBox(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(120.dp)
                    .height(20.dp),
                cornerRadius = 4,
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Horizontal scrolling items
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(itemCount) {
                SkeletonMovieCard()
            }
        }
    }
}

/**
 * ✅ PHASE 3: Full Screen Loading State
 */
@Composable
fun SkeletonHomeScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Featured content placeholder
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            cornerRadius = 12,
        )

        // Continue watching carousel
        SkeletonCarousel(title = "Continue Watching")

        // Recently added carousel
        SkeletonCarousel(title = "Recently Added")

        // Recommended carousel
        SkeletonCarousel(title = "Recommended")
    }
}
