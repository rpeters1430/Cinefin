package com.example.jellyfinandroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jellyfinandroid.data.repository.ErrorType
import com.example.jellyfinandroid.ui.utils.ProcessedError

/**
 * Comprehensive error display components for consistent error handling throughout the app.
 *
 * Provides various error display patterns with retry capabilities, user guidance,
 * and appropriate visual styling based on error severity.
 */

/**
 * Main error display component that shows user-friendly error messages with retry options.
 *
 * @param error The processed error to display
 * @param onRetry Callback when user taps retry button (null to hide retry button)
 * @param onDismiss Callback when user dismisses the error (null to hide dismiss button)
 * @param modifier Layout modifier
 */
@Composable
fun ErrorDisplay(
    error: ProcessedError,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = getErrorIcon(error.errorType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )

                Text(
                    text = getErrorTitle(error.errorType),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            if (error.suggestedAction.isNotBlank()) {
                Text(
                    text = "Suggestion: ${error.suggestedAction}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                onDismiss?.let { dismiss ->
                    TextButton(onClick = dismiss) {
                        Text("Dismiss")
                    }
                }

                if (error.isRetryable && onRetry != null) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/**
 * Compact error banner for inline error display.
 *
 * @param errorMessage The error message to display
 * @param onRetry Optional retry callback
 * @param modifier Layout modifier
 */
@Composable
fun ErrorBanner(
    errorMessage: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }

            onRetry?.let { retry ->
                IconButton(
                    onClick = retry,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Full-screen error state for major failures.
 *
 * @param error The processed error to display
 * @param onRetry Retry callback
 * @param modifier Layout modifier
 */
@Composable
fun ErrorScreen(
    error: ProcessedError,
    onRetry: (() -> Unit)? = null,
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
            Icon(
                imageVector = getErrorIcon(error.errorType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )

            Text(
                text = getErrorTitle(error.errorType),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (error.suggestedAction.isNotBlank()) {
                Text(
                    text = error.suggestedAction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (error.isRetryable && onRetry != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier.widthIn(min = 120.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

/**
 * Snackbar error message with optional retry action.
 *
 * @param snackbarHostState The snackbar host state
 * @param errorMessage The error message to display
 * @param onRetry Optional retry callback
 */
@Composable
fun ErrorSnackbar(
    snackbarHostState: SnackbarHostState,
    errorMessage: String,
    onRetry: (() -> Unit)? = null,
) {
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank()) {
            val result = snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = if (onRetry != null) "Retry" else null,
                duration = SnackbarDuration.Long,
            )

            if (result == SnackbarResult.ActionPerformed && onRetry != null) {
                onRetry()
            }
        }
    }
}

// Helper functions

private fun getErrorIcon(errorType: ErrorType) = when (errorType) {
    ErrorType.NETWORK -> Icons.Default.Warning
    ErrorType.AUTHENTICATION -> Icons.Default.Error
    ErrorType.UNAUTHORIZED -> Icons.Default.Error
    ErrorType.FORBIDDEN -> Icons.Default.Error
    ErrorType.NOT_FOUND -> Icons.Default.Warning
    ErrorType.SERVER_ERROR -> Icons.Default.Error
    ErrorType.OPERATION_CANCELLED -> Icons.Default.Warning
    ErrorType.UNKNOWN -> Icons.Default.Error
}

private fun getErrorTitle(errorType: ErrorType) = when (errorType) {
    ErrorType.NETWORK -> "Connection Problem"
    ErrorType.AUTHENTICATION -> "Authentication Failed"
    ErrorType.UNAUTHORIZED -> "Unauthorized Access"
    ErrorType.FORBIDDEN -> "Access Denied"
    ErrorType.NOT_FOUND -> "Not Found"
    ErrorType.SERVER_ERROR -> "Server Error"
    ErrorType.OPERATION_CANCELLED -> "Operation Cancelled"
    ErrorType.UNKNOWN -> "Unexpected Error"
}

/**
 * Extension function to easily show errors with retry functionality.
 */
@Composable
fun String.ErrorWithRetry(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (this.isNotBlank()) {
        ErrorBanner(
            errorMessage = this,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}
