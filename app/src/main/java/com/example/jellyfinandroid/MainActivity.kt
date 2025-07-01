package com.example.jellyfinandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.ui.screens.ServerConnectionScreen
import com.example.jellyfinandroid.ui.theme.JellyfinAndroidTheme

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
    var isConnected by rememberSaveable { mutableStateOf(false) }

    if (!isConnected) {
        ServerConnectionScreen(
            onConnect = { serverUrl, username, password ->
                // TODO: Implement actual connection logic
                isConnected = true
                currentDestination = AppDestinations.HOME
            },
            onQuickConnect = {
                // TODO: Implement Quick Connect
            }
        )
    } else {
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
                        onClick = { currentDestination = it }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                when (currentDestination) {
                    AppDestinations.HOME -> {
                        Text(
                            text = "Welcome to Jellyfin!",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(innerPadding).padding(24.dp)
                        )
                    }
                    AppDestinations.LIBRARY -> {
                        Text(
                            text = "Media Library",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(innerPadding).padding(24.dp)
                        )
                    }
                    AppDestinations.SEARCH -> {
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(innerPadding).padding(24.dp)
                        )
                    }
                    AppDestinations.FAVORITES -> {
                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(innerPadding).padding(24.dp)
                        )
                    }
                    AppDestinations.PROFILE -> {
                        Text(
                            text = "Profile",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(innerPadding).padding(24.dp)
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