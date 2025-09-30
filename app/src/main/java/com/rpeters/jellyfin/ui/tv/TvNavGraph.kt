package com.rpeters.jellyfin.ui.tv

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rpeters.jellyfin.ui.player.tv.TvVideoPlayerRoute
import com.rpeters.jellyfin.ui.screens.tv.TvHomeScreen
import com.rpeters.jellyfin.ui.screens.tv.TvItemDetailScreen
import com.rpeters.jellyfin.ui.screens.tv.TvLibraryScreen
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel

private object TvRoutes {
    const val ServerConnection = "tv_server_connection"
    const val Home = "tv_home"
    const val Library = "tv_library/{libraryId}"
    const val Item = "tv_item/{itemId}"
    const val Player = "tv_player/{itemId}?itemName={itemName}&startPosition={startPosition}"

    fun playerRoute(itemId: String, itemName: String, startPosition: Long = 0L): String {
        val encodedName = Uri.encode(itemName)
        return "tv_player/$itemId?itemName=$encodedName&startPosition=$startPosition"
    }
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
            val lifecycleOwner = LocalLifecycleOwner.current
            val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED,
            )

            // Navigate to Home when successfully connected
            LaunchedEffect(connectionState.isConnected, connectionState.isConnecting, connectionState.errorMessage) {
                Log.d("TvNavGraph", "Connection state changed - isConnected: ${connectionState.isConnected}, isConnecting: ${connectionState.isConnecting}, error: ${connectionState.errorMessage}")
                if (connectionState.isConnected) {
                    Log.d("TvNavGraph", "Navigating to TV home screen")
                    navController.navigate(TvRoutes.Home) {
                        popUpTo(TvRoutes.ServerConnection) { inclusive = true }
                    }
                }
            }

            TvServerConnectionScreen(
                onConnect = { serverUrl, username, password ->
                    connectionViewModel.connectToServer(serverUrl, username, password)
                },
                isConnecting = connectionState.isConnecting,
                errorMessage = connectionState.errorMessage,
                savedServerUrl = connectionState.savedServerUrl,
                savedUsername = connectionState.savedUsername,
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
            TvItemDetailScreen(
                itemId = itemId,
                onPlay = { id, name, startMs ->
                    navController.navigate(TvRoutes.playerRoute(id, name, startMs))
                },
            )
        }

        composable(
            route = TvRoutes.Player,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("itemName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("startPosition") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            if (itemId != null) {
                val itemName = backStackEntry.arguments?.getString("itemName").orEmpty()
                val startPosition = backStackEntry.arguments?.getLong("startPosition") ?: 0L
                TvVideoPlayerRoute(
                    itemId = itemId,
                    itemName = itemName,
                    startPositionMs = startPosition,
                    onExit = { navController.popBackStack() },
                )
            } else {
                navController.popBackStack()
            }
        }
    }
}
