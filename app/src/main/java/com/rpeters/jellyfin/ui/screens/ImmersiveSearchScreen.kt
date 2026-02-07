package com.rpeters.jellyfin.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.components.MiniPlayer
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveCardSize
import com.rpeters.jellyfin.ui.components.immersive.ImmersiveMediaCard
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens
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
@OptInAppExperimentalApis
@Composable
fun ImmersiveSearchScreen(
    appState: MainAppState,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    onItemClick: (BaseItemDto) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    // Calculate adaptive layout config
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(activity = context as Activity)
    val adaptiveConfig = rememberAdaptiveLayoutConfig(windowSizeClass)

    var searchQuery by remember { mutableStateOf("") }
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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

    // Auto-hide search bar when scrolling down
    val showSearchBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 100
        }
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
                top = 100.dp, // Space for floating search bar
                bottom = 120.dp, // Space for MiniPlayer + FABs
            ),
            verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.SpacingRowTight),
        ) {
            // Content Type Filters
            if (isFilterExpanded) {
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
                                        onClick = {
                                            selectedContentTypes = if (selectedContentTypes.contains(kind)) {
                                                selectedContentTypes - kind
                                            } else {
                                                selectedContentTypes + kind
                                            }
                                        },
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

            // Search suggestions when no active search
            if (searchQuery.isBlank() && appState.searchResults.isEmpty()) {
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
                                        items = recentSearches,
                                        key = { it },
                                        contentType = { "immersive_recent_search" },
                                    ) { search ->
                                        SuggestionChip(
                                            onClick = { searchQuery = search },
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
                                    items(
                                        count = smartSuggestions.size,
                                        key = { index -> "immersive_suggestion_$index" },
                                        contentType = { "immersive_smart_suggestion" },
                                    ) { index ->
                                        val suggestion = smartSuggestions[index]
                                        SuggestionChip(
                                            onClick = { searchQuery = suggestion },
                                            label = { Text(suggestion) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search results
            if (appState.isSearching) {
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
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(
                                text = "Searching...",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            appState.errorMessage?.let { error ->
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

            if (appState.searchResults.isEmpty() && !appState.isSearching && appState.errorMessage == null && searchQuery.isNotBlank()) {
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
                            text = "Try a different search term or adjust filters",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Grouped results by type
            val groupedResults = appState.searchResults.groupBy { it.type }
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
                    contentType = { "immersive_search_result" },
                ) { item ->
                    ImmersiveMediaCard(
                        title = item.name ?: "",
                        imageUrl = getImageUrl(item) ?: "",
                        onCardClick = { onItemClick(item) },
                        subtitle = when (item.type) {
                            BaseItemKind.EPISODE -> item.seriesName ?: ""
                            else -> item.productionYear?.toString() ?: ""
                        },
                        rating = item.communityRating?.toFloat(),
                        isFavorite = item.userData?.isFavorite == true,
                        isWatched = item.userData?.played == true,
                        watchProgress = (item.userData?.playedPercentage ?: 0.0).toFloat() / 100f,
                        cardSize = ImmersiveCardSize.MEDIUM, // 280dp width
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Floating search bar (auto-hides on scroll)
        AnimatedVisibility(
            visible = showSearchBar,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300),
            ),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300),
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
            ) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onSearch = { /* Search is handled by LaunchedEffect with debouncing */ },
                            expanded = false,
                            onExpandedChange = { },
                            placeholder = { Text(stringResource(id = R.string.search_hint)) },
                            leadingIcon = {
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    onBackClick()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(id = R.string.navigate_up),
                                    )
                                }
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        onClearSearch()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear Search",
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                    modifier = Modifier.focusRequester(focusRequester),
                ) {
                    // Empty content - search results are shown in LazyColumn
                }
            }
        }

        // Floating action buttons (bottom-right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp), // Above MiniPlayer
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // AI Search toggle FAB
            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            ) {
                FloatingActionButton(
                    onClick = { aiSearchEnabled = !aiSearchEnabled },
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
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Search",
                    )
                }
            }

            // Filter toggle FAB
            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            ) {
                FloatingActionButton(
                    onClick = { isFilterExpanded = !isFilterExpanded },
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

        // MiniPlayer at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            MiniPlayer(onExpandClick = onNowPlayingClick)
        }
    }
}
