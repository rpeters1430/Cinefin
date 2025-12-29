package com.rpeters.jellyfin.ui.screens.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

object TvQuickConnectTestTags {
    const val SERVER_INPUT = "tv_qc_server_input"
    const val GET_CODE_BUTTON = "tv_qc_get_code"
    const val CANCEL_BUTTON = "tv_qc_cancel"
    const val CODE_CARD = "tv_qc_code_card"
    const val CODE_TEXT = "tv_qc_code_text"
    const val STATUS_CARD = "tv_qc_status"
}

/**
 * TV-optimized Quick Connect screen with large readable code display and D-pad navigation.
 * Designed for comfortable viewing from 10 feet away with clear visual hierarchy.
 */
@Composable
fun TvQuickConnectScreen(
    serverUrl: String,
    quickConnectCode: String,
    isConnecting: Boolean,
    isPolling: Boolean,
    status: String,
    errorMessage: String?,
    onServerUrlChange: (String) -> Unit,
    onInitiateQuickConnect: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var localServerUrl by remember { mutableStateOf(serverUrl) }

    val serverUrlFocusRequester = remember { FocusRequester() }
    val getCodeButtonFocusRequester = remember { FocusRequester() }
    val cancelButtonFocusRequester = remember { FocusRequester() }

    // Update local state when prop changes
    LaunchedEffect(serverUrl) {
        localServerUrl = serverUrl
    }

    // Auto-focus the server URL field on start if empty, otherwise focus get code button
    LaunchedEffect(Unit) {
        if (localServerUrl.isEmpty()) {
            serverUrlFocusRequester.requestFocus()
        } else if (!isPolling && quickConnectCode.isEmpty()) {
            getCodeButtonFocusRequester.requestFocus()
        }
    }

    // TV-optimized centered layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Text(
                text = "Quick Connect",
                style = TvMaterialTheme.typography.displayLarge,
                color = TvMaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Sign in without typing your password",
                style = TvMaterialTheme.typography.titleMedium,
                color = TvMaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Server URL input
            OutlinedTextField(
                value = localServerUrl,
                onValueChange = {
                    localServerUrl = it
                    onServerUrlChange(it)
                },
                label = { Text("Server URL", style = TvMaterialTheme.typography.bodyLarge) },
                placeholder = {
                    Text(
                        "https://jellyfin.example.com",
                        style = TvMaterialTheme.typography.bodyLarge,
                    )
                },
                textStyle = TvMaterialTheme.typography.bodyLarge,
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (localServerUrl.isNotBlank() && !isPolling) {
                            getCodeButtonFocusRequester.requestFocus()
                        }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .focusRequester(serverUrlFocusRequester)
                    .testTag(TvQuickConnectTestTags.SERVER_INPUT),
                enabled = !isConnecting && !isPolling,
            )

            // Quick Connect Code display - LARGE for TV viewing from 10 feet
            if (quickConnectCode.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                androidx.tv.material3.Card(
                    onClick = { /* No-op */ },
                    colors = androidx.tv.material3.CardDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TvQuickConnectTestTags.CODE_CARD),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Enter this code on your server:",
                            style = TvMaterialTheme.typography.titleLarge,
                            color = TvMaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // LARGE code display - 96sp for readability from 10 feet
                        Text(
                            text = quickConnectCode,
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvMaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp,
                            modifier = Modifier.testTag(TvQuickConnectTestTags.CODE_TEXT),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Instructions with server URL
                        Text(
                            text = "1. Go to: $localServerUrl/web\n2. Navigate to Dashboard → Users → Quick Connect\n3. Enter the code above",
                            style = TvMaterialTheme.typography.bodyLarge,
                            color = TvMaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp,
                        )
                    }
                }
            }

            // Status message with icon
            if (status.isNotBlank()) {
                androidx.tv.material3.Card(
                    onClick = { /* No-op */ },
                    colors = androidx.tv.material3.CardDefaults.colors(
                        containerColor = if (isPolling) {
                            TvMaterialTheme.colorScheme.secondaryContainer
                        } else {
                            TvMaterialTheme.colorScheme.tertiaryContainer
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TvQuickConnectTestTags.STATUS_CARD),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isPolling) {
                            // Animated spinning indicator for polling state
                            val infiniteTransition = rememberInfiniteTransition(label = "polling")
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart,
                                ),
                                label = "rotation",
                            )

                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Waiting",
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer { rotationZ = rotation },
                                tint = TvMaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Text(
                            text = status,
                            color = if (isPolling) {
                                TvMaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                TvMaterialTheme.colorScheme.onTertiaryContainer
                            },
                            style = TvMaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

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
                        style = TvMaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(20.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                // Get Code / Retry button
                Button(
                    onClick = onInitiateQuickConnect,
                    enabled = !isConnecting && !isPolling && localServerUrl.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .focusRequester(getCodeButtonFocusRequester)
                        .testTag(TvQuickConnectTestTags.GET_CODE_BUTTON),
                ) {
                    if (isConnecting) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = TvMaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Connecting...",
                                style = TvMaterialTheme.typography.titleMedium,
                            )
                        }
                    } else if (isPolling) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = TvMaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Waiting for approval...",
                                style = TvMaterialTheme.typography.titleMedium,
                            )
                        }
                    } else {
                        Text(
                            if (quickConnectCode.isNotBlank()) "Get New Code" else "Get Quick Connect Code",
                            style = TvMaterialTheme.typography.titleMedium,
                        )
                    }
                }

                // Cancel / Back button
                Button(
                    onClick = onCancel,
                    enabled = !isConnecting,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .focusRequester(cancelButtonFocusRequester)
                        .testTag(TvQuickConnectTestTags.CANCEL_BUTTON),
                ) {
                    Text(
                        if (isPolling) "Cancel" else "Back",
                        style = TvMaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Help text
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quick Connect allows you to sign in without typing your password on the TV. " +
                    "Use your phone, tablet, or computer to authorize this connection.",
                style = TvMaterialTheme.typography.bodyMedium,
                color = TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
