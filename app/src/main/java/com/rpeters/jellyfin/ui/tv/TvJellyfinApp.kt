package com.rpeters.jellyfin.ui.tv

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

@Composable
fun TvServerConnectionScreen(
    onConnect: (String, String, String) -> Unit,
    onQuickConnect: () -> Unit,
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

    val focusManager = LocalFocusManager.current
    val serverUrlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val connectButtonFocusRequester = remember { FocusRequester() }
    val quickConnectButtonFocusRequester = remember { FocusRequester() }

    // Update local state when saved values change
    LaunchedEffect(savedServerUrl, savedUsername) {
        serverUrl = savedServerUrl ?: ""
        username = savedUsername ?: ""
    }

    // Auto-focus the appropriate field on start
    LaunchedEffect(Unit) {
        when {
            serverUrl.isEmpty() -> serverUrlFocusRequester.requestFocus()
            username.isEmpty() -> usernameFocusRequester.requestFocus()
            else -> passwordFocusRequester.requestFocus()
        }
    }

    // Simplified TV-optimized layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .wrapContentHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Text(
                text = "Jellyfin",
                style = TvMaterialTheme.typography.displayMedium,
                color = TvMaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Connect to your Jellyfin server",
                style = TvMaterialTheme.typography.bodyLarge,
                color = TvMaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            // Server URL
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://jellyfin.example.com") },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        usernameFocusRequester.requestFocus()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(serverUrlFocusRequester),
                enabled = !isConnecting,
            )

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        passwordFocusRequester.requestFocus()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(usernameFocusRequester),
                enabled = !isConnecting,
            )

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        connectButtonFocusRequester.requestFocus()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(passwordFocusRequester),
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
                    Text(
                        text = errorMessage,
                        color = TvMaterialTheme.colorScheme.error,
                        style = TvMaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Spacer to ensure button is visible
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                // Connect button
                Button(
                    onClick = {
                        Log.d("TvServerConnectionScreen", "Connect button clicked - serverUrl: '$serverUrl', username: '$username', password: '***'")
                        if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            Log.d("TvServerConnectionScreen", "Calling onConnect with credentials")
                            onConnect(serverUrl, username, password)
                        } else {
                            Log.w("TvServerConnectionScreen", "Cannot connect - missing credentials")
                        }
                    },
                    enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isConnecting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(connectButtonFocusRequester),
                ) {
                    if (isConnecting) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = TvMaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting...", style = TvMaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text("Sign In", style = TvMaterialTheme.typography.labelLarge)
                    }
                }

                // Quick Connect button
                Button(
                    onClick = {
                        Log.d("TvServerConnectionScreen", "Quick Connect button clicked")
                        onQuickConnect()
                    },
                    enabled = !isConnecting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(quickConnectButtonFocusRequester),
                ) {
                    Text("Quick Connect", style = TvMaterialTheme.typography.labelLarge)
                }
            }

            // Help text for Quick Connect
            Text(
                text = "Tip: Use Quick Connect to sign in without typing your password on TV",
                style = TvMaterialTheme.typography.bodySmall,
                color = TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            // Bottom spacer to ensure scrolling works properly
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun TvJellyfinApp(
    modifier: Modifier = Modifier,
) {
    // Root composable for the TV experience.
    // Hosts the navigation graph for all TV screens.
    TvMaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            TvNavGraph()
        }
    }
}
