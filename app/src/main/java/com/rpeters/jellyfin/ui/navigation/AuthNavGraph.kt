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
        val viewModel: ServerConnectionViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val connectionState by viewModel.connectionState.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )
        val activity = context as? FragmentActivity
        val autoPrompted = androidx.compose.runtime.saveable.rememberSaveable(
            connectionState.savedServerUrl,
            connectionState.savedUsername,
        ) { androidx.compose.runtime.mutableStateOf(false) }

        // Navigate to Home when successfully connected
        LaunchedEffect(connectionState.isConnected) {
            if (connectionState.isConnected) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.ServerConnection.route) { inclusive = true }
                }
            }
        }
        LaunchedEffect(
            connectionState.isBiometricAuthEnabled,
            connectionState.isBiometricAuthAvailable,
            connectionState.hasSavedPassword,
            connectionState.rememberLogin,
            connectionState.isConnecting,
            connectionState.isConnected,
            connectionState.savedServerUrl,
            connectionState.savedUsername,
        ) {
            if (autoPrompted.value) return@LaunchedEffect
            if (activity == null) return@LaunchedEffect
            if (!connectionState.isBiometricAuthEnabled || !connectionState.isBiometricAuthAvailable) return@LaunchedEffect
            if (!connectionState.rememberLogin || !connectionState.hasSavedPassword) return@LaunchedEffect
            if (connectionState.savedServerUrl.isBlank() || connectionState.savedUsername.isBlank()) return@LaunchedEffect
            if (connectionState.isConnecting || connectionState.isConnected) return@LaunchedEffect
            autoPrompted.value = true
            viewModel.autoLoginWithBiometric(activity)
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
            isBiometricAuthEnabled = connectionState.isBiometricAuthEnabled,
            isBiometricAuthAvailable = connectionState.isBiometricAuthAvailable,
            requireStrongBiometric = connectionState.requireStrongBiometric,
            isUsingWeakBiometric = connectionState.isUsingWeakBiometric,
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
            onTemporarilyTrustPin = { viewModel.temporarilyTrustPin() },
            onDismissPinningAlert = { viewModel.dismissPinningAlert() },
            onRequireStrongBiometricChange = { viewModel.setRequireStrongBiometric(it) },
        )
    }

    composable(Screen.QuickConnect.route) {
        val viewModel: ServerConnectionViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
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
