package com.rpeters.jellyfin.ui.components.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.adaptive.AdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.image.rememberCoilSize
import com.rpeters.jellyfin.ui.tv.TvFocusManager
import com.rpeters.jellyfin.ui.tv.TvFocusableCarousel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

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
            focusRequester = focusRequester,
            onFocusChanged = { isFocused, index ->
                focusedIndex = index
                if (isFocused && index < items.size) {
                    onItemFocus(items[index])
                }
            },
        ) { focusModifier ->
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(layoutConfig.spacing),
                modifier = focusModifier,
            ) {
                itemsIndexed(items, key = { index, item -> item.id?.toString() ?: index.toString() }) { index, item ->
                    TvContentCard(
                        item = item,
                        onItemFocus = {
                            focusedIndex = index
                            onItemFocus(item)
                        },
                        onItemSelect = { onItemSelect(item) },
                        getImageUrl = viewModel::getImageUrl,
                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                        isFocused = focusedIndex == index,
                        posterWidth = layoutConfig.carouselItemWidth,
                        posterHeight = layoutConfig.carouselItemHeight,
                    )
                }
            }
        }
    }
}

@Composable
fun TvContentCard(
    item: BaseItemDto,
    onItemFocus: () -> Unit,
    onItemSelect: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    getSeriesImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    posterWidth: Dp = 240.dp,
    posterHeight: Dp = 360.dp,
) {
    Column(
        modifier = modifier
            .width(posterWidth)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onItemFocus()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TvCard(
            onClick = { onItemSelect() },
            modifier = Modifier.size(posterWidth, posterHeight),
            colors = TvCardDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
            ),
            glow = TvCardDefaults.glow(
                focusedGlow = androidx.tv.material3.Glow(
                    elevationColor = if (isFocused) TvMaterialTheme.colorScheme.primary else TvMaterialTheme.colorScheme.surface,
                    elevation = if (isFocused) 16.dp else 0.dp,
                ),
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imageUrl = if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                    getSeriesImageUrl(item)
                } else {
                    getImageUrl(item)
                }

                JellyfinAsyncImage(
                    model = imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    requestSize = rememberCoilSize(posterWidth, posterHeight),
                    builder = { crossfade(true) },
                )
            }
        }

        // Title below the card
        TvText(
            text = item.name ?: stringResource(id = R.string.unknown),
            style = TvMaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = TvMaterialTheme.colorScheme.onSurface,
        )

        // Subtitle
        item.productionYear?.let { year ->
            TvText(
                text = year.toString(),
                style = TvMaterialTheme.typography.bodyMedium,
                color = TvMaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
