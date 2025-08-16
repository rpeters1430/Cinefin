package com.example.jellyfinandroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.data.repository.common.ErrorType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ✅ PHASE 3: Advanced Error Handling UI Components
 * Provides user-friendly error states with recovery actions
 */

data class ErrorUIState(
    val title: String,
    val message: String,
    val icon: ImageVector,
    val primaryAction: ErrorAction? = null,
    val secondaryAction: ErrorAction? = null,
    val canRetry: Boolean = true,
)

data class ErrorAction(
    val label: String,
    val action: suspend () -> Unit,
)

@Composable
fun ErrorStateScreen(
    errorType: ErrorType,
    errorMessage: String,
    onRetry: (suspend () -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val errorState = remember(errorType, errorMessage) {
        when (errorType) {
            ErrorType.NETWORK -> ErrorUIState(
                title = "Network Connection Error",
                message = "Unable to connect to the server. Please check your internet connection and try again.",
                icon = Icons.Default.SignalWifiOff,
                primaryAction = onRetry?.let { ErrorAction("Retry", it) },
                secondaryAction = onNavigateToSettings?.let { ErrorAction("Settings") { it() } },
            )

            ErrorType.AUTHENTICATION -> ErrorUIState(
                title = "Authentication Failed",
                message = "Unable to authenticate with the server. Please check your credentials.",
                icon = Icons.Default.Lock,
                primaryAction = onDismiss?.let { ErrorAction("Try Again") { it() } },
                canRetry = false,
            )

            ErrorType.SERVER_ERROR -> ErrorUIState(
                title = "Server Error",
                message = "The server encountered an error. Please try again later.",
                icon = Icons.Default.CloudOff,
                primaryAction = onRetry?.let { ErrorAction("Retry", it) },
            )

            ErrorType.TIMEOUT -> ErrorUIState(
                title = "Request Timeout",
                message = "The request took too long to complete. Please check your connection and try again.",
                icon = Icons.Default.Warning,
                primaryAction = onRetry?.let { ErrorAction("Retry", it) },
            )

            else -> ErrorUIState(
                title = "Something Went Wrong",
                message = errorMessage.ifBlank { "An unexpected error occurred. Please try again." },
                icon = Icons.Default.Error,
                primaryAction = onRetry?.let { ErrorAction("Retry", it) },
            )
        }
    }

    ErrorContent(
        errorState = errorState,
        modifier = modifier,
    )
}

@Composable
private fun ErrorContent(
    errorState: ErrorUIState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isRetrying by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Error Icon
                Icon(
                    imageVector = errorState.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Error Title
                Text(
                    text = errorState.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Error Message
                Text(
                    text = errorState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Primary Action Button
                    errorState.primaryAction?.let { action ->
                        Button(
                            onClick = {
                                scope.launch {
                                    isRetrying = true
                                    try {
                                        action.action()
                                    } finally {
                                        delay(500) // Brief delay for UX
                                        isRetrying = false
                                    }
                                }
                            },
                            enabled = !isRetrying,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            AnimatedVisibility(
                                visible = isRetrying,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }

                            Text(
                                text = if (isRetrying) "Retrying..." else action.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Secondary Action Button
                    errorState.secondaryAction?.let { action ->
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    action.action()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ✅ PHASE 3: Inline Error Banner for non-critical errors
 */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    onRetry?.let {
                        OutlinedButton(
                            onClick = it,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ✅ PHASE 3: Empty State Component
 */
@Composable
fun EmptyStateScreen(
    title: String,
    message: String,
    icon: ImageVector = Icons.Default.Warning,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}
