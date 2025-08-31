package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.rpeters.jellyfin.ui.theme.getContentTypeColor
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.flow.collect
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun LibraryGridSection(
    libraries: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    onLibraryClick: (BaseItemDto) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val listState = rememberLazyListState()
        val context = LocalContext.current
        val imageLoader = context.imageLoader
        val density = LocalDensity.current
        val imageWidth = with(density) { 200.dp.roundToPx() }
        val imageHeight = with(density) { 120.dp.roundToPx() }

        LaunchedEffect(listState, libraries) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collect { lastVisible ->
                    val start = (lastVisible ?: -1) + 1
                    val end = start + 3
                    for (index in start until end) {
                        libraries.getOrNull(index)?.let { prefetchItem ->
                            val request = ImageRequest.Builder(context)
                                .data(getImageUrl(prefetchItem))
                                .size(imageWidth, imageHeight)
                                .scale(Scale.FILL)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build()
                            imageLoader.enqueue(request)
                        }
                    }
                }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(libraries, key = { it.getItemKey() }) { library ->
                LibraryCard(
                    library = library,
                    getImageUrl = getImageUrl,
                    onClick = onLibraryClick,
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(
    library: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentTypeColor = getContentTypeColor(library.type?.toString())
    Card(
        modifier = modifier.width(200.dp).clickable { onClick(library) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box {
                val context = LocalContext.current
                val density = LocalDensity.current
                val request = ImageRequest.Builder(context)
                    .data(getImageUrl(library))
                    .size(with(density) { 200.dp.roundToPx() }, with(density) { 120.dp.roundToPx() })
                    .scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = library.name ?: "Library",
                    placeholder = ColorPainter(contentTypeColor.copy(alpha = 0.1f)),
                    error = ColorPainter(contentTypeColor.copy(alpha = 0.1f)),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = library.name ?: "Unknown Library",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                library.type?.let { type ->
                    Text(
                        text = type.toString().replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryGridSectionPreview() {
    LibraryGridSection(
        libraries = emptyList(),
        getImageUrl = { null },
        onLibraryClick = {},
        title = "Libraries",
    )
}
