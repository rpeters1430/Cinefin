package com.rpeters.jellyfin.ui.screens

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.core.util.PerformanceMetricsTracker
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.aiAura
import com.rpeters.jellyfin.ui.components.immersive.rememberImmersivePerformanceConfig
import com.rpeters.jellyfin.ui.image.ImageQuality
import com.rpeters.jellyfin.ui.image.ImageSize
import com.rpeters.jellyfin.ui.image.OptimizedImage
import com.rpeters.jellyfin.ui.theme.ImmersiveShapes
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.ui.viewmodel.SearchViewModel
import com.rpeters.jellyfin.utils.getItemKey
import com.rpeters.jellyfin.utils.rememberDebouncedState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Immersive version of SearchScreen with:
 * - Floating translucent search bar (auto-hides on scroll)
 * - Full-screen results with large immersive cards (280dp)
 * - Auto-hiding FABs for AI and filters
 * - Tighter spacing for cinematic feel
 * - Material 3 Expressive animations
 */
@OptIn(ExperimentalFoundationApi::class)
@OptInAppExperimentalApis
@Composable
fun ImmersiveSearchScreen(
    appState: MainAppState,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    onSearchRequests: (String) -> Unit = {},
    onItemClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    // Calculate adaptive layout config
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
    val adaptiveConfig = rememberAdaptiveLayoutConfig(windowSizeClass)
    val perfConfig = rememberImmersivePerformanceConfig()

    PerformanceMetricsTracker(
        enabled = com.rpeters.jellyfin.BuildConfig.DEBUG,
        intervalMs = 30000,
    )

    var searchQuery by remember { mutableStateOf(appState.searchQuery) }
    var isFilterExpanded by remember { mutableStateOf(false) }
    var selectedContentTypes by remember {
        mutableStateOf(
            setOf(
                BaseItemKind.MOVIE,
                BaseItemKind.SERIES,
                BaseItemKind.AUDIO,
                BaseItemKind.BOOK,
            ),
        )
    }

    val debouncedQuery = rememberDebouncedState(
        value = searchQuery,
        delayMs = Constants.SEARCH_DEBOUNCE_MS,
    )

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    var aiSearchEnabled by remember { mutableStateOf(false) }

    // Recent search suggestions
    val recentSearches = remember {
        listOf("Avengers", "Breaking Bad", "The Office", "Star Wars", "Marvel")
    }

    // Smart suggestions based on content
    val smartSuggestions = remember(appState.allItems) {
        val genres = appState.allItems
            .flatMap { it.genres ?: emptyList() }
            .groupBy { it }
            .entries
            .sortedByDescending { it.value.size }
            .take(8)
            .map { it.key }

        val years = appState.allItems
            .mapNotNull { it.productionYear }
            .distinct()
            .sorted()
            .takeLast(5)
            .map { it.toString() }

        genres + years
    }

    LaunchedEffect(debouncedQuery, aiSearchEnabled) {
        if (debouncedQuery.isBlank()) {
            onClearSearch()
        } else {
            // Use AI to enhance query if enabled
            if (aiSearchEnabled && debouncedQuery.length > 3) {
                coroutineScope.launch {
                    val enhancedQuery = viewModel.enhanceSearchQuery(debouncedQuery)
                    onSearch(enhancedQuery)
                }
            } else {
                onSearch(debouncedQuery)
            }
        }
    }

    val listState = rememberLazyListState()

    // Density pass (item 8): scope chips (All/Movies/Shows/People, with counts) filter the
    // already-loaded search results client-side.
    var selectedScope by remember { mutableStateOf(SearchScope.ALL) }
    val scopeCounts = remember(appState.searchResults) {
        SearchScope.entries.associateWith { scope -> appState.searchResults.count { matchesScope(it, scope) } }
    }
    val scopedResults = remember(appState.searchResults, selectedScope) {
        appState.searchResults.filter { matchesScope(it, selectedScope) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Main content
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 120.dp, // Space for MiniPlayer + FABs
            ),
        ) {
            // Density pass: sticky search field + scope chip row, always pinned at the top.
            searchStickyHeader(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                focusRequester = focusRequester,
                onBackClick = {
                    focusManager.clearFocus()
                    onBackClick()
                },
                onClearSearch = {
                    searchQuery = ""
                    onClearSearch()
                },
                showScopeChips = appState.searchResults.isNotEmpty(),
                selectedScope = selectedScope,
                scopeCounts = scopeCounts,
                onScopeSelected = { selectedScope = it },
            )

            // Content Type Filters
            if (isFilterExpanded) {
                contentTypeFiltersSection(
                    selectedContentTypes = selectedContentTypes,
                    onToggleContentType = { kind ->
                        selectedContentTypes = if (selectedContentTypes.contains(kind)) {
                            selectedContentTypes - kind
                        } else {
                            selectedContentTypes + kind
                        }
                    },
                )
            }

            // Search suggestions when no active search
            if (searchQuery.isBlank() && appState.searchResults.isEmpty()) {
                searchSuggestionsSection(
                    recentSearches = recentSearches,
                    smartSuggestions = smartSuggestions,
                    maxRowItems = perfConfig.maxRowItems,
                    onSuggestionClick = { searchQuery = it },
                )
            }

            // Search results
            if (appState.isSearching) {
                searchingIndicatorSection()
            }

            appState.errorMessage?.let { error ->
                searchErrorSection(error)
            }

            if (appState.searchResults.isEmpty() && !appState.isSearching && appState.errorMessage == null && searchQuery.isNotBlank()) {
                noResultsSection(
                    searchQuery = searchQuery,
                    onSearchRequests = onSearchRequests,
                )
            }

            if (scopedResults.isEmpty() && appState.searchResults.isNotEmpty()) {
                noScopeResultsSection(selectedScope)
            }

            // Grouped results by type (density pass: list rows instead of a poster grid)
            groupedResultsSection(
                groupedResults = scopedResults.groupBy { it.type },
                getImageUrl = getImageUrl,
                onItemClick = onItemClick,
            )
        }

        // Floating action buttons (bottom-right). Always visible now that the search field is a
        // sticky header rather than an auto-hiding overlay.
        SearchFabColumn(
            aiSearchEnabled = aiSearchEnabled,
            onToggleAiSearch = { aiSearchEnabled = !aiSearchEnabled },
            isFilterExpanded = isFilterExpanded,
            onToggleFilters = { isFilterExpanded = !isFilterExpanded },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp), // Above MiniPlayer
        )
    }
}

/** Sticky search field + scope chip row, pinned to the top of the results list. */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.searchStickyHeader(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onClearSearch: () -> Unit,
    showScopeChips: Boolean,
    selectedScope: SearchScope,
    scopeCounts: Map<SearchScope, Int>,
    onScopeSelected: (SearchScope) -> Unit,
) {
    stickyHeader(key = "search_header") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = 8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(id = R.string.search_hint)) },
                    leadingIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                        }
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            if (showScopeChips) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        items = SearchScope.entries,
                        key = { it.name },
                        contentType = { "immersive_search_scope_chip" },
                    ) { scope ->
                        FilterChip(
                            selected = selectedScope == scope,
                            onClick = { onScopeSelected(scope) },
                            label = { Text("${scope.label} (${scopeCounts[scope] ?: 0})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Expandable "Content Types" filter card. */
private fun LazyListScope.contentTypeFiltersSection(
    selectedContentTypes: Set<BaseItemKind>,
    onToggleContentType: (BaseItemKind) -> Unit,
) {
    item(key = "filters") {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "Content Types",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    val contentTypes = listOf(
                        BaseItemKind.MOVIE to "Movies",
                        BaseItemKind.SERIES to "TV Shows",
                        BaseItemKind.AUDIO to "Music",
                        BaseItemKind.BOOK to "Books",
                        BaseItemKind.AUDIO_BOOK to "Audiobooks",
                        BaseItemKind.VIDEO to "Videos",
                    )

                    items(
                        items = contentTypes,
                        key = { (kind, _) -> kind },
                        contentType = { "immersive_search_content_type_filter" },
                    ) { (kind, label) ->
                        FilterChip(
                            selected = selectedContentTypes.contains(kind),
                            onClick = { onToggleContentType(kind) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Recent-searches + smart-suggestions chips shown before the user has searched. */
private fun LazyListScope.searchSuggestionsSection(
    recentSearches: List<String>,
    smartSuggestions: List<String>,
    maxRowItems: Int,
    onSuggestionClick: (String) -> Unit,
) {
    item(key = "suggestions") {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Recent searches
            if (recentSearches.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        items(
                            items = recentSearches.take(maxRowItems),
                            key = { it },
                            contentType = { "immersive_recent_search" },
                        ) { search ->
                            SuggestionChip(
                                onClick = { onSuggestionClick(search) },
                                label = { Text(search) },
                            )
                        }
                    }
                }
            }

            // Smart suggestions
            if (smartSuggestions.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Popular in Your Library",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        val limitedSuggestions = smartSuggestions.take(maxRowItems)
                        items(
                            count = limitedSuggestions.size,
                            key = { index -> "immersive_suggestion_$index" },
                            contentType = { "immersive_smart_suggestion" },
                        ) { index ->
                            val suggestion = limitedSuggestions[index]
                            SuggestionChip(
                                onClick = { onSuggestionClick(suggestion) },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Searching..." loading row. */
private fun LazyListScope.searchingIndicatorSection() {
    item(key = "searching") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExpressiveCircularLoading(size = 24.dp)
                Text(
                    text = "Searching...",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** Error card shown when [error] is non-null. */
private fun LazyListScope.searchErrorSection(error: String) {
    item(key = "error") {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Empty-state shown when a completed search returned zero results. */
private fun LazyListScope.noResultsSection(
    searchQuery: String,
    onSearchRequests: (String) -> Unit,
) {
    item(key = "no_results") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = "No results found",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Try a different search term or check the request catalog",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SuggestionChip(
                onClick = { onSearchRequests(searchQuery.trim()) },
                label = { Text("Search request catalog") },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** Shown when the active scope chip filters out every loaded result. */
private fun LazyListScope.noScopeResultsSection(selectedScope: SearchScope) {
    item(key = "no_scope_results") {
        Text(
            text = "No ${selectedScope.label.lowercase()} results for this search.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 32.dp),
        )
    }
}

/** Results grouped by item type (density pass: list rows instead of a poster grid). */
private fun LazyListScope.groupedResultsSection(
    groupedResults: Map<BaseItemKind?, List<BaseItemDto>>,
    getImageUrl: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
) {
    groupedResults.forEach { (type, items) ->
        item(key = "header_$type") {
            Text(
                text = when (type) {
                    BaseItemKind.MOVIE -> "Movies"
                    BaseItemKind.SERIES -> "TV Shows"
                    BaseItemKind.EPISODE -> "Episodes"
                    BaseItemKind.AUDIO -> "Music"
                    BaseItemKind.MUSIC_ALBUM -> "Albums"
                    BaseItemKind.MUSIC_ARTIST -> "Artists"
                    BaseItemKind.BOOK -> "Books"
                    BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                    else -> type.toString()
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }

        items(
            items = items,
            key = { it.getItemKey() },
            contentType = { "immersive_search_result_row" },
        ) { item ->
            SearchResultRow(
                item = item,
                imageUrl = getImageUrl(item) ?: "",
                onClick = { onItemClick(item) },
            )
        }
    }
}

/** Bottom-right floating action buttons for AI search and content-type filters. */
@Composable
private fun SearchFabColumn(
    aiSearchEnabled: Boolean,
    onToggleAiSearch: () -> Unit,
    isFilterExpanded: Boolean,
    onToggleFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // AI Search toggle FAB
        FloatingActionButton(
            onClick = onToggleAiSearch,
            containerColor = if (aiSearchEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            },
            contentColor = if (aiSearchEnabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.aiAura(enabled = aiSearchEnabled),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Search",
            )
        }

        // Filter toggle FAB
        FloatingActionButton(
            onClick = onToggleFilters,
            containerColor = if (isFilterExpanded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            },
            contentColor = if (isFilterExpanded) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Search Filters",
            )
        }
    }
}

/** All/Movies/Shows/People scope for the density-pass search scope chip row (item 8). */
private enum class SearchScope(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SHOWS("Shows"),
    PEOPLE("People"),
}

private fun matchesScope(item: BaseItemDto, scope: SearchScope): Boolean = when (scope) {
    SearchScope.ALL -> true
    SearchScope.MOVIES -> item.type == BaseItemKind.MOVIE
    SearchScope.SHOWS -> item.type == BaseItemKind.SERIES || item.type == BaseItemKind.EPISODE
    SearchScope.PEOPLE -> item.type == BaseItemKind.PERSON
}

/**
 * Density-pass search result row (item 8): a 78dp-tall row with a 44x66dp poster thumbnail,
 * title/metadata, and a trailing state slot (Watched label, or nothing).
 *
 * Note: a "NEW" badge was part of the original spec but is intentionally not implemented here -
 * BaseItemDto.dateCreated's exact type couldn't be confirmed against the Jellyfin SDK sources in
 * this sandbox (dependencies are unavailable / network-blocked), so date-arithmetic was avoided
 * rather than guessed. See the commit message.
 */
@Composable
private fun SearchResultRow(
    item: BaseItemDto,
    imageUrl: String,
    onClick: () -> Unit,
) {
    val subtitle = when (item.type) {
        BaseItemKind.EPISODE -> item.seriesName ?: ""
        else -> item.productionYear?.toString().orEmpty()
    }
    val isWatched = item.userData?.played == true

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OptimizedImage(
                imageUrl = imageUrl,
                contentDescription = item.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                size = ImageSize.THUMBNAIL,
                quality = ImageQuality.MEDIUM,
                modifier = Modifier
                    .width(44.dp)
                    .height(66.dp)
                    .clip(ImmersiveShapes.PosterImage),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            if (isWatched) {
                Text(
                    text = "Watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
