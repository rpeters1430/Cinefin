package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo
import com.rpeters.jellyfin.ui.components.tv.TvContentCard
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.tv.TvFocusableGrid
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SearchViewModel
import kotlin.math.min
import org.jellyfin.sdk.model.api.BaseItemKind
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.ExperimentalTvMaterial3Api

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    mainViewModel: MainAppViewModel = hiltViewModel(),
) {
    val searchState by viewModel.searchState.collectAsState()
    val focusManager = LocalFocusManager.current
    val tvFocusManager = rememberTvFocusManager()
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val windowLayoutInfo = rememberWindowLayoutInfo()
    val layoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, windowLayoutInfo)
    val configuration = LocalConfiguration.current
    
    var focusedBackdrop by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // Background Layer
        TvImmersiveBackground(backdropUrl = focusedBackdrop)

        // Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp)
                .padding(top = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Search Input
            OutlinedTextField(
                value = searchState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                placeholder = { TvText("Search movies, TV shows, and more...", color = Color.Gray) },
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
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = TvMaterialTheme.shapes.medium
            )

            // Content Type Filters
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    BaseItemKind.MOVIE to "Movies",
                    BaseItemKind.SERIES to "TV Shows",
                    BaseItemKind.AUDIO to "Music",
                    BaseItemKind.BOOK to "Books"
                )

                filters.forEach { (kind, label) ->
                    val isSelected = searchState.selectedContentTypes.contains(kind)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleContentType(kind) }
                    ) {
                        TvText(text = label)
                    }
                }
            }

            // Results Grid
            if (searchState.isSearching) {
                TvFullScreenLoading(message = "Searching...")
            } else if (searchState.searchResults.isEmpty() && searchState.hasSearched) {
                TvEmptyState(
                    title = "No Results",
                    message = "We couldn't find anything matching '${searchState.searchQuery}'",
                    onAction = { viewModel.clearSearch() },
                    actionText = "Clear Search"
                )
            } else if (searchState.searchResults.isNotEmpty()) {
                val gridState = rememberLazyGridState()
                val horizontalScreenPadding = 56.dp * 2
                val gridSpacing = 24.dp
                val availableWidth = (configuration.screenWidthDp.dp - horizontalScreenPadding).coerceAtLeast(0.dp)
                val maxColumnsForCardWidth =
                    ((availableWidth + gridSpacing) / (layoutConfig.carouselItemWidth + gridSpacing))
                        .toInt()
                        .coerceAtLeast(2)
                val columns = min(layoutConfig.gridColumns.coerceAtLeast(4), maxColumnsForCardWidth)

                TvFocusableGrid(
                    gridId = "search_results",
                    focusManager = tvFocusManager,
                    lazyGridState = gridState,
                    itemCount = searchState.searchResults.size,
                    columnsCount = columns,
                    onFocusChanged = { isFocused, index ->
                        if (isFocused && index in searchState.searchResults.indices) {
                            focusedBackdrop = mainViewModel.getBackdropUrl(searchState.searchResults[index])
                        }
                    }
                ) { focusModifier ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = focusModifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 56.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        itemsIndexed(
                            items = searchState.searchResults,
                            key = { _, item -> item.id.toString() }
                        ) { index, item ->
                            TvContentCard(
                                item = item,
                                onItemFocus = {
                                    focusedBackdrop = mainViewModel.getBackdropUrl(item)
                                },
                                onItemSelect = { onItemSelect(item.id.toString()) },
                                getImageUrl = mainViewModel::getImageUrl,
                                getSeriesImageUrl = mainViewModel::getSeriesImageUrl,
                                posterWidth = layoutConfig.carouselItemWidth,
                                posterHeight = layoutConfig.carouselItemHeight,
                            )
                        }
                    }
                }
            } else {
                // Initial state / Suggestions could go here
                TvText(
                    text = "Try searching for your favorite titles",
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 40.dp)
                )
            }
        }
    }
}
