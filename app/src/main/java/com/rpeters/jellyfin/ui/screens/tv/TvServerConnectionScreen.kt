package com.rpeters.jellyfin.ui.screens.tv

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

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
    var passwordFocused by remember { mutableStateOf(false) }

    val serverUrlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val connectButtonFocusRequester = remember { FocusRequester() }
    val quickConnectButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(savedServerUrl, savedUsername) {
        serverUrl = savedServerUrl ?: ""
        username = savedUsername ?: ""
    }

    LaunchedEffect(Unit) {
        when {
            serverUrl.isEmpty() -> serverUrlFocusRequester.requestFocus()
            username.isEmpty() -> usernameFocusRequester.requestFocus()
            else -> passwordFocusRequester.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TvImmersiveBackground(backdropUrl = null)

        Box(
            modifier = Modifier
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
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                TvText(
                    text = "Cinefin",
                    style = TvMaterialTheme.typography.displayMedium,
                    color = TvMaterialTheme.colorScheme.primary,
                )

                TvText(
                    text = "Connect to your Jellyfin server",
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = TvMaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { TvText("Server URL") },
                    placeholder = { TvText("https://jellyfin.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { usernameFocusRequester.requestFocus() },
                    ),
                    colors = tvLoginTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .focusRequester(serverUrlFocusRequester),
                    enabled = !isConnecting,
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { TvText("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() },
                    ),
                    colors = tvLoginTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .focusRequester(usernameFocusRequester),
                    enabled = !isConnecting,
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { TvText("Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = if (passwordFocused) TvMaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { connectButtonFocusRequester.requestFocus() },
                    ),
                    colors = tvLoginTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .focusRequester(passwordFocusRequester)
                        .onFocusChanged { passwordFocused = it.isFocused },
                    enabled = !isConnecting,
                )

                if (errorMessage != null) {
                    TvCard(
                        onClick = { /* No-op */ },
                        colors = TvCardDefaults.colors(
                            containerColor = TvMaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TvText(
                            text = errorMessage,
                            color = TvMaterialTheme.colorScheme.error,
                            style = TvMaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    TvButton(
                        onClick = {
                            Log.d("TvServerConnectionScreen", "Connect button clicked")
                            if (serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                                onConnect(serverUrl, username, password)
                            } else {
                                Log.w("TvServerConnectionScreen", "Cannot connect - missing credentials")
                            }
                        },
                        enabled = serverUrl.isNotBlank() && username.isNotBlank() &&
                            password.isNotBlank() && !isConnecting,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .focusRequester(connectButtonFocusRequester),
                    ) {
                        if (isConnecting) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExpressiveCircularLoading(
                                    size = 20.dp,
                                    color = TvMaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TvText("Connecting...", style = TvMaterialTheme.typography.labelLarge)
                            }
                        } else {
                            TvText("Sign In", style = TvMaterialTheme.typography.labelLarge)
                        }
                    }

                    TvButton(
                        onClick = {
                            Log.d("TvServerConnectionScreen", "Quick Connect button clicked")
                            onQuickConnect()
                        },
                        enabled = !isConnecting,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .focusRequester(quickConnectButtonFocusRequester),
                    ) {
                        TvText("Quick Connect", style = TvMaterialTheme.typography.labelLarge)
                    }
                }

                TvText(
                    text = "Tip: Use Quick Connect to sign in without typing your password on TV",
                    style = TvMaterialTheme.typography.bodySmall,
                    color = TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun tvLoginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = TvMaterialTheme.colorScheme.primary,
    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
    focusedBorderColor = TvMaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
    focusedContainerColor = Color.White.copy(alpha = 0.15f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
    cursorColor = TvMaterialTheme.colorScheme.primary,
    focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
)
