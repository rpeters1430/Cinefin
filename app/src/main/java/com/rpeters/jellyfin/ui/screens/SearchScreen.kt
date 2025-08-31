package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.utils.rememberDebouncedState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    appState: MainAppState,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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

    LaunchedEffect(debouncedQuery) {
        if (debouncedQuery.isBlank()) {
            onClearSearch()
        } else {
            onSearch(debouncedQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.search)) },
                navigationIcon = {
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
                actions = {
                    IconButton(onClick = { isFilterExpanded = !isFilterExpanded }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Search Filters",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(id = R.string.search),
                            )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
            ) {
                // Empty content - search results are shown below
            }

            // Content Type Filters
            if (isFilterExpanded) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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

                            items(contentTypes) { (kind, label) ->
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

            // Search suggestions when no active search
            if (searchQuery.isBlank() && appState.searchResults.isEmpty()) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) {
                                items(recentSearches) { search ->
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
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp),
                            ) {
                                items(smartSuggestions) { suggestion ->
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

            SearchResultsContent(
                searchResults = appState.searchResults,
                isSearching = appState.isSearching,
                errorMessage = appState.errorMessage,
                getImageUrl = getImageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
