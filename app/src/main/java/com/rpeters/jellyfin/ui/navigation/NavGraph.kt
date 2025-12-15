@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.viewmodel.MainAppViewModel

@OptInAppExperimentalApis
@androidx.media3.common.util.UnstableApi
@Composable
fun JellyfinNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.ServerConnection.route,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
) {
    val mainViewModel: MainAppViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        authNavGraph(navController)
        homeLibraryNavGraph(navController)
        mediaNavGraph(navController, mainViewModel)
        profileNavGraph(navController, onLogout)
        detailNavGraph(navController)
    }
}
