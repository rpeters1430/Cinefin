package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.ExpressiveMediaCard
import com.rpeters.jellyfin.ui.utils.MediaPlayerUtils
import com.rpeters.jellyfin.ui.utils.ShareUtils
import com.rpeters.jellyfin.ui.viewmodel.AlbumDetailViewModel
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.utils.getItemKey
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

@OptInAppExperimentalApis
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBackClick: () -> Unit,
    mainViewModel: MainAppViewModel = hiltViewModel(),
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val handlePlay: (BaseItemDto) -> Unit = { track ->
        val streamUrl = mainViewModel.getStreamUrl(track)
        if (streamUrl != null) {
            MediaPlayerUtils.playMedia(context, streamUrl, track)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Unable to start playback")
            }
        }
    }

    LaunchedEffect(albumId) {
        viewModel.load(albumId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.album?.name ?: "Album",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Album header
            state.album?.let { album ->
                AlbumHeader(album = album, getImageUrl = { mainViewModel.getImageUrl(it) })
            }

            // Track list
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.tracks,
                    key = { track -> track.getItemKey().ifEmpty { track.name ?: track.toString() } },
                ) { track ->
                    ExpressiveMediaCard(
                        title = track.name ?: "",
                        subtitle = track.albumArtist ?: track.artists?.firstOrNull() ?: "",
                        imageUrl = mainViewModel.getImageUrl(track) ?: "",
                        rating = null,
                        isFavorite = track.userData?.isFavorite == true,
                        onCardClick = { handlePlay(track) },
                        onPlayClick = { handlePlay(track) },
                        onFavoriteClick = {
                            mainViewModel.toggleFavorite(track)
                        },
                        onMoreClick = {
                            ShareUtils.shareMedia(context, track)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = album.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = album.albumArtist ?: album.artists?.firstOrNull() ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
