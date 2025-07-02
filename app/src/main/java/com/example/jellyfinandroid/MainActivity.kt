package com.example.jellyfinandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.jellyfinandroid.data.JellyfinServer
import org.jellyfin.sdk.model.api.BaseItemDto
import com.example.jellyfinandroid.ui.screens.ServerConnectionScreen
import com.example.jellyfinandroid.ui.theme.JellyfinAndroidTheme
import com.example.jellyfinandroid.ui.viewmodel.MainAppState
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import com.example.jellyfinandroid.ui.viewmodel.ServerConnectionViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JellyfinAndroidTheme {
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
        ServerConnectionScreen(
            onConnect = { serverUrl, username, password ->
                connectionViewModel.connectToServer(serverUrl, username, password)
            },
            onQuickConnect = {
                connectionViewModel.startQuickConnect()
            },
            isConnecting = connectionState.isConnecting,
            errorMessage = connectionState.errorMessage
        )
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
    LIBRARY("Library", Icons.Default.List),
    SEARCH("Search", Icons.Default.Search),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JellyfinAndroidTheme {
        Greeting("Android")
    }
}

@Composable
fun HomeScreen(
    appState: MainAppState,
    currentServer: JellyfinServer?,
    onRefresh: () -> Unit,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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
        
        if (appState.libraries.isNotEmpty()) {
            item {
                Text(
                    text = "Your Libraries",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        
        if (appState.recentlyAdded.isNotEmpty()) {
            item {
                Text(
                    text = "Recently Added",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(appState.recentlyAdded.take(10)) { item ->
                        MediaCard(
                            item = item,
                            getImageUrl = getImageUrl
                        )
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

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search functionality coming soon...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
    Card(
        modifier = modifier.width(200.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            AsyncImage(
                model = getImageUrl(item),
                contentDescription = item.name ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            
            Text(
                text = item.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
                maxLines = 2
            )
        }
    }
}

@Composable
fun MediaCard(
    item: BaseItemDto,
    getImageUrl: (BaseItemDto) -> String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(140.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            AsyncImage(
                model = getImageUrl(item),
                contentDescription = item.name ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
                
                item.productionYear?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}