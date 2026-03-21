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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Button
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveCircularLoading
import com.rpeters.jellyfin.ui.components.tv.TvImmersiveBackground
import com.rpeters.jellyfin.ui.theme.CinefinTvTheme
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
    val tvLayout = CinefinTvTheme.layout
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
    Box(modifier = modifier.fillMaxSize()) {
        TvImmersiveBackground(backdropUrl = null)

        Column(
            modifier = Modifier
                .fillMaxWidth(tvLayout.formMaxWidthFraction)
                .wrapContentHeight()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tvLayout.sectionSpacing),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvText(
                    text = "Quick Connect",
                    style = TvMaterialTheme.typography.displayLarge,
                    color = TvMaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )

                TvText(
                    text = "Sign in without typing your password",
                    style = TvMaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                TvText(
                    text = "Enter your server once, request a temporary code, then approve it from another device already signed in to Jellyfin.",
                    style = TvMaterialTheme.typography.bodyLarge,
                    color = TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }

            TvCard(
                onClick = {},
                colors = TvCardDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f)),
                scale = TvCardDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    OutlinedTextField(
                        value = localServerUrl,
                        onValueChange = {
                            localServerUrl = it
                            onServerUrlChange(it)
                        },
                        label = { TvText(stringResource(id = R.string.server_url_label), style = TvMaterialTheme.typography.bodyLarge) },
                        placeholder = {
                            TvText(
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickConnectStepChip("1. Enter Server")
                        QuickConnectStepChip("2. Get Code")
                        QuickConnectStepChip("3. Approve Elsewhere")
                    }
                }
            }

            // Quick Connect Code display - LARGE for TV viewing from 10 feet
            if (quickConnectCode.isNotBlank()) {
                TvCard(
                    onClick = { /* No-op */ },
                    colors = TvCardDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TvQuickConnectTestTags.CODE_CARD),
                    scale = TvCardDefaults.scale(focusedScale = 1f),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TvText(
                            text = "Enter this code on your server:",
                            style = TvMaterialTheme.typography.titleLarge,
                            color = TvMaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // LARGE code display - 96sp for readability from 10 feet
                        TvText(
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
                        TvText(
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
                TvCard(
                    onClick = { /* No-op */ },
                    colors = TvCardDefaults.colors(
                        containerColor = if (isPolling) {
                            TvMaterialTheme.colorScheme.secondaryContainer
                        } else {
                            TvMaterialTheme.colorScheme.tertiaryContainer
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TvQuickConnectTestTags.STATUS_CARD),
                    scale = TvCardDefaults.scale(focusedScale = 1f),
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

                            TvIcon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Waiting",
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer { rotationZ = rotation },
                                tint = TvMaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        TvText(
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
                TvCard(
                    onClick = { /* No-op */ },
                    colors = TvCardDefaults.colors(
                        containerColor = TvMaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    scale = TvCardDefaults.scale(focusedScale = 1f),
                ) {
                    TvText(
                        text = errorMessage,
                        color = TvMaterialTheme.colorScheme.error,
                        style = TvMaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(20.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

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
                            ExpressiveCircularLoading(
                                modifier = Modifier.size(24.dp),
                                color = TvMaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TvText(
                                "Connecting...",
                                style = TvMaterialTheme.typography.titleMedium,
                            )
                        }
                    } else if (isPolling) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExpressiveCircularLoading(
                                modifier = Modifier.size(24.dp),
                                color = TvMaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TvText(
                                "Waiting for approval...",
                                style = TvMaterialTheme.typography.titleMedium,
                            )
                        }
                    } else {
                        TvText(
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
                    TvText(
                        if (isPolling) "Cancel" else "Back",
                        style = TvMaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Help text
            Spacer(modifier = Modifier.height(8.dp))
            TvText(
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

@Composable
private fun QuickConnectStepChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = {},
        modifier = modifier,
        colors = TvCardDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f)),
        scale = TvCardDefaults.scale(focusedScale = 1f),
    ) {
        TvText(
            text = text,
            style = TvMaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
