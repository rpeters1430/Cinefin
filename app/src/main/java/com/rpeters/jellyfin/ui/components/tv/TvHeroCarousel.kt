package com.rpeters.jellyfin.ui.components.tv

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberScreenWidthHeight
import org.jellyfin.sdk.model.api.BaseItemDto

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * A featured content carousel for the TV Home screen.
 * Uses Material 3 TV Carousel with immersive backdrops and quick actions.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroCarousel(
    featuredItems: List<BaseItemDto>,
    onItemClick: (BaseItemDto) -> Unit,
    onPlayClick: (BaseItemDto) -> Unit,
    getHeroImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
    carouselState: CarouselState = rememberCarouselState(),
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    if (featuredItems.isEmpty()) return

    Carousel(
        itemCount = featuredItems.size,
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .focusRequester(focusRequester),
        carouselState = carouselState,
        contentTransformStartToEnd = fadeIn().togetherWith(fadeOut()),
        contentTransformEndToStart = fadeIn().togetherWith(fadeOut()),
    ) { index ->
        val item = featuredItems[index]
        val heroImageUrl = getHeroImageUrl(item)
        val isPlayed = item.userData?.played == true
        val progressRatio = item.playbackProgressRatio()
        val isInProgress = progressRatio > 0f && !isPlayed

        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            JellyfinAsyncImage(
                model = heroImageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                requestSize = rememberScreenWidthHeight(400.dp),
            )

            // Cinematic Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                            ),
                            startX = 0f,
                            endX = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
            )

            // Content Information
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 56.dp, bottom = 48.dp)
                    .fillMaxWidth(0.5f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = item.name ?: "Unknown Title",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isPlayed || isInProgress) {
                    TvPlaybackStatusBadge(
                        isPlayed = isPlayed,
                        isInProgress = isInProgress,
                    )
                }

                item.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isInProgress) {
                    TvPlaybackProgressBar(
                        progressRatio = progressRatio,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(5.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { onPlayClick(item) },
                        modifier = Modifier.width(120.dp),
                    ) {
                        Text(stringResource(id = R.string.play))
                    }

                    Button(
                        onClick = { onItemClick(item) },
                        modifier = Modifier.width(140.dp),
                    ) {
                        Text(stringResource(id = R.string.more_info))
                    }
                }
            }
        }
    }
}
