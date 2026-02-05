package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
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
    cardWidth: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

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
    cardWidth: Dp = 160.dp,
    modifier: Modifier = Modifier,
) {
    val watchedPercentage = item.userData?.playedPercentage ?: 0.0

    ElevatedCard(
        modifier = modifier.width(cardWidth),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongPress(item) },
            ),
        ) {
            Box {
                OptimizedImage(
                    imageUrl = getImageUrl(item),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    size = ImageSize.POSTER,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
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
