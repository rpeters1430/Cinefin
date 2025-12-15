@file:Suppress("DEPRECATION")

package com.rpeters.jellyfin.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.screens.QuickConnectScreen
import com.rpeters.jellyfin.ui.screens.ServerConnectionScreen
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel

/**
 * Authentication and connection routes.
 */
fun androidx.navigation.NavGraphBuilder.authNavGraph(
    navController: NavHostController,
) {
    composable(Screen.ServerConnection.route) {
        val viewModel: ServerConnectionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val connectionState by viewModel.connectionState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        // Navigate to Home when successfully connected
        LaunchedEffect(connectionState.isConnected) {
            if (connectionState.isConnected) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.ServerConnection.route) { inclusive = true }
                }
            }
        }

        ServerConnectionScreen(
            onConnect = { serverUrl, username, password ->
                viewModel.connectToServer(serverUrl, username, password)
            },
            onQuickConnect = {
                navController.navigate(Screen.QuickConnect.route)
            },
            connectionState = connectionState,
            savedServerUrl = connectionState.savedServerUrl,
            savedUsername = connectionState.savedUsername,
            rememberLogin = connectionState.rememberLogin,
            hasSavedPassword = connectionState.hasSavedPassword,
            isBiometricAuthAvailable = connectionState.isBiometricAuthAvailable,
            onRememberLoginChange = { viewModel.setRememberLogin(it) },
            onAutoLogin = { viewModel.autoLogin() },
            onBiometricLogin = {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    viewModel.autoLoginWithBiometric(activity)
                } else {
                    viewModel.showError(context.getString(R.string.biometric_activity_error))
                }
            },
        )
    }

    composable(Screen.QuickConnect.route) {
        val viewModel: ServerConnectionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        val lifecycleOwner = LocalLifecycleOwner.current
        val connectionState by viewModel.connectionState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )

        // Navigate to Home when successfully connected
        LaunchedEffect(connectionState.isConnected) {
            if (connectionState.isConnected) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.ServerConnection.route) { inclusive = true }
                }
            }
        }

        QuickConnectScreen(
            connectionState = connectionState,
            onConnect = { viewModel.initiateQuickConnect() },
            onCancel = {
                viewModel.cancelQuickConnect()
                navController.popBackStack()
            },
            onServerUrlChange = { url -> viewModel.updateQuickConnectServerUrl(url) },
        )
    }
}
