package com.rpeters.jellyfin.ui.components.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rpeters.jellyfin.ui.tv.TvFocusableCarousel
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
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

    val focusManager = rememberTvFocusManager()
    val lazyListState = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(0) }

    Column(modifier = modifier) {
        TvText(
            text = title,
            style = TvMaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 56.dp, top = 24.dp, bottom = 16.dp),
        )

        TvFocusableCarousel(
            carouselId = carouselId,
            focusManager = focusManager,
            lazyListState = lazyListState,
            itemCount = items.size,
            onFocusChanged = { isFocused, index ->
                focusedIndex = index
                if (isFocused && index < items.size) {
                    onItemFocus(items[index])
                }
            },
        ) { focusModifier ->
            val rowModifier = focusRequester?.let { focusModifier.focusRequester(it) } ?: focusModifier

            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = rowModifier,
            ) {
                items(items, key = { it.id?.toString() ?: "" }) { item ->
                    val itemIndex = items.indexOf(item)
                    TvContentCard(
                        item = item,
                        onItemFocus = {
                            focusedIndex = itemIndex
                            onItemFocus(item)
                        },
                        onItemSelect = { onItemSelect(item) },
                        getImageUrl = viewModel::getImageUrl,
                        getSeriesImageUrl = viewModel::getSeriesImageUrl,
                        isFocused = focusedIndex == itemIndex,
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
) {
    Column(
        modifier = modifier
            .width(240.dp)
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
            modifier = Modifier.size(240.dp, 360.dp),
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

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // Title below the card
        TvText(
            text = item.name ?: "Unknown",
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
