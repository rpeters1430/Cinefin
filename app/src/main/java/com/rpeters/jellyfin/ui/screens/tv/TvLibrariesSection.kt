package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import org.jellyfin.sdk.model.api.BaseItemDto
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
fun TvLibrariesSection(
    libraries: List<BaseItemDto>,
    onLibrarySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    if (libraries.isEmpty() && !isLoading) return

    TvText(
        text = "Your Libraries",
        style = TvMaterialTheme.typography.headlineLarge,
        modifier = Modifier.padding(start = 56.dp, top = 24.dp, bottom = 16.dp),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier,
    ) {
        items(
            items = libraries,
            key = { it.id.toString() },
            contentType = { "tv_library_card" },
        ) { library ->
            TvLibraryCard(
                library = library,
                onLibrarySelect = onLibrarySelect,
            )
        }
    }
}

@Composable
fun TvLibraryCard(
    library: BaseItemDto,
    onLibrarySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .width(160.dp)
            .onFocusChanged { isFocused = it.isFocused },
        onClick = { onLibrarySelect(library.id.toString()) },
        scale = CardDefaults.scale(focusedScale = 1.1f),
        colors = CardDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = TvMaterialTheme.colorScheme.surfaceVariant,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(
                    width = 3.dp,
                    color = TvMaterialTheme.colorScheme.border
                ),
            )
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = TvMaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                elevation = 20.dp,
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            TvText(
                text = library.name ?: "Library",
                style = TvMaterialTheme.typography.headlineSmall,
                color = if (isFocused) TvMaterialTheme.colorScheme.onSurface else TvMaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
