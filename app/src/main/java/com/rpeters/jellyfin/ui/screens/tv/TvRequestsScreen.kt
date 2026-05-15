package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.ui.components.tv.TvContentCard
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.image.JellyfinAsyncImage
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.tv.TvFocusableGrid
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.requestInitialFocus
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.RequestsViewModel
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRequestsScreen(
    initialQuery: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val seerrPrefs by viewModel.seerrPreferences.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val tvFocusManager = rememberTvFocusManager()
    val tvLayout = CinefinTvTheme.layout

    var focusedItem by remember { mutableStateOf<SeerrMediaItem?>(null) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && initialQuery != uiState.query) {
            viewModel.onQueryChange(initialQuery)
        }
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isBlank() && !uiState.isPluginConfigured && seerrPrefs.baseUrl.isBlank()) {
            try {
                searchFieldFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .tvKeyboardHandler(
                focusManager = focusManager,
                onBack = onBack,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tvLayout.screenHorizontalPadding)
                .padding(top = tvLayout.screenTopPadding),
            verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
        ) {
            // Search Input
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .focusRequester(searchFieldFocusRequester),
                placeholder = { TvText("Search for movies or shows to request...", color = Color.Gray) },
                leadingIcon = { TvIcon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TvMaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                ),
                shape = TvMaterialTheme.shapes.medium,
            )

            if (!uiState.isPluginConfigured && seerrPrefs.baseUrl.isBlank() && !uiState.isLoading) {
                TvEmptyState(
                    title = "Requests Not Configured",
                    message = "Configure Seerr/Overseerr or the Cinefin plugin in settings to request media.",
                    onAction = onBack,
                    actionText = "Go Back",
                )
            } else if (uiState.isLoading) {
                TvFullScreenLoading(message = "Searching request catalog...")
            } else if (uiState.results.isEmpty() && uiState.query.isNotBlank()) {
                TvEmptyState(
                    title = "No Results",
                    message = "We couldn't find anything matching '${uiState.query}' in the request catalog.",
                    onAction = { viewModel.onQueryChange("") },
                    actionText = "Clear Search",
                )
            } else if (uiState.results.isNotEmpty()) {
                val gridState = rememberLazyGridState()
                val columns = 5 // Fixed for TV

                TvFocusableGrid(
                    gridId = "request_results",
                    focusManager = tvFocusManager,
                    lazyGridState = gridState,
                    itemCount = uiState.results.size,
                    columnsCount = columns,
                    focusRequester = resultsFocusRequester,
                    onExitUp = {
                        searchFieldFocusRequester.requestFocus()
                        true
                    },
                    onFocusChanged = { isFocused, index ->
                        if (isFocused && index in uiState.results.indices) {
                            focusedItem = uiState.results[index]
                        }
                    },
                ) { focusModifier, wrapperFocusedIndex, itemFocusRequesters ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = focusModifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = tvLayout.contentBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(tvLayout.cardSpacing),
                        horizontalArrangement = Arrangement.spacedBy(tvLayout.cardSpacing),
                    ) {
                        itemsIndexed(
                            items = uiState.results,
                            key = { _, item -> item.id },
                        ) { index, item ->
                            TvRequestCard(
                                item = item,
                                seerrBaseUrl = seerrPrefs.baseUrl,
                                isRequesting = uiState.requestingMediaId == item.id,
                                onRequest = { viewModel.requestMedia(item) },
                                focusRequester = itemFocusRequesters[index],
                                isFocused = wrapperFocusedIndex == index,
                            )
                        }
                    }
                }
            } else {
                TvEmptyState(
                    title = "Request New Media",
                    message = "Type a name to search for movies or TV shows that aren't on the server yet.",
                    onAction = { searchFieldFocusRequester.requestFocus() },
                    actionText = "Start Typing",
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvRequestCard(
    item: SeerrMediaItem,
    seerrBaseUrl: String,
    isRequesting: Boolean,
    onRequest: () -> Unit,
    focusRequester: FocusRequester,
    isFocused: Boolean,
) {
    val posterUrl = item.posterPath?.let { path ->
        if (seerrBaseUrl.isNotBlank()) {
            "${seerrBaseUrl.trimEnd('/')}/imageproxy/t/p/w600_and_h900_bestv2$path"
        } else {
            "https://image.tmdb.org/t/p/w500$path"
        }
    }

    val status = item.mediaInfo?.status ?: 1
    val isAvailable = status == 5
    val isPending = status == 2 || status == 3

    TvCard(
        onClick = { if (!isAvailable && !isPending && !isRequesting) onRequest() },
        modifier = Modifier
            .width(180.dp)
            .height(300.dp)
            .focusRequester(focusRequester),
        scale = TvCardDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            JellyfinAsyncImage(
                model = posterUrl,
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Status Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TvText(
                        text = item.displayTitle,
                        style = TvMaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )

                    when {
                        isAvailable -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TvIcon(Icons.Default.Check, null, tint = Color.Green, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                TvText("Available", style = TvMaterialTheme.typography.labelSmall, color = Color.Green)
                            }
                        }
                        isPending -> {
                            TvText("Pending", style = TvMaterialTheme.typography.labelSmall, color = Color.Yellow)
                        }
                        isRequesting -> {
                            TvText("Requesting...", style = TvMaterialTheme.typography.labelSmall, color = TvMaterialTheme.colorScheme.primary)
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TvIcon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                TvText("Request", style = TvMaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
