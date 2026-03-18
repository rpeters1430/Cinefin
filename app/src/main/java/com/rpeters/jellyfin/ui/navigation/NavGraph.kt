@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

    val forwardNavigationEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            initialOffset = { fullWidth -> fullWidth / 6 },
        ) + fadeIn()
    }
    val forwardNavigationExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            targetOffset = { fullWidth -> -fullWidth / 12 },
        ) + fadeOut()
    }
    val backwardNavigationEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            initialOffset = { fullWidth -> -fullWidth / 12 },
        ) + fadeIn()
    }
    val backwardNavigationExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            targetOffset = { fullWidth -> fullWidth / 6 },
        ) + fadeOut()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = forwardNavigationEnter,
        exitTransition = forwardNavigationExit,
        popEnterTransition = backwardNavigationEnter,
        popExitTransition = backwardNavigationExit,
    ) {
        authNavGraph(navController)
        homeLibraryNavGraph(navController)
        mediaNavGraph(navController, mainViewModel)
        profileNavGraph(navController, onLogout)
        detailNavGraph(navController)
    }
}
