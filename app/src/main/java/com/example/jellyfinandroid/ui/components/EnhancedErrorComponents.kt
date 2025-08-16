package com.example.jellyfinandroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.data.repository.common.ErrorType
import com.example.jellyfinandroid.ui.utils.ProcessedError

/**
 * Enhanced error components that provide rich user experience for error states.
 * Part of Phase 2: Enhanced Error Handling & User Experience improvements.
 */

/**
 * Modern error banner that slides in from the top and provides contextual actions.
 */
@Composable
fun ErrorBanner(
    error: ProcessedError?,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = error != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        error?.let { processedError ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (processedError.errorType) {
                        ErrorType.NETWORK -> MaterialTheme.colorScheme.errorContainer
                        ErrorType.AUTHENTICATION -> MaterialTheme.colorScheme.warningContainer
                        ErrorType.SERVER_ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = getErrorIcon(processedError.errorType),
                        contentDescription = null,
                        tint = when (processedError.errorType) {
                            ErrorType.NETWORK -> MaterialTheme.colorScheme.onErrorContainer
                            ErrorType.AUTHENTICATION -> MaterialTheme.colorScheme.onWarningContainer
                            ErrorType.SERVER_ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = processedError.userMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = when (processedError.errorType) {
                                ErrorType.NETWORK -> MaterialTheme.colorScheme.onErrorContainer
                                ErrorType.AUTHENTICATION -> MaterialTheme.colorScheme.onWarningContainer
                                ErrorType.SERVER_ERROR -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )

                        Text(
                            text = processedError.suggestedAction,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (processedError.errorType) {
                                ErrorType.NETWORK -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                ErrorType.AUTHENTICATION -> MaterialTheme.colorScheme.onWarningContainer.copy(alpha = 0.8f)
                                ErrorType.SERVER_ERROR -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (processedError.isRetryable && onRetry != null) {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (onDismiss != null) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.height(36.dp),
                            ) {
                                Text(
                                    text = "Dismiss",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen error state with illustration and clear actions.
 */
@Composable
fun FullScreenError(
    error: ProcessedError,
    onRetry: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            // Error illustration
            Icon(
                imageVector = getErrorIcon(error.errorType),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )

            // Error title
            Text(
                text = getErrorTitle(error.errorType),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Error message
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Suggested action
            Text(
                text = error.suggestedAction,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error.isRetryable && onRetry != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }

                if (onNavigateBack != null) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

/**
 * Compact error state for use in lists or smaller spaces.
 */
@Composable
fun CompactError(
    error: ProcessedError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = getErrorIcon(error.errorType),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = error.userMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (error.isRetryable) {
                    Text(
                        text = "Tap to retry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            if (error.isRetryable && onRetry != null) {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(32.dp),
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

/**
 * Offline state indicator with smart messaging.
 */
@Composable
fun OfflineIndicator(
    isVisible: Boolean,
    hasOfflineContent: Boolean = false,
    onViewOfflineContent: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "You're offline",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        text = if (hasOfflineContent) {
                            "Downloaded content is available"
                        } else {
                            "Connect to internet to browse content"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                if (hasOfflineContent && onViewOfflineContent != null) {
                    TextButton(
                        onClick = onViewOfflineContent,
                    ) {
                        Text("View Downloads")
                    }
                }
            }
        }
    }
}

// Helper functions for error icons and titles

private fun getErrorIcon(errorType: ErrorType): ImageVector {
    return when (errorType) {
        ErrorType.NETWORK -> Icons.Default.WifiOff
        ErrorType.SERVER_ERROR -> Icons.Default.CloudOff
        ErrorType.AUTHENTICATION, ErrorType.UNAUTHORIZED, ErrorType.FORBIDDEN -> Icons.Default.Error
        ErrorType.NOT_FOUND -> Icons.Default.Error
        ErrorType.TIMEOUT -> Icons.Default.CloudOff
        ErrorType.OPERATION_CANCELLED -> Icons.Default.Error
        ErrorType.UNKNOWN -> Icons.Default.Error
    }
}

private fun getErrorTitle(errorType: ErrorType): String {
    return when (errorType) {
        ErrorType.NETWORK -> "Connection Problem"
        ErrorType.SERVER_ERROR -> "Server Error"
        ErrorType.AUTHENTICATION -> "Authentication Error"
        ErrorType.UNAUTHORIZED -> "Access Denied"
        ErrorType.FORBIDDEN -> "Permission Denied"
        ErrorType.NOT_FOUND -> "Content Not Found"
        ErrorType.TIMEOUT -> "Request Timed Out"
        ErrorType.OPERATION_CANCELLED -> "Operation Cancelled"
        ErrorType.UNKNOWN -> "Something Went Wrong"
    }
}

// Extension property for warning container color (if not available in your theme)
private val ColorScheme.warningContainer: androidx.compose.ui.graphics.Color
    @Composable get() = surfaceVariant

private val ColorScheme.onWarningContainer: androidx.compose.ui.graphics.Color
    @Composable get() = onSurfaceVariant
