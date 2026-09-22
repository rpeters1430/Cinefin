package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.immersive.ImmersivePosterCard
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
import com.rpeters.jellyfin.utils.getItemKey
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun ContinueWatchingSection(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    onItemLongPress: (BaseItemDto) -> Unit = {},
    cardWidth: Dp = ImmersiveDimens.ContinueCardWidth,
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
    cardWidth: Dp = ImmersiveDimens.ContinueCardWidth,
    modifier: Modifier = Modifier,
) {
    // Density-redesign spec: a 200x112 (16:9) thumb with an inset progress bar and the title
    // below. Derive the thumb height from whatever width a caller passes so existing
    // adaptive-layout call sites keep working unchanged.
    val thumbHeight = cardWidth * (ImmersiveDimens.ContinueThumbHeight / ImmersiveDimens.ContinueCardWidth)
    val watchProgress = item.userData?.playedPercentage?.let { (it / 100.0).toFloat() }
    val seriesName = if (item.type == BaseItemKind.EPISODE) item.seriesName.orEmpty() else ""

    ImmersivePosterCard(
        title = item.name ?: stringResource(id = R.string.unknown),
        imageUrl = getImageUrl(item) ?: "",
        onCardClick = { onItemClick(item) },
        onCardLongClick = { onItemLongPress(item) },
        modifier = modifier,
        subtitle = seriesName,
        watchProgress = watchProgress,
        posterWidth = cardWidth,
        posterHeight = thumbHeight,
        posterShape = MaterialTheme.shapes.large,
    )
}
