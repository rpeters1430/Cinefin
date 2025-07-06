package com.example.jellyfinandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jellyfinandroid.data.JellyfinServer
import com.example.jellyfinandroid.ui.navigation.AppDestinations
import com.example.jellyfinandroid.ui.screens.FavoritesScreen
import com.example.jellyfinandroid.ui.screens.HomeScreen
import com.example.jellyfinandroid.ui.screens.LibraryScreen
import com.example.jellyfinandroid.ui.screens.LibraryTypeScreen
import com.example.jellyfinandroid.ui.screens.ProfileScreen
import com.example.jellyfinandroid.ui.screens.SearchScreen
import com.example.jellyfinandroid.ui.screens.ServerConnectionScreen
import com.example.jellyfinandroid.ui.screens.QuickConnectScreen
import com.example.jellyfinandroid.ui.screens.TVSeasonScreen
import com.example.jellyfinandroid.ui.theme.JellyfinAndroidTheme
import com.example.jellyfinandroid.ui.viewmodel.MainAppViewModel
import com.example.jellyfinandroid.ui.viewmodel.ServerConnectionViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.jellyfin.sdk.model.api.BaseItemDto

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
        
        // Navigation state for TV Season screen
        var selectedSeriesId by rememberSaveable { mutableStateOf<String?>(null) }
        var showTVSeasonScreen by rememberSaveable { mutableStateOf(false) }

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
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                when (currentDestination) {
                    AppDestinations.HOME -> {
                        HomeScreen(
                            appState = appState,
                            currentServer = currentServer,
                            onRefresh = { mainViewModel.loadInitialData() },
                            onSearch = { query -> mainViewModel.search(query) },
                            onClearSearch = { mainViewModel.clearSearch() },
                            getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                            getSeriesImageUrl = { item -> mainViewModel.getSeriesImageUrl(item) },
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
                    AppDestinations.MOVIES -> {
                        LibraryTypeScreen(
                            libraryType = com.example.jellyfinandroid.ui.screens.LibraryType.MOVIES,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.TV_SHOWS -> {
                        if (showTVSeasonScreen && selectedSeriesId != null) {
                            TVSeasonScreen(
                                seriesId = selectedSeriesId!!,
                                onBackClick = {
                                    showTVSeasonScreen = false
                                    selectedSeriesId = null
                                },
                                getImageUrl = { item -> mainViewModel.getImageUrl(item) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            LibraryTypeScreen(
                                libraryType = com.example.jellyfinandroid.ui.screens.LibraryType.TV_SHOWS,
                                onTVShowClick = { seriesId ->
                                    selectedSeriesId = seriesId
                                    showTVSeasonScreen = true
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                    AppDestinations.MUSIC -> {
                        LibraryTypeScreen(
                            libraryType = com.example.jellyfinandroid.ui.screens.LibraryType.MUSIC,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    AppDestinations.STUFF -> {
                        LibraryTypeScreen(
                            libraryType = com.example.jellyfinandroid.ui.screens.LibraryType.STUFF,
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
