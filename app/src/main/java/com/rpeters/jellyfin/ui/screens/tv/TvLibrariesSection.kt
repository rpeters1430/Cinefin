package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import org.jellyfin.sdk.model.api.BaseItemDto
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
fun TvLibrariesSection(
    libraries: List<BaseItemDto>,
    onLibrarySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (libraries.isEmpty()) return

    TvText(
        text = "Your Libraries",
        style = TvMaterialTheme.typography.headlineLarge,
        modifier = Modifier.padding(start = 56.dp, top = 24.dp, bottom = 16.dp),
    )

    val focusRequester = remember { FocusRequester() }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        items(libraries, key = { it.id?.toString() ?: "" }) { library ->
            TvLibraryCard(
                library = library,
                onLibrarySelect = { id -> onLibrarySelect(id) },
            )
        }
    }

    // Request initial focus on libraries row
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (libraries.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun TvLibraryCard(
    library: BaseItemDto,
    onLibrarySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .width(240.dp)
            .height(180.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        colors = CardDefaults.colors(
            containerColor = TvMaterialTheme.colorScheme.surfaceVariant,
        ),
        glow = CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = if (isFocused) TvMaterialTheme.colorScheme.primary else TvMaterialTheme.colorScheme.surface,
                elevation = if (isFocused) 16.dp else 0.dp,
            ),
        ),
        onClick = {
            library.id?.let { onLibrarySelect(it.toString()) }
        },
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            TvText(
                text = library.name ?: "Library",
                style = TvMaterialTheme.typography.headlineSmall,
            )
        }
    }
}
