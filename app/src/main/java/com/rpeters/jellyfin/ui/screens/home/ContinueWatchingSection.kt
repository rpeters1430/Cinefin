package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.expressiveGlow
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.WatchProgressBar
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.math.roundToInt

@Composable
fun ContinueWatchingSection(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 240.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeSectionTitle(title = stringResource(R.string.home_continue_watching))

        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(
                items = items,
                key = { it.getItemKey() },
                contentType = { "continue_watching_item" },
            ) { item ->
                ContinueWatchingCard(
                    item = item,
                    getImageUrl = getImageUrl,
                    onItemClick = onItemClick,
                    onItemLongPress = onItemLongPress,
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@OptInAppExperimentalApis
@Composable
fun ContinueWatchingCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = 240.dp,
    modifier: Modifier = Modifier,
) {
    val haptics = com.rpeters.jellyfin.ui.utils.rememberExpressiveHaptics()
    val sharedTransitionScope = com.rpeters.jellyfin.ui.navigation.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.rpeters.jellyfin.ui.navigation.LocalAnimatedVisibilityScope.current
    val itemId = item.id.toString()
    
    val sharedElementModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "media_$itemId"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }

    val watchedPercentage = item.userData?.playedPercentage ?: 0.0
    val imageHeight = cardWidth * (9f / 16f)

    ElevatedCard(
        modifier = modifier
            .width(cardWidth)
            .then(sharedElementModifier)
            .expressiveGlow(
                color = MaterialTheme.colorScheme.primary,
                alpha = 0.1f,
                borderRadius = 20.dp
            )
            .graphicsLayer {
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        8f, 8f, android.graphics.Shader.TileMode.CLAMP
                    ).let { effect ->
                        @Suppress("DEPRECATION")
                        com.rpeters.jellyfin.ui.utils.asComposeRenderEffect(effect)
                    }
                }
            },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        ),
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = { 
                    haptics.lightClick()
                    onItemClick(item) 
                },
                onLongClick = { 
                    haptics.heavyClick()
                    onItemLongPress(item) 
                },
            ),
        ) {
            Box {
                OptimizedImage(
                    imageUrl = getImageUrl(item),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    size = ImageSize.BANNER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(imageHeight),
                )

                WatchProgressBar(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                )
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name ?: stringResource(id = R.string.unknown),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val seriesName = if (item.type == BaseItemKind.EPISODE) item.seriesName.orEmpty() else ""
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "${watchedPercentage.roundToInt()}% watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
