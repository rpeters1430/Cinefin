package com.rpeters.jellyfin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard
import com.rpeters.jellyfin.ui.components.immersive.ParallaxHeroSection
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Immersive version of FavoritesScreen with:
 * - Random favorite as hero backdrop (full-bleed parallax)
 * - Masonry/staggered grid layout for variety
 * - Floating action buttons (auto-hide on scroll)
 * - Tighter spacing for cinematic feel
 * - Large favorite cards (280dp)
 * - Material 3 Expressive animations
 */
@OptInAppExperimentalApis
@Composable
fun ImmersiveFavoritesScreen(
    favorites: List<BaseItemDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onNowPlayingClick: () -> Unit = {},
    onItemClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyStaggeredGridState()

    // Auto-hide FABs when scrolling down
    val showFabs by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 100
        }
    }

    // Pick a random favorite for hero (first item for consistency)
    val heroItem = remember(favorites) {
        favorites.firstOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2), // 2-column masonry grid
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (heroItem != null) ImmersiveDimens.HeroHeightPhone + 16.dp else 24.dp, // Space for hero if present
                    bottom = 120.dp, // Space for MiniPlayer + FABs
                ),
                horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
                verticalItemSpacing = ImmersiveDimens.SpacingRowTight,
            ) {
                // Content states
                when {
                    isLoading && favorites.isEmpty() -> {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    errorMessage != null -> {
                        item(key = "error") {
                            ImmersiveErrorCard(errorMessage = errorMessage)
                        }
                    }
                    favorites.isEmpty() -> {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                                Text(
                                    text = "No favorites yet",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "Add items to your favorites to see them here",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    else -> {
                        // Favorites grid (skip hero item to avoid duplication)
                        val gridItems = if (heroItem != null) favorites.drop(1) else favorites
                        items(
                            items = gridItems,
                            key = { it.getItemKey() },
                            contentType = { "immersive_favorite" },
                        ) { item ->
                            ImmersiveMediaCard(
                                title = item.name ?: "",
                                imageUrl = getImageUrl(item) ?: "",
                                onCardClick = { onItemClick(item) },
                                subtitle = when (item.type) {
                                    BaseItemKind.EPISODE -> item.seriesName ?: ""
                                    else -> item.productionYear?.toString() ?: ""
                                },
                                rating = item.communityRating,
                                isFavorite = true, // All items here are favorites
                                isWatched = item.userData?.played == true,
                                watchProgress = (item.userData?.playedPercentage ?: 0.0).toFloat() / 100f,
                                cardSize = ImmersiveCardSize.MEDIUM, // 280dp width
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        // Hero section (if there are favorites)
        heroItem?.let { hero ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(ImmersiveDimens.HeroHeightPhone),
            ) {
                ParallaxHeroSection(
                    imageUrl = getImageUrl(hero) ?: "",
                    scrollOffset = gridState.firstVisibleItemScrollOffset.toFloat(),
                    parallaxFactor = 0.5f,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Title and metadata overlaid on hero
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.favorites),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${favorites.size} ${if (favorites.size == 1) "item" else "items"}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        // Floating action buttons (top-right corner)
        AnimatedVisibility(
            visible = showFabs,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button
                FloatingActionButton(
                    onClick = onBackClick,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_up),
                    )
                }

                // Refresh button
                FloatingActionButton(
                    onClick = onRefresh,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                    )
                }
            }
        }

        // MiniPlayer at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            MiniPlayer(onExpandClick = onNowPlayingClick)
        }
    }
}

/**
 * Error card with Material 3 styling
 */
@Composable
private fun ImmersiveErrorCard(
    errorMessage: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
    }
}
