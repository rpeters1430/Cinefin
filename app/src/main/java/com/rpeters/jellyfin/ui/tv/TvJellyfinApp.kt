package com.rpeters.jellyfin.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
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
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            // TV-specific navigation host (Phase 1 Step 1.1)
            TvNavGraph()
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

    val focusManager = LocalFocusManager.current
    val serverUrlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

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

    val screenH = LocalConfiguration.current.screenHeightDp.dp

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .imePadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.tv.material3.Card(
            onClick = { /* No-op, card is not clickable */ },
            modifier = Modifier
                .fillMaxWidth(0.8f) // Increase width slightly for better visibility
                .heightIn(max = screenH - 120.dp) // Add more margin
                .padding(0.dp),
            colors = androidx.tv.material3.CardDefaults.colors(
                containerColor = TvMaterialTheme.colorScheme.surface,
            ),
            border = androidx.tv.material3.CardDefaults.border(
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = TvMaterialTheme.colorScheme.primary,
                    ),
                ),
            ),
        ) {
            val scrollState = rememberScrollState()
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(scrollState),
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
                    maxLines = 1,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onNext = { usernameFocusRequester.requestFocus() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(serverUrlFocusRequester)
                        .tvKeyboardHandler(focusManager = focusManager),
                    enabled = !isConnecting,
                )

                // Username
                androidx.compose.material3.OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { androidx.compose.material3.Text("Username") },
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                        .tvKeyboardHandler(focusManager = focusManager),
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
                    maxLines = 1,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                onConnect(serverUrl, username, password)
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .tvKeyboardHandler(focusManager = focusManager),
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
                        focusManager.clearFocus()
                        if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            onConnect(serverUrl, username, password)
                        }
                    },
                    enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isConnecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvKeyboardHandler(
                            focusManager = focusManager,
                            onBack = { focusManager.clearFocus() },
                        ),
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
