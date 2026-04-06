package com.rpeters.jellyfin.ui.components.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text as TvText
import coil3.request.crossfade
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.adaptive.AdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.tv.TvFocusManager
import com.rpeters.jellyfin.ui.tv.TvFocusableCarousel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun TvContentCarousel(
    items: List<BaseItemDto>,
    title: String,
    layoutConfig: AdaptiveLayoutConfig,
    focusManager: TvFocusManager,
    onItemFocus: (BaseItemDto) -> Unit = {},
    onItemSelect: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel = hiltViewModel(),
    carouselId: String = title.replace(" ", "_").lowercase(),
    isLoading: Boolean = false,
    focusRequester: FocusRequester? = null,
    focusBridgeManager: FocusManager? = null,
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    onExitLeft: (() -> Boolean)? = null,
    onExitRight: (() -> Boolean)? = null,
    posterWidth: Dp? = null,
    posterHeight: Dp? = null,
) {
    // Show skeleton if loading or no items
    if (isLoading || items.isEmpty()) {
        TvSkeletonCarousel(
            title = if (isLoading) "Loading..." else title,
            itemCount = if (isLoading) 6 else 0,
            modifier = modifier,
        )
        return
    }

    val lazyListState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(0) }
    val layoutDirection = LocalLayoutDirection.current
    val horizontalPadding = layoutConfig.headerPadding.calculateStartPadding(layoutDirection)

    Column(modifier = modifier) {
        TvText(
            text = title,
            style = TvMaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = horizontalPadding, top = 24.dp, bottom = 16.dp),
        )

        TvFocusableCarousel(
            carouselId = carouselId,
            focusManager = focusManager,
            lazyListState = lazyListState,
            itemCount = items.size,
            itemKeys = items.map { it.id.toString() },
            focusRequester = focusRequester,
            onExitLeft = onExitLeft ?: focusBridgeManager?.let { manager ->
                { manager.moveFocus(FocusDirection.Left) }
            },
            onExitRight = onExitRight ?: focusBridgeManager?.let { manager ->
                { manager.moveFocus(FocusDirection.Right) }
            },
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            onFocusChanged = { isFocused, index ->
                focusedIndex = index
                if (isFocused && index < items.size) {
                    onItemFocus(items[index])
                }
            },
        ) { focusModifier, wrapperFocusedIndex, itemFocusRequesters ->
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
                modifier = focusModifier,
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, item -> item.id.toString() },
                    contentType = { _, _ -> "tv_content_carousel_item" },
                ) { index, item ->
                    TvContentCard(
                        item = item,
                        onItemFocus = {
                            focusedIndex = index
                            onItemFocus(item)
                        },
                        onItemSelect = { onItemSelect(item) },
                        getImageUrl = viewModel::getImageUrl,
                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                        focusRequester = itemFocusRequesters[index],
                        isFocused = focusedIndex == index || wrapperFocusedIndex == index,
                        posterWidth = posterWidth ?: layoutConfig.carouselItemWidth,
                        posterHeight = posterHeight ?: layoutConfig.carouselItemHeight,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvContentCard(
    item: BaseItemDto,
    onItemFocus: () -> Unit,
    onItemSelect: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isFocused: Boolean = false,
    posterWidth: Dp = 150.dp,
    posterHeight: Dp = 225.dp,
) {
    val imageUrl = if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
        getImageUrl(item) ?: getSeriesImageUrl(item)
    } else {
        getImageUrl(item)
    }
    val isPlayed = item.userData?.played == true
    val progressRatio = item.playbackProgressRatio()
    val isInProgress = progressRatio > 0f && !isPlayed

    StandardCardContainer(
        modifier = modifier
            .width(posterWidth)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    onItemFocus()
                }
            },
        imageCard = { interactionSource ->
            TvCard(
                onClick = { onItemSelect() },
                modifier = Modifier
                    .size(posterWidth, posterHeight)
                    .then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    ),
                interactionSource = interactionSource,
                colors = TvCardDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = TvMaterialTheme.colorScheme.surfaceVariant,
                ),
                scale = TvCardDefaults.scale(focusedScale = 1.1f),
                border = TvCardDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(
                            width = 3.dp,
                            color = TvMaterialTheme.colorScheme.border
                        ),
                    )
                ),
                glow = TvCardDefaults.glow(
                    focusedGlow = Glow(
                        elevationColor = TvMaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        elevation = 20.dp,
                    ),
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    JellyfinAsyncImage(
                        model = imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        requestSize = rememberCoilSize(posterWidth, posterHeight),
                        builder = { crossfade(true) },
                    )

                    TvPlaybackStatusBadge(
                        isPlayed = isPlayed,
                        isInProgress = isInProgress,
                        modifier = Modifier.padding(10.dp),
                    )

                    if (isInProgress) {
                        TvPlaybackProgressBar(
                            progressRatio = progressRatio,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(5.dp)
                        )
                    }
                }
            }
        },
        title = {
            TvText(
                text = item.name ?: stringResource(id = R.string.unknown),
                style = TvMaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isFocused) Color.White else TvMaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        },
        subtitle = {
            item.productionYear?.let { year ->
                TvText(
                    text = year.toString(),
                    style = TvMaterialTheme.typography.bodyMedium,
                    color = TvMaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    )
}
