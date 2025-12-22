package com.rpeters.jellyfin.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveCompactCard
import com.rpeters.jellyfin.ui.components.ShimmerBox
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.theme.getContentTypeColor
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
            modifier = Modifier.padding(horizontal = Dimens.Spacing16, vertical = Dimens.Spacing8),
        )

        // Note: This sits inside a parent LazyColumn; avoid nested vertical scrollables.
        Column(
            modifier = Modifier.padding(horizontal = Dimens.Spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing8),
        ) {
            libraries.forEach { library ->
                ExpressiveCompactCard(
                    title = library.name ?: stringResource(R.string.unknown),
                    subtitle = library.type?.toString()?.replace("_", " ")?.lowercase()
                        ?.replaceFirstChar { it.uppercase() } ?: "",
                    imageUrl = getImageUrl(library) ?: "",
                    onClick = { onLibraryClick(library) },
                    leadingIcon = when (library.type?.toString()?.lowercase()) {
                        "movies" -> Icons.Default.Movie
                        "tvshows" -> Icons.Default.Tv
                        "music" -> Icons.Default.MusicNote
                        else -> Icons.Default.Folder
                    },
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
                SubcomposeAsyncImage(
                    model = getImageUrl(library),
                    contentDescription = library.name ?: "Library",
                    loading = {
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            cornerRadius = 12,
                        )
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                                .background(contentTypeColor.copy(alpha = 0.1f))
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Library",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = library.name ?: stringResource(R.string.unknown),
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
