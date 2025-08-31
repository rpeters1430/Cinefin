package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.rpeters.jellyfin.ui.screens.tv.TvHomeScreen
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

/**
 * Root composable for the Android TV experience.
 *
 * @param onLogout callback when the user logs out.
 * @param useDynamicColor whether to apply dynamic colors on Android 12+ devices. Enabled by default.
 */
@Composable
fun TvJellyfinApp(
    onLogout: () -> Unit = {},
    useDynamicColor: Boolean = true,
) {
    JellyfinAndroidTheme(dynamicColor = useDynamicColor) {
        val navController = rememberNavController()
        val connectionViewModel: ServerConnectionViewModel = hiltViewModel()
        // Simplified approach for now
        val startDestination = "server_connection"

        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            // For TV, we'll simplify the navigation and use a more direct approach
            if (false) { // Disable for now
                TvHomeScreen(
                    onItemSelect = { itemId ->
                        // Handle item selection
                    },
                    onLibrarySelect = { libraryId ->
                        // Handle library selection
                    },
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                TvServerConnectionScreen(
                    onConnect = { serverUrl, username, password ->
                        connectionViewModel.connectToServer(serverUrl, username, password)
                    },
                    isConnecting = false,
                    errorMessage = null,
                    savedServerUrl = "",
                    savedUsername = "",
                )
            }
        }
    }
}

@Composable
fun TvServerConnectionScreen(
    onConnect: (String, String, String) -> Unit,
    isConnecting: Boolean,
    errorMessage: String?,
    savedServerUrl: String?,
    savedUsername: String?,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf(savedServerUrl ?: "") }
    var username by remember { mutableStateOf(savedUsername ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Update local state when saved values change
    LaunchedEffect(savedServerUrl, savedUsername) {
        serverUrl = savedServerUrl ?: ""
        username = savedUsername ?: ""
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.tv.material3.Card(
            onClick = { /* No-op, card is not clickable */ },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(24.dp),
            colors = androidx.tv.material3.CardDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surface,
            ),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
            ) {
                // Header
                TvText(
                    text = "Jellyfin",
                    style = TvMaterialTheme.typography.displayMedium,
                    color = TvMaterialTheme.colorScheme.primary,
                )

                TvText(
                    text = "Connect to your Jellyfin server",
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = TvMaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                // Server URL
                androidx.compose.material3.OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { androidx.compose.material3.Text("Server URL") },
                    placeholder = { androidx.compose.material3.Text("https://jellyfin.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                )

                // Username
                androidx.compose.material3.OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { androidx.compose.material3.Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                )

                // Password
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { androidx.compose.material3.Text("Password") },
                    visualTransformation = if (showPassword) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        androidx.compose.material3.IconButton(
                            onClick = { showPassword = !showPassword },
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                )

                // Error message
                if (errorMessage != null) {
                    androidx.tv.material3.Card(
                        onClick = { /* No-op */ },
                        colors = androidx.tv.material3.CardDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TvText(
                            text = errorMessage,
                            color = TvMaterialTheme.colorScheme.error,
                            style = TvMaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                // Connect button
                androidx.tv.material3.Button(
                    onClick = {
                        if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            onConnect(serverUrl, username, password)
                        }
                    },
                    enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isConnecting) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TvMaterialTheme.colorScheme.onPrimary,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                        TvText("Connecting...")
                    } else {
                        TvText("Connect")
                    }
                }
            }
        }
    }
}
