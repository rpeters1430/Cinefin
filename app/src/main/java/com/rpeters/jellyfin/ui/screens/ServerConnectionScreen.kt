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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ConnectionPhase
import com.rpeters.jellyfin.ui.components.ConnectionState
import com.rpeters.jellyfin.ui.components.ExpressiveBackNavigationIcon
import com.rpeters.jellyfin.ui.components.ExpressiveFilledButton
import com.rpeters.jellyfin.ui.components.ExpressiveIconButton
import com.rpeters.jellyfin.ui.components.ExpressiveOutlinedButton
import com.rpeters.jellyfin.ui.components.ExpressiveTonalButton
import com.rpeters.jellyfin.ui.components.ExpressiveTopAppBar
import com.rpeters.jellyfin.ui.components.ExpressiveWavyCircularLoading
import com.rpeters.jellyfin.ui.components.PinningAlertReason
import com.rpeters.jellyfin.ui.components.PinningAlertState
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import com.rpeters.jellyfin.ui.theme.ShapeTokens
import java.text.DateFormat
import java.util.Date

@OptInAppExperimentalApis
@Composable
fun ServerConnectionScreen(
    onConnect: (String, String, String) -> Unit = { _, _, _ -> },
    onQuickConnect: () -> Unit = {},
    connectionState: ConnectionState = ConnectionState(),
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
    onRequireStrongBiometricChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf(savedServerUrl) }
    var username by remember { mutableStateOf(savedUsername) }
    val passwordState = rememberTextFieldState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val passwordText by remember {
        derivedStateOf { passwordState.text.toString() }
    }
    val canSubmit by remember {
        derivedStateOf { serverUrl.isNotBlank() && username.isNotBlank() && passwordText.isNotBlank() }
    }
    val submitIfValid: () -> Unit = {
        keyboardController?.hide()
        if (canSubmit) {
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
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            LoginHeaderCard(modifier = Modifier.fillMaxWidth())

            // Auto-login button if we have saved credentials
            val showAutoLoginCard = hasSavedPassword &&
                rememberLogin &&
                savedServerUrl.isNotBlank() &&
                savedUsername.isNotBlank()
            if (showAutoLoginCard) {
                AutoLoginCard(
                    savedServerUrl = savedServerUrl,
                    savedUsername = savedUsername,
                    isConnecting = connectionState.isConnecting,
                    isBiometricAuthEnabled = isBiometricAuthEnabled,
                    isBiometricAuthAvailable = isBiometricAuthAvailable,
                    onAutoLogin = onAutoLogin,
                    onBiometricLogin = onBiometricLogin,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Expressive Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = stringResource(id = R.string.login_or_divider),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }

            LoginFormCard(
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                username = username,
                onUsernameChange = { username = it },
                passwordState = passwordState,
                rememberLogin = rememberLogin,
                isConnecting = connectionState.isConnecting,
                focusRequester = focusRequester,
                onRememberLoginChange = onRememberLoginChange,
                onPasswordSubmit = submitIfValid,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isBiometricAuthEnabled && (isBiometricAuthAvailable || requireStrongBiometric || isUsingWeakBiometric)) {
                BiometricSecurityNotice(
                    requireStrongBiometric = requireStrongBiometric,
                    isUsingWeakBiometric = isUsingWeakBiometric,
                    onRequireStrongBiometricChange = onRequireStrongBiometricChange,
                )
            }

            // Show helper text when saved credentials are available but no password
            val showSavedCredentialsHint = savedServerUrl.isNotBlank() &&
                savedUsername.isNotBlank() &&
                rememberLogin &&
                !hasSavedPassword
            if (showSavedCredentialsHint) {
                SavedCredentialsHintCard(modifier = Modifier.fillMaxWidth())
            }

            // Error message
            if (connectionState.errorMessage != null) {
                ConnectionErrorCard(
                    message = connectionState.errorMessage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connect button - Expressive
            ExpressiveFilledButton(
                onClick = submitIfValid,
                enabled = canSubmit && !connectionState.isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = ShapeTokens.ExtraLarge,
            ) {
                if (connectionState.isConnecting) {
                    ExpressiveWavyCircularLoading(
                        modifier = Modifier.size(28.dp),
                        amplitude = 0.12f,
                        wavelength = 20.dp,
                        waveSpeed = 10.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(id = R.string.connecting),
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    Text(
                        stringResource(id = R.string.connect),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Quick Connect button - Expressive
            ExpressiveOutlinedButton(
                onClick = onQuickConnect,
                enabled = !connectionState.isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = ShapeTokens.ExtraLarge,
            ) {
                Text(
                    stringResource(id = R.string.quick_connect_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun LoginHeaderCard(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App logo - wrapped in an expressive surface
        androidx.compose.material3.Surface(
            shape = ShapeTokens.ExtraLarge,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.size(120.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_fg),
                    contentDescription = stringResource(id = R.string.app_name),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(90.dp),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(id = R.string.sign_in_to_server),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptInAppExperimentalApis
@Composable
private fun AutoLoginCard(
    savedServerUrl: String,
    savedUsername: String,
    isConnecting: Boolean,
    isBiometricAuthEnabled: Boolean,
    isBiometricAuthAvailable: Boolean,
    onAutoLogin: () -> Unit,
    onBiometricLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 6.dp,
        ),
        shape = ShapeTokens.ExtraLarge,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.welcome_back),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(
                    id = R.string.saved_credentials_info,
                    savedServerUrl,
                    savedUsername,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isBiometricAuthEnabled && isBiometricAuthAvailable) {
                    ExpressiveIconButton(
                        text = stringResource(id = R.string.login_with_biometric),
                        icon = Icons.Default.Security,
                        onClick = onBiometricLogin,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                }

                ExpressiveTonalButton(
                    onClick = onAutoLogin,
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = ShapeTokens.ExtraLarge,
                ) {
                    Text(stringResource(id = R.string.auto_login))
                }
            }
        }
    }
}

@OptInAppExperimentalApis
@Composable
private fun LoginFormCard(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    passwordState: androidx.compose.foundation.text.input.TextFieldState,
    rememberLogin: Boolean,
    isConnecting: Boolean,
    focusRequester: FocusRequester,
    onRememberLoginChange: (Boolean) -> Unit,
    onPasswordSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text(stringResource(id = R.string.server_url_label)) },
            placeholder = { Text(stringResource(id = R.string.server_url_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            enabled = !isConnecting,
            shape = ShapeTokens.ExtraLarge,
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(id = R.string.username_label)) },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
            shape = ShapeTokens.ExtraLarge,
        )

        OutlinedSecureTextField(
            state = passwordState,
            label = { Text(stringResource(id = R.string.password_label)) },
            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            onKeyboardAction = { performDefaultAction ->
                onPasswordSubmit()
                performDefaultAction()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
            shape = ShapeTokens.ExtraLarge,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = rememberLogin,
                onCheckedChange = onRememberLoginChange,
                enabled = !isConnecting,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(id = R.string.remember_login),
                style = MaterialTheme.typography.bodyMediumEmphasized,
            )
        }
    }
}

@Composable
private fun SavedCredentialsHintCard(
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp,
        ),
        shape = ShapeTokens.ExtraLarge,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(id = R.string.saved_credentials_hint),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ConnectionErrorCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 8.dp,
        ),
        shape = ShapeTokens.ExtraLarge,
        modifier = modifier,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
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

@Composable
private fun BiometricSecurityNotice(
    requireStrongBiometric: Boolean,
    isUsingWeakBiometric: Boolean,
    onRequireStrongBiometricChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isUsingWeakBiometric) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 0.dp,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(id = R.string.biometric_weak_security_title),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.biometric_weak_security_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.biometric_weak_security_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    if (requireStrongBiometric || isUsingWeakBiometric) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 0.dp,
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = stringResource(id = R.string.biometric_security_settings_title),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.biometric_security_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = R.string.biometric_security_settings_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = requireStrongBiometric,
                        onCheckedChange = { onRequireStrongBiometricChange(it) },
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.biometric_require_strong),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = stringResource(id = R.string.biometric_require_strong_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
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

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(id = R.string.quick_connect_title),
                navigationIcon = {
                    ExpressiveBackNavigationIcon(onClick = onCancel)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                text = stringResource(id = R.string.quick_connect_instruction),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Server URL input
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    onServerUrlChange(it)
                },
                label = { Text(stringResource(id = R.string.quick_connect_server_url_label)) },
                placeholder = { Text(stringResource(id = R.string.quick_connect_server_url_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
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
                shape = MaterialTheme.shapes.large,
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
                        defaultElevation = 0.dp,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = status,
                        color = if (isPolling) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(20.dp),
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
                        defaultElevation = 0.dp,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(id = R.string.quick_connect_code_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = code,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.quick_connect_enter_code_instruction),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
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
                        defaultElevation = 2.dp,
                    ),
                    shape = MaterialTheme.shapes.large,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExpressiveFilledButton(
                    onClick = {
                        keyboardController?.hide()
                        onConnect()
                    },
                    enabled = !connectionState.isConnecting && !isPolling && serverUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    if (connectionState.isConnecting) {
                        ExpressiveWavyCircularLoading(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(id = R.string.quick_connect_connecting))
                    } else if (isPolling) {
                        ExpressiveWavyCircularLoading(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(id = R.string.quick_connect_waiting))
                    } else {
                        Text(stringResource(id = R.string.quick_connect_get_code))
                    }
                }

                ExpressiveOutlinedButton(
                    onClick = onCancel,
                    enabled = !connectionState.isConnecting,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Help text
            Text(
                text = stringResource(id = R.string.quick_connect_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickConnectScreenPreview() {
    JellyfinAndroidTheme {
        QuickConnectScreen()
    }
}
