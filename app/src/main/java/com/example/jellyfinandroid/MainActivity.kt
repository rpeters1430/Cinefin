package com.example.jellyfinandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.ui.screens.ServerConnectionScreen
import com.example.jellyfinandroid.ui.screens.QuickConnectScreen
import com.example.jellyfinandroid.ui.theme.JellyfinAndroidTheme
import com.example.jellyfinandroid.ui.theme.getContentTypeColor
import com.example.jellyfinandroid.ui.theme.getQualityColor
import com.example.jellyfinandroid.ui.theme.getStatusColor
import com.example.jellyfinandroid.ui.theme.MovieRed
import com.example.jellyfinandroid.ui.theme.SeriesBlue
import com.example.jellyfinandroid.ui.theme.MusicGreen
import com.example.jellyfinandroid.ui.theme.BookPurple
import com.example.jellyfinandroid.ui.theme.AudioBookOrange
import com.example.jellyfinandroid.ui.theme.PhotoYellow
import com.example.jellyfinandroid.ui.theme.RatingGold
import com.example.jellyfinandroid.ui.theme.Quality4K
import com.example.jellyfinandroid.ui.theme.QualityHD
import com.example.jellyfinandroid.ui.theme.QualitySD
import com.example.jellyfinandroid.ui.theme.RatingSilver
import com.example.jellyfinandroid.ui.theme.RatingBronze
import com.example.jellyfinandroid.ui.viewmodel.MainAppState
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import com.example.jellyfinandroid.ui.viewmodel.ServerConnectionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.Locale
import com.example.jellyfinandroid.ui.ShimmerBox

// Quality badge helper must be at the very top for visibility
fun getQualityLabel(item: BaseItemDto): Pair<String, Color>? {
    val mediaSource = item.mediaSources?.firstOrNull() ?: return null
    val videoStream = mediaSource.mediaStreams?.firstOrNull { (it.type as? String)?.lowercase(Locale.ROOT) == "video" }
    val width = videoStream?.width ?: 0
    return when {
        width >= 3800 -> "4K" to Quality4K
        width >= 1900 -> "HD" to QualityHD
        width > 0 -> "SD" to QualitySD
        mediaSource.container?.contains("4k", ignoreCase = true) == true -> "4K" to Quality4K
        mediaSource.container?.contains("hd", ignoreCase = true) == true -> "HD" to QualityHD
        else -> null
    }
}

@AndroidEntryPoint

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JellyfinAndroidTheme(
                dynamicColor = true
            ) {
                JellyfinAndroidApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun JellyfinAndroidApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.CONNECT) }
    val connectionViewModel: ServerConnectionViewModel = hiltViewModel()
    val connectionState by connectionViewModel.connectionState.collectAsState()

    if (!connectionState.isConnected) {
        if (connectionState.isQuickConnectActive) {
            QuickConnectScreen(
                onConnect = {
                    connectionViewModel.initiateQuickConnect()
                },
                onCancel = {
                    connectionViewModel.cancelQuickConnect()
                },
                isConnecting = connectionState.isConnecting,
                errorMessage = connectionState.errorMessage,
                serverUrl = connectionState.quickConnectServerUrl,
                code = connectionState.quickConnectCode,
                isPolling = connectionState.isQuickConnectPolling,
                status = connectionState.quickConnectStatus,
                onServerUrlChange = { serverUrl: String -> connectionViewModel.updateQuickConnectServerUrl(serverUrl) }
            )
        } else {
            ServerConnectionScreen(
                onConnect = { serverUrl, username, password ->
                    connectionViewModel.connectToServer(serverUrl, username, password)
                },
                onQuickConnect = {
                    connectionViewModel.startQuickConnect()
                },
                isConnecting = connectionState.isConnecting,
                errorMessage = connectionState.errorMessage,
                savedServerUrl = connectionState.savedServerUrl,
                savedUsername = connectionState.savedUsername,
                rememberLogin = connectionState.rememberLogin,
                onRememberLoginChange = { connectionViewModel.setRememberLogin(it) }
            )
        }
    } else {
        currentDestination = AppDestinations.HOME
        val mainViewModel: MainAppViewModel = hiltViewModel()
        val appState by mainViewModel.appState.collectAsState()
        val currentServer by mainViewModel.currentServer.collectAsState(initial = null)

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.filter { it != AppDestinations.CONNECT }.forEach {
                    item(
                        icon = {
                            Icon(
                                it.icon,
                                contentDescription = it.label
                            )
                        },
                        label = { Text(it.label) },
                        selected = it == currentDestination,
                        onClick = {
                            currentDestination = it
                            if (it == AppDestinations.FAVORITES) {
                                mainViewModel.loadFavorites()
                            }
                        }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (currentDestination) {
                    AppDestinations.HOME -> {
                        HomeScreen(
                            appState = appState,
                            currentServer = currentServer,
                            onRefresh = { mainViewModel.loadInitialData() },
                            onSearch = { query -> mainViewModel.search(query) },
                            onClearSearch = { mainViewModel.clearSearch() },
                            getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.LIBRARY -> {
                        LibraryScreen(
                            libraries = appState.libraries,
                            isLoading = appState.isLoading,
                            errorMessage = appState.errorMessage,
                            onRefresh = { mainViewModel.loadInitialData() },
                            getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.SEARCH -> {
                        SearchScreen(
                            appState = appState,
                            onSearch = { query -> mainViewModel.search(query) },
                            onClearSearch = { mainViewModel.clearSearch() },
                            getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.FAVORITES -> {
                        FavoritesScreen(
                            favorites = appState.favorites,
                            isLoading = appState.isLoading,
                            errorMessage = appState.errorMessage,
                            onRefresh = { mainViewModel.loadFavorites() },
                            getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.PROFILE -> {
                        ProfileScreen(
                            currentServer = currentServer,
                            onLogout = { mainViewModel.logout() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.CONNECT -> {
                        // This shouldn't happen when connected
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    CONNECT("Connect", Icons.Default.Home), // Hidden from navigation
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.AutoMirrored.Filled.List),
    SEARCH("Search", Icons.Default.Search),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}


@Composable
fun SearchResultsContent(
    searchResults: List<BaseItemDto>,
    isSearching: Boolean,
    errorMessage: String?,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isSearching) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = "Searching...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        errorMessage?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (searchResults.isEmpty() && !isSearching && errorMessage == null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Try searching with different keywords",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Group results by type
        val groupedResults = searchResults.groupBy { it.type }

        groupedResults.forEach { (type, items) ->
            item {
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
                        else -> type?.toString() ?: "Other"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(items.chunked(2)) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { item ->
                        MediaCard(
                            item = item,
                            getImageUrl = getImageUrl,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining space if odd number of items
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Top App Bar with Search
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = appState.searchQuery,
                    onValueChange = onSearch,
                    placeholder = {
                        Text(
                            text = "Search movies, shows, music...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (appState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Content
        if (appState.searchQuery.isNotEmpty()) {
            // Show search results
            SearchResultsContent(
                searchResults = appState.searchResults,
                isSearching = appState.isSearching,
                errorMessage = appState.errorMessage,
                getImageUrl = getImageUrl,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Show regular home content
            HomeContent(
                appState = appState,
                currentServer = currentServer,
                onRefresh = onRefresh,
                getImageUrl = getImageUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun HomeContent(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header Section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Welcome back!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        currentServer?.let { server ->
                            Text(
                                text = "Connected to ${server.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            }
        }

        if (appState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        appState.errorMessage?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Recently Added Carousel
        if (appState.recentlyAdded.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Added",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Recently Added"
                            )
                        }
                    }

                    RecentlyAddedCarousel(
                        items = appState.recentlyAdded.take(10),
                        getImageUrl = getImageUrl,
                        modifier = Modifier.height(280.dp)
                    )
                }
            }
        }

        // Libraries Section
        if (appState.libraries.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Your Libraries",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(appState.libraries) { library ->
                            LibraryCard(
                                item = library,
                                getImageUrl = getImageUrl
                            )
                        }
                    }
                }
            }
        }

        // Recently Added by Library Type Sections
        if (appState.recentlyAdded.isNotEmpty()) {
            // Group recently added items by library type
            val groupedItems = appState.recentlyAdded.groupBy { item ->
                when (item.type) {
                    BaseItemKind.MOVIE -> "Movies"
                    BaseItemKind.SERIES -> "TV Shows"
                    BaseItemKind.EPISODE -> "Episodes"
                    BaseItemKind.AUDIO -> "Music"
                    BaseItemKind.MUSIC_ALBUM -> "Albums"
                    BaseItemKind.MUSIC_ARTIST -> "Artists"
                    BaseItemKind.BOOK -> "Books"
                    BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                    else -> "Other"
                }
            }

            // Display each library type section
            groupedItems.forEach { (libraryType, items) ->
                if (items.isNotEmpty()) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recently Added $libraryType",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                IconButton(onClick = onRefresh) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh $libraryType"
                                    )
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(items.take(8)) { item ->
                                    RecentlyAddedCard(
                                        item = item,
                                        getImageUrl = getImageUrl
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    libraries: List<BaseItemDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Media Libraries",
                style = MaterialTheme.typography.headlineMedium
            )

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            libraries.isEmpty() -> {
                Text(
                    text = "No libraries found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(libraries) { library ->
                        LibraryCard(
                            item = library,
                            getImageUrl = getImageUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    appState: MainAppState,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input
        OutlinedTextField(
            value = appState.searchQuery,
            onValueChange = onSearch,
            placeholder = {
                Text(
                    text = "Search movies, shows, music, books...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (appState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Search Results
        SearchResultsContent(
            searchResults = appState.searchResults,
            isSearching = appState.isSearching,
            errorMessage = appState.errorMessage,
            getImageUrl = getImageUrl,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<BaseItemDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.headlineMedium
            )

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            favorites.isEmpty() -> {
                Text(
                    text = "No favorites yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favorites) { favorite ->
                        MediaCard(
                            item = favorite,
                            getImageUrl = getImageUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    currentServer: JellyfinServer?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        currentServer?.let { server ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "User: ${server.username ?: "Unknown"}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Server: ${server.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "URL: ${server.url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Add logout functionality later
        Text(
            text = "Profile management coming soon...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LibraryCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    val contentTypeColor = getContentTypeColor(item.collectionType?.toString())

    Card(
        modifier = modifier.width(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Box {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                // Add library type badge with semantic color
                item.collectionType?.let { collectionType ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = contentTypeColor.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = collectionType.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Item count badge
                item.childCount?.let { count ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$count items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name ?: "Unknown Library",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                item.childCount?.let { count ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$count items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MediaCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    val contentTypeColor = getContentTypeColor(item.type?.toString())

    Card(
        modifier = modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Box {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(12.dp)
                )

                // Content type badge with semantic color
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = contentTypeColor.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (item.type) {
                            BaseItemKind.MOVIE -> "Movie"
                            BaseItemKind.SERIES -> "Series"
                            BaseItemKind.EPISODE -> "Episode"
                            BaseItemKind.AUDIO -> "Music"
                            BaseItemKind.MUSIC_ALBUM -> "Album"
                            BaseItemKind.MUSIC_ARTIST -> "Artist"
                            BaseItemKind.BOOK -> "Book"
                            BaseItemKind.AUDIO_BOOK -> "Audiobook"
                            else -> "Media"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Add favorite indicator if applicable
                item.userData?.isFavorite?.let { isFavorite ->
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(16.dp),
                            tint = Color.Red
                        )
                    }
                }

                getQualityLabel(item)?.let { (label, color) ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name ?: "Unknown Title",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item.communityRating?.let { rating ->
                        val animatedRating by animateFloatAsState(
                            targetValue = rating.toFloat(),
                            label = "rating_anim"
                        )
                        val ratingColor = when {
                            rating >= 7.5f -> RatingGold
                            rating >= 5.0f -> RatingSilver
                            else -> RatingBronze
                        }
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedRating / 10f },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = ratingColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = ratingColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentlyAddedCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    val contentTypeColor = getContentTypeColor(item.type?.toString())

    Card(
        modifier = modifier.width(140.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Box {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(12.dp)
                )

                // Add favorite indicator if applicable
                item.userData?.isFavorite?.let { isFavorite ->
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(16.dp),
                            tint = Color.Red
                        )
                    }
                }

                // Content type badge with semantic color
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = contentTypeColor.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (item.type) {
                            BaseItemKind.MOVIE -> "Movie"
                            BaseItemKind.SERIES -> "Series"
                            BaseItemKind.EPISODE -> "Episode"
                            BaseItemKind.AUDIO -> "Music"
                            BaseItemKind.MUSIC_ALBUM -> "Album"
                            BaseItemKind.MUSIC_ARTIST -> "Artist"
                            BaseItemKind.BOOK -> "Book"
                            BaseItemKind.AUDIO_BOOK -> "Audiobook"
                            else -> "Media"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                getQualityLabel(item)?.let { (label, color) ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name ?: "Unknown Title",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item.communityRating?.let { rating ->
                        val animatedRating by animateFloatAsState(
                            targetValue = rating.toFloat(),
                            label = "rating_anim"
                        )
                        val ratingColor = when {
                            rating >= 7.5f -> RatingGold
                            rating >= 5.0f -> RatingSilver
                            else -> RatingBronze
                        }
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { animatedRating / 10f },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = ratingColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = ratingColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyAddedCarousel(
    items: List<BaseItemDto>,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val carouselState = rememberCarouselState { items.size }
    var currentItem by rememberSaveable { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Note: CarouselState doesn't have firstVisibleItemIndex like LazyListState
    // For Material 3 Carousel, we track the current item differently

    Column(modifier = modifier) {
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 320.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(horizontal = 32.dp)
        ) { index ->
            CarouselItemCard(
                item = items[index],
                getImageUrl = getImageUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Page indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { index ->
                val isSelected = index == currentItem
                val animatedWidth by animateDpAsState(
                    targetValue = if (isSelected) 32.dp else 8.dp,
                    animationSpec = tween(300),
                    label = "indicator_width"
                )
                val animatedColor by animateColorAsState(
                    targetValue = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "indicator_color"
                )
                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.3f else 0.9f,
                    animationSpec = spring(stiffness = 400f),
                    label = "indicator_scale"
                )
                val animatedAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.5f,
                    animationSpec = tween(300),
                    label = "indicator_alpha"
                )

                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(animatedWidth)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            alpha = animatedAlpha
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(animatedColor)
                        .clickable {
                            // Update current item and scroll to the selected item
                            currentItem = index
                            coroutineScope.launch {
                                carouselState.animateScrollToItem(index)
                            }
                        }
                )

                if (index < items.size - 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }
    }
}

@Composable
fun CarouselItemCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    val contentTypeColor = getContentTypeColor(item.type?.toString())

    Card(
        modifier = modifier
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box {
            // Background Image
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                shape = RoundedCornerShape(12.dp)
            )

            // Content type badge with semantic color
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = contentTypeColor.copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (item.type) {
                        BaseItemKind.MOVIE -> "Movie"
                        BaseItemKind.SERIES -> "Series"
                        BaseItemKind.EPISODE -> "Episode"
                        BaseItemKind.AUDIO -> "Music"
                        BaseItemKind.MUSIC_ALBUM -> "Album"
                        BaseItemKind.MUSIC_ARTIST -> "Artist"
                        BaseItemKind.BOOK -> "Book"
                        BaseItemKind.AUDIO_BOOK -> "Audiobook"
                        else -> "Media"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            ) {
                // Content Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = item.name ?: "Unknown Title",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.productionYear?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        item.communityRating?.let { rating ->
                            val animatedRating by animateFloatAsState(
                                targetValue = rating.toFloat(),
                                label = "rating_anim"
                            )
                            val ratingColor = when {
                                rating >= 7.5f -> RatingGold
                                rating >= 5.0f -> RatingSilver
                                else -> RatingBronze
                            }
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { animatedRating / 10f },
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = ratingColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = String.format("%.1f", rating),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ratingColor
                                )
                            }
                        }

                        item.officialRating?.let { rating ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = contentTypeColor.copy(alpha = 0.8f)
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = rating,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    item.overview?.let { overview ->
                        if (overview.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                getQualityLabel(item)?.let { (label, color) ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
