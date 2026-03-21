package com.rpeters.jellyfin.ui.screens.tv

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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.rememberWindowLayoutInfo
import com.rpeters.jellyfin.ui.components.tv.TvContentCard
import com.rpeters.jellyfin.ui.components.tv.TvEmptyState
import com.rpeters.jellyfin.ui.components.tv.TvErrorBanner
import com.rpeters.jellyfin.ui.components.tv.TvFullScreenLoading
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
import com.rpeters.jellyfin.ui.tv.TvFocusableGrid
import com.rpeters.jellyfin.ui.tv.TvScreenFocusScope
import com.rpeters.jellyfin.ui.tv.requestInitialFocus
import com.rpeters.jellyfin.ui.tv.rememberTvFocusManager
import com.rpeters.jellyfin.ui.tv.tvKeyboardHandler
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel
import com.rpeters.jellyfin.ui.viewmodel.SearchViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.math.min
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSearchScreen(
    onItemSelect: (String) -> Unit,
    onBack: (() -> Unit)? = null,
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
    val tvLayout = CinefinTvTheme.layout
    val configuration = LocalConfiguration.current

    var focusedBackdrop by remember { mutableStateOf<String?>(null) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val firstFilterFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }
    searchFieldFocusRequester.requestInitialFocus(
        condition = searchState.searchQuery.isBlank() && !searchState.hasSearched
    )

    TvScreenFocusScope(
        screenKey = "tv_search",
        focusManager = tvFocusManager,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .tvKeyboardHandler(
                    focusManager = focusManager,
                    onBack = onBack,
                ),
        ) {
            // Background Layer
            TvImmersiveBackground(backdropUrl = focusedBackdrop)

            // Content Layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tvLayout.screenHorizontalPadding)
                    .padding(top = tvLayout.screenTopPadding),
                verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .focusRequester(searchFieldFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        if (searchState.selectedContentTypes.isNotEmpty()) {
                                            firstFilterFocusRequester.requestFocus()
                                        } else if (searchState.searchResults.isNotEmpty()) {
                                            resultsFocusRequester.requestFocus()
                                        }
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        focusManager.moveFocus(FocusDirection.Left)
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
                    placeholder = { TvText(stringResource(id = R.string.tv_search_placeholder), color = Color.Gray) },
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

                // Content Type Filters
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tvLayout.drawerItemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val filters = listOf(
                        BaseItemKind.MOVIE to "Movies",
                        BaseItemKind.SERIES to "TV Shows",
                        BaseItemKind.AUDIO to "Music",
                        BaseItemKind.BOOK to "Books",
                    )

                    filters.forEach { (kind, label) ->
                        val isSelected = searchState.selectedContentTypes.contains(kind)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleContentType(kind) },
                            modifier = Modifier
                                .then(
                                    if (label == "Movies") {
                                        Modifier.focusRequester(firstFilterFocusRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                                .onPreviewKeyEvent { keyEvent ->
                                    when {
                                        keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.key == Key.DirectionUp -> {
                                            searchFieldFocusRequester.requestFocus()
                                            true
                                        }
                                        keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.key == Key.DirectionDown &&
                                            searchState.searchResults.isNotEmpty() -> {
                                            resultsFocusRequester.requestFocus()
                                            true
                                        }
                                        keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.key == Key.DirectionLeft -> {
                                            focusManager.moveFocus(FocusDirection.Left)
                                            true
                                        }
                                        else -> false
                                    }
                                },
                        ) {
                            TvText(text = label)
                        }
                    }
                }

                // Results Grid
                if (searchState.isSearching) {
                    TvFullScreenLoading(message = "Searching...")
                } else if (!searchState.errorMessage.isNullOrBlank()) {
                    TvErrorBanner(
                        title = "Search Failed",
                        message = searchState.errorMessage ?: "Unknown error",
                        onRetry = {
                            if (searchState.searchQuery.isNotBlank()) {
                                viewModel.performSearch()
                            }
                        },
                        onDismiss = viewModel::clearSearch,
                    )
                } else if (searchState.searchResults.isEmpty() && searchState.hasSearched) {
                    TvEmptyState(
                        title = "No Results",
                        message = "We couldn't find anything matching '${searchState.searchQuery}'",
                        onAction = { viewModel.clearSearch() },
                        actionText = "Clear Search",
                    )
                } else if (searchState.searchResults.isNotEmpty()) {
                    val resultGroups = remember(searchState.searchResults) {
                        searchState.searchResults
                            .groupBy { it.type }
                            .entries
                            .sortedBy { searchResultTypeLabel(it.key) }
                    }
                    val gridState = rememberLazyGridState()
                    val horizontalScreenPadding = tvLayout.screenHorizontalPadding * 2
                    val gridSpacing = tvLayout.cardSpacing
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
                        itemKeys = searchState.searchResults.map { it.id.toString() },
                        columnsCount = columns,
                        focusRequester = resultsFocusRequester,
                        onExitLeft = {
                            focusManager.moveFocus(FocusDirection.Left)
                        },
                        onExitUp = {
                            firstFilterFocusRequester.requestFocus()
                            true
                        },
                        onFocusChanged = { isFocused, index ->
                            if (isFocused && index in searchState.searchResults.indices) {
                                focusedBackdrop = mainViewModel.getBackdropUrl(searchState.searchResults[index])
                            }
                        },
                    ) { focusModifier, wrapperFocusedIndex, itemFocusRequesters ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvText(
                                text = "${searchState.searchResults.size} results for \"${searchState.searchQuery}\"",
                                style = TvMaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f),
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                resultGroups.forEach { (type, items) ->
                                    TvText(
                                        text = "${searchResultTypeLabel(type)} ${items.size}",
                                        style = TvMaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.66f),
                                    )
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = gridState,
                                modifier = focusModifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = tvLayout.contentBottomPadding),
                                verticalArrangement = Arrangement.spacedBy(tvLayout.cardSpacing),
                                horizontalArrangement = Arrangement.spacedBy(tvLayout.cardSpacing),
                            ) {
                                itemsIndexed(
                                    items = searchState.searchResults,
                                    key = { _, item -> item.id.toString() },
                                ) { index, item ->
                                    TvContentCard(
                                        item = item,
                                        onItemFocus = {
                                            focusedBackdrop = mainViewModel.getBackdropUrl(item)
                                        },
                                        onItemSelect = { onItemSelect(item.id.toString()) },
                                        getImageUrl = mainViewModel::getImageUrl,
                                        getSeriesImageUrl = mainViewModel::getSeriesImageUrl,
                                        focusRequester = itemFocusRequesters[index],
                                        isFocused = wrapperFocusedIndex == index,
                                        posterWidth = layoutConfig.carouselItemWidth,
                                        posterHeight = layoutConfig.carouselItemHeight,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    TvEmptyState(
                        title = "Search Across Your Library",
                        message = "Use the search field and filters above to find movies, shows, music, and more with the remote.",
                        onAction = {
                            searchFieldFocusRequester.requestFocus()
                        },
                        actionText = "Start Searching",
                    )
                }
            }
        }
    }
}

private fun searchResultTypeLabel(type: BaseItemKind?): String {
    return when (type) {
        BaseItemKind.MOVIE -> "Movies"
        BaseItemKind.SERIES -> "TV Shows"
        BaseItemKind.AUDIO -> "Music"
        BaseItemKind.BOOK -> "Books"
        BaseItemKind.EPISODE -> "Episodes"
        BaseItemKind.SEASON -> "Seasons"
        BaseItemKind.VIDEO -> "Videos"
        else -> "Other"
    }
}
