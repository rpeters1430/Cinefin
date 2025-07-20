package com.example.jellyfinandroid.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.jellyfinandroid.ui.components.BottomNavBar
import com.example.jellyfinandroid.ui.navigation.JellyfinNavGraph
import com.example.jellyfinandroid.ui.navigation.Screen
import com.example.jellyfinandroid.ui.theme.JellyfinAndroidTheme
import com.example.jellyfinandroid.ui.viewmodel.ServerConnectionViewModel

@Composable
fun JellyfinApp(onLogout: () -> Unit = {}) {
    JellyfinAndroidTheme(dynamicColor = true) {
        val navController = rememberNavController()
        val connectionViewModel: ServerConnectionViewModel = hiltViewModel()
        val connectionState by connectionViewModel.connectionState.collectAsState()

        val startDestination = if (connectionState.isConnected) {
            Screen.Home.route
        } else {
            Screen.ServerConnection.route
        }

        Scaffold(
            bottomBar = {
                if (connectionState.isConnected) {
                    BottomNavBar(navController = navController)
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            JellyfinNavGraph(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
                onLogout = onLogout
            )
        }
    }
}
