package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import org.jellyfin.sdk.model.api.BaseItemDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeVideoDetailScreen(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    getBackdropUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onPlayClick: (BaseItemDto) -> Unit = {},
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onShareClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isFavorite by remember { mutableStateOf(item.userData?.isFavorite == true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name ?: "Video") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isFavorite = !isFavorite
                        onFavoriteClick(item)
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        )
                    }
                    IconButton(onClick = { onShareClick(item) }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onPlayClick(item) }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                val backdrop = getBackdropUrl(item)
                if (backdrop != null) {
                    SubcomposeAsyncImage(
                        model = backdrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
            item {
                Row(modifier = Modifier.padding(16.dp)) {
                    val poster = getImageUrl(item)
                    if (poster != null) {
                        SubcomposeAsyncImage(
                            model = poster,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(120.dp).height(180.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text(item.name ?: "", style = MaterialTheme.typography.titleLarge)
                        item.productionYear?.let {
                            Text(it.toString(), style = MaterialTheme.typography.bodyMedium)
                        }
                        item.runTimeTicks?.let { ticks ->
                            val minutes = (ticks / 10_000_000L) / 60
                            Text("$minutes min", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

