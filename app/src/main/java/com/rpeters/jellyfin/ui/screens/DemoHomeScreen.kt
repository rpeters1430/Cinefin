package com.rpeters.jellyfin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A single mock media item shown in Demo Mode. No real server data is involved. */
data class DemoMediaItem(
    val title: String,
    val subtitle: String,
    val accentColor: Color,
)

/**
 * Sample library content shown when the app is running in Demo Mode (see
 * [com.rpeters.jellyfin.data.repository.DemoModeRepository]). This is a self-contained screen
 * with no dependency on a real Jellyfin server; it exists purely so app store reviewers can
 * explore representative app functionality without personal media.
 */
private val demoMediaItems = listOf(
    DemoMediaItem("Big Buck Bunny", "Movie · Sample", Color(0xFF6200EE)),
    DemoMediaItem("Sintel", "Movie · Sample", Color(0xFF2962FF)),
    DemoMediaItem("Tears of Steel", "Movie · Sample", Color(0xFF00695C)),
    DemoMediaItem("Elephants Dream", "Movie · Sample", Color(0xFF6D4C41)),
    DemoMediaItem("Cosmos Laundromat", "Short Film · Sample", Color(0xFFAD1457)),
    DemoMediaItem("Caminandes", "Short Film · Sample", Color(0xFF00838F)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoHomeScreen(
    onExitDemoMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPlayer by remember { mutableStateOf(false) }

    if (showPlayer) {
        DemoVideoPlayerScreen(onBack = { showPlayer = false })
        return
    }

    // Demo Mode is the only entry on its back stack (the sign-in screen was popped when Demo
    // Mode was entered), so without this, hardware/gesture back exits the app entirely instead
    // of returning to sign-in the way the top app bar's back arrow does.
    BackHandler(onBack = onExitDemoMode)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Demo Mode") },
                navigationIcon = {
                    IconButton(onClick = onExitDemoMode) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Exit Demo Mode")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            Text(
                text = "You're viewing sample content in Demo Mode. No server connection is required.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(demoMediaItems) { item ->
                    DemoMediaCard(item = item, onClick = { showPlayer = true })
                }
            }
        }
    }
}

@Composable
private fun DemoMediaCard(item: DemoMediaItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = item.accentColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Play ${item.title}",
                tint = Color.White,
                modifier = Modifier.padding(8.dp),
            )
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}
