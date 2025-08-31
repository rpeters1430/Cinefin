package com.rpeters.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.ui.screens.tv.TvHomeScreen
import com.rpeters.jellyfin.ui.screens.tv.TvItemDetailScreen
import com.rpeters.jellyfin.ui.screens.tv.TvLibraryScreen
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel

private object TvRoutes {
    const val ServerConnection = "tv_server_connection"
    const val Home = "tv_home"
    const val Library = "tv_library/{libraryId}"
    const val Item = "tv_item/{itemId}"
}

@Composable
fun TvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = TvRoutes.ServerConnection,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(TvRoutes.ServerConnection) {
            val connectionViewModel: ServerConnectionViewModel = hiltViewModel()
            TvServerConnectionScreen(
                onConnect = { serverUrl, username, password ->
                    connectionViewModel.connectToServer(serverUrl, username, password)
                    // Navigate when connection state flips; simple optimistic nav for now
                    navController.navigate(TvRoutes.Home) {
                        popUpTo(TvRoutes.ServerConnection) { inclusive = true }
                    }
                },
                isConnecting = false,
                errorMessage = null,
                savedServerUrl = null,
                savedUsername = null,
            )
        }

        composable(TvRoutes.Home) {
            TvHomeScreen(
                onItemSelect = { itemId ->
                    navController.navigate("tv_item/$itemId")
                },
                onLibrarySelect = { libraryId ->
                    navController.navigate("tv_library/$libraryId")
                },
            )
        }

        composable(TvRoutes.Library) { backStackEntry ->
            val libraryId = backStackEntry.arguments?.getString("libraryId")
            TvLibraryScreen(
                libraryId = libraryId,
                onItemSelect = { itemId -> navController.navigate("tv_item/$itemId") },
            )
        }

        composable(TvRoutes.Item) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            TvItemDetailScreen(itemId = itemId)
        }
    }
}
