package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import org.jellyfin.sdk.model.api.BaseItemDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit,
    onFavoriteClick: (BaseItemDto) -> Unit = {},
    onShareClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isFavorite by remember { mutableStateOf(item.userData?.isFavorite == true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name ?: "Photo") },
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
        modifier = modifier,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val imageUrl = getImageUrl(item)
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

