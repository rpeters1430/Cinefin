package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.viewmodel.ArtistAlbumsViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

@OptInAppExperimentalApis
@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String?,
    onBackClick: () -> Unit,
    onAlbumClick: (BaseItemDto) -> Unit,
    mainViewModel: MainAppViewModel = hiltViewModel(),
    viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(artistId) {
        viewModel.loadAlbums(artistId)
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = artistName ?: "Artist",
                navigationIcon = {
                    ExpressiveBackNavigationIcon(onClick = onBackClick)
                }
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(state.albums, key = { it.id.toString() }) { album ->
                ExpressiveMediaCard(
                    title = album.name ?: "",
                    subtitle = album.productionYear?.toString() ?: "",
                    imageUrl = mainViewModel.getImageUrl(album) ?: "",
                    onCardClick = { onAlbumClick(album) },
                )
            }
        }
    }
}
