package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ConnectionPhase
import com.rpeters.jellyfin.ui.components.ConnectionState
import com.rpeters.jellyfin.ui.components.PinningAlertReason
import com.rpeters.jellyfin.ui.components.PinningAlertState
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import java.text.DateFormat
import java.util.Date

@OptInAppExperimentalApis
@Composable
fun ServerConnectionScreen(
    onConnect: (String, String, String) -> Unit = { _, _, _ -> },
    onQuickConnect: () -> Unit = {},
    connectionState: ConnectionState = ConnectionState(), // Use enhanced connection state
    savedServerUrl: String = "",
    savedUsername: String = "",
    rememberLogin: Boolean = true,
    hasSavedPassword: Boolean = false,
    isBiometricAuthEnabled: Boolean = false,
    isBiometricAuthAvailable: Boolean = false,
    requireStrongBiometric: Boolean = false,
    isUsingWeakBiometric: Boolean = false,
    onRememberLoginChange: (Boolean) -> Unit = {},
    onAutoLogin: () -> Unit = {},
    onBiometricLogin: () -> Unit = {},
    onTemporarilyTrustPin: () -> Unit = {},
    onDismissPinningAlert: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf(savedServerUrl) }
    var username by remember { mutableStateOf(savedUsername) }
    val passwordState = rememberTextFieldState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val submitIfValid: () -> Unit = {
        keyboardController?.hide()
        val passwordText = passwordState.text.toString()
        if (serverUrl.isNotBlank() && username.isNotBlank() && passwordText.isNotBlank()) {
            onConnect(serverUrl, username, passwordText)
        }
    }

    connectionState.pinningAlert?.let { pinningAlert ->
        PinningAlertDialog(
            alertState = pinningAlert,
            onDismiss = onDismissPinningAlert,
            onTemporarilyTrust = onTemporarilyTrustPin,
        )
    }

    // Update local state when saved values change
    LaunchedEffect(savedServerUrl, savedUsername) {
        serverUrl = savedServerUrl
        username = savedUsername
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = "Jellyfin",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Sign in to your server",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Auto-login button if we have saved credentials
            if (hasSavedPassword && rememberLogin && savedServerUrl.isNotBlank() && savedUsername.isNotBlank()) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 6.dp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Welcome back",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Server: $savedServerUrl\nUser: $savedUsername",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                        )
                        FilledTonalButton(
                            onClick = onAutoLogin,
                            enabled = !connectionState.isConnecting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Auto Login")
                        }

                        // Biometric login button if available
                        if (isBiometricAuthAvailable) {
                            OutlinedButton(
                                onClick = onBiometricLogin,
                                enabled = !connectionState.isConnecting,
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

            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Server URL input
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(id = R.string.server_url_label)) },
                        placeholder = { Text(stringResource(id = R.string.server_url_placeholder)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        enabled = !connectionState.isConnecting,
                    )

                    // Username input
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(id = R.string.username_label)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connectionState.isConnecting,
                    )

                    // Password input
                    OutlinedSecureTextField(
                        state = passwordState,
                        label = { Text(stringResource(id = R.string.password_label)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        onKeyboardAction = { performDefaultAction ->
                            submitIfValid()
                            performDefaultAction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !connectionState.isConnecting,
                    )

                    // Remember login toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = rememberLogin,
                            onCheckedChange = { onRememberLoginChange(it) },
                            enabled = !connectionState.isConnecting,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remember login")
                    }
                }
            }

            if (isBiometricAuthEnabled && (isBiometricAuthAvailable || requireStrongBiometric || isUsingWeakBiometric)) {
                BiometricSecurityNotice(
                    requireStrongBiometric = requireStrongBiometric,
                    isUsingWeakBiometric = isUsingWeakBiometric,
                    onRequireStrongBiometricChange = onRequireStrongBiometricChange,
                )
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
            if (connectionState.errorMessage != null) {
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
                        text = connectionState.errorMessage,
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
                    submitIfValid()
                },
                enabled =
                serverUrl.isNotBlank() &&
                    username.isNotBlank() &&
                    passwordState.text.isNotBlank() &&
                    !connectionState.isConnecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (connectionState.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.connecting))
                } else {
                    Text(stringResource(id = R.string.connect))
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
}

@Composable
private fun PinningAlertDialog(
    alertState: PinningAlertState,
    onDismiss: () -> Unit,
    onTemporarilyTrust: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val title = stringResource(
        id = if (alertState.reason == PinningAlertReason.EXPIRED) {
            R.string.pinning_alert_title_expired
        } else {
            R.string.pinning_alert_title_mismatch
        },
    )
    val subtitle = stringResource(
        id = if (alertState.reason == PinningAlertReason.EXPIRED) {
            R.string.pinning_alert_message_expired
        } else {
            R.string.pinning_alert_message_mismatch
        },
        alertState.hostname,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onTemporarilyTrust) {
                Text(text = stringResource(id = R.string.pinning_trust_temporarily))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.pinning_abort))
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
                alertState.firstSeenEpochMillis?.let { firstSeen ->
                    Text(
                        text = stringResource(
                            id = R.string.pinning_first_seen,
                            dateFormatter.format(Date(firstSeen)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                alertState.expiresAtEpochMillis?.let { expires ->
                    Text(
                        text = stringResource(
                            id = R.string.pinning_expires_at,
                            dateFormatter.format(Date(expires)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (alertState.certificateDetails.isNotEmpty()) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(
                            text = stringResource(
                                id = if (showDetails) {
                                    R.string.pinning_hide_certificate_details
                                } else {
                                    R.string.pinning_view_certificate_details
                                },
                            ),
                        )
                    }

                    if (showDetails) {
                        alertState.certificateDetails.forEachIndexed { index, cert ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(id = R.string.pinning_certificate_number, index + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(id = R.string.pinning_cert_subject, cert.subject),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = stringResource(id = R.string.pinning_cert_issuer, cert.issuer),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = stringResource(
                                        id = R.string.pinning_cert_validity,
                                        dateFormatter.format(Date(cert.validFromEpochMillis)),
                                        dateFormatter.format(Date(cert.validToEpochMillis)),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = stringResource(id = R.string.pinning_cert_pin, cert.pin),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (index < alertState.certificateDetails.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
    )
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

@OptInAppExperimentalApis
@Composable
fun QuickConnectScreen(
    connectionState: ConnectionState = ConnectionState(),
    onConnect: () -> Unit = {},
    onCancel: () -> Unit = {},
    onServerUrlChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var serverUrl by remember { mutableStateOf(connectionState.quickConnectServerUrl) }
    val code = connectionState.quickConnectCode
    val isPolling = connectionState.isQuickConnectPolling
    val status = connectionState.quickConnectStatus

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
            onValueChange = {
                serverUrl = it
                onServerUrlChange(it)
            },
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
            enabled = !connectionState.isConnecting && !isPolling,
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
        if (connectionState.errorMessage != null) {
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
                    text = connectionState.errorMessage,
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
            enabled = !connectionState.isConnecting && !isPolling && serverUrl.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (connectionState.isConnecting) {
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
            enabled = !connectionState.isConnecting,
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
