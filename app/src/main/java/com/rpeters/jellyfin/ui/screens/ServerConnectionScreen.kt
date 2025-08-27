package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ConnectionPhase
import com.rpeters.jellyfin.ui.components.ConnectionState
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectionScreen(
    onConnect: (String, String, String) -> Unit = { _, _, _ -> },
    onQuickConnect: () -> Unit = {},
    connectionState: ConnectionState = ConnectionState(), // Use enhanced connection state
    savedServerUrl: String = "",
    savedUsername: String = "",
    rememberLogin: Boolean = false,
    hasSavedPassword: Boolean = false,
    isBiometricAuthAvailable: Boolean = false,
    onRememberLoginChange: (Boolean) -> Unit = {},
    onAutoLogin: () -> Unit = {},
    onBiometricLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf(savedServerUrl) }
    var username by remember { mutableStateOf(savedUsername) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Update local state when saved values change
    LaunchedEffect(savedServerUrl, savedUsername) {
        serverUrl = savedServerUrl
        username = savedUsername
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Jellyfin branding
        Text(
            text = "Jellyfin",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Connect to your Jellyfin server",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-login button if we have saved credentials
        if (hasSavedPassword && rememberLogin && savedServerUrl.isNotBlank() && savedUsername.isNotBlank()) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 8.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Saved credentials found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Server: $savedServerUrl\nUser: $savedUsername",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onAutoLogin,
                        enabled = !isConnecting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Auto Login")
                    }

                    // Biometric login button if available
                    if (isBiometricAuthAvailable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onBiometricLogin,
                            enabled = !isConnecting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Login with Biometric")
                        }
                    }
                }
            }

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
        }

        // Server URL input
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://jellyfin.example.com") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
        )

        // Username input
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
        )

        // Password input
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                        onConnect(serverUrl, username, password)
                    }
                },
            ),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            enabled = !isConnecting,
        )

        // Remember login toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = rememberLogin,
                onCheckedChange = { onRememberLoginChange(it) },
                enabled = !isConnecting,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Remember login")
        }

        // Show helper text when saved credentials are available but no password
        if (savedServerUrl.isNotBlank() && savedUsername.isNotBlank() && rememberLogin && !hasSavedPassword) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 4.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Saved credentials found. Just enter your password to connect.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Error message
        if (errorMessage != null) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 6.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Connect button
        Button(
            onClick = {
                keyboardController?.hide()
                onConnect(serverUrl, username, password)
            },
            enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !connectionState.isConnecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (connectionState.isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connecting...")
            } else {
                Text("Connect")
            }
        }

        // Quick Connect button
        OutlinedButton(
            onClick = onQuickConnect,
            enabled = !connectionState.isConnecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Quick Connect")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun ServerConnectionScreenPreview() {
    JellyfinAndroidTheme {
        ServerConnectionScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun ServerConnectionScreenConnectingPreview() {
    JellyfinAndroidTheme {
        ServerConnectionScreen(
            connectionState = ConnectionState(
                isConnecting = true,
                connectionPhase = ConnectionPhase.Testing,
                currentUrl = "https://jellyfin.example.com",
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ServerConnectionScreenErrorPreview() {
    JellyfinAndroidTheme {
        ServerConnectionScreen(
            connectionState = ConnectionState(
                errorMessage = "Failed to connect to server. Please check your server URL and credentials.",
                connectionPhase = ConnectionPhase.Error,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickConnectScreen(
    onConnect: () -> Unit = {},
    onCancel: () -> Unit = {},
    isConnecting: Boolean = false,
    errorMessage: String? = null,
    serverUrl: String = "",
    code: String = "",
    isPolling: Boolean = false,
    status: String = "",
    onServerUrlChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Header
        Text(
            text = stringResource(id = R.string.quick_connect_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(id = R.string.quick_connect_instruction),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Server URL input
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text(stringResource(id = R.string.quick_connect_server_url_label)) },
            placeholder = { Text(stringResource(id = R.string.quick_connect_server_url_placeholder)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (serverUrl.isNotBlank()) {
                        onConnect()
                    }
                },
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting && !isPolling,
        )

        // Status message
        if (status.isNotBlank()) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isPolling) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = if (isPolling) 6.dp else 4.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = status,
                    color = if (isPolling) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Quick Connect Code display
        if (code.isNotBlank()) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 8.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(id = R.string.quick_connect_code_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = code,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.quick_connect_enter_code_instruction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Error message
        if (errorMessage != null) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 6.dp,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Get Code button
        Button(
            onClick = {
                keyboardController?.hide()
                onConnect()
            },
            enabled = !isConnecting && !isPolling && serverUrl.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.quick_connect_connecting))
            } else if (isPolling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.quick_connect_waiting))
            } else {
                Text(stringResource(id = R.string.quick_connect_get_code))
            }
        }

        // Cancel button
        OutlinedButton(
            onClick = onCancel,
            enabled = !isConnecting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(id = R.string.cancel))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Help text
        Text(
            text = stringResource(id = R.string.quick_connect_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun QuickConnectScreenPreview() {
    JellyfinAndroidTheme {
        QuickConnectScreen()
    }
}
