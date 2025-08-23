package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.core.constants.Constants
import com.rpeters.jellyfin.ui.viewmodel.MainAppState
import com.rpeters.jellyfin.utils.rememberDebouncedState
import org.jellyfin.sdk.model.api.BaseItemDto

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
    val debouncedQuery = rememberDebouncedState(
        value = searchQuery,
        delayMs = Constants.SEARCH_DEBOUNCE_MS,
    )

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

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
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { /* Search is handled by LaunchedEffect with debouncing */ },
                active = false,
                onActiveChange = { },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(id = R.string.search),
                    )
                },
                placeholder = { Text(stringResource(id = R.string.search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
            ) {
                // Empty content - search results are shown below
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
