package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.data.security.CertificateDetails

/**
 * Enhanced connection progress indicator with detailed status information
 */
@Composable
fun ConnectionProgressIndicator(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (connectionState.connectionPhase) {
                ConnectionPhase.Idle -> {
                    ConnectionPhaseContent(
                        icon = Icons.Default.Wifi,
                        title = "Ready to Connect",
                        subtitle = "Enter server details to connect",
                        showProgress = false,
                        progress = null,
                    )
                }
                ConnectionPhase.Testing -> {
                    ConnectionPhaseContent(
                        icon = Icons.Default.Wifi,
                        title = "Testing Server Connection",
                        subtitle = connectionState.currentUrl.ifEmpty { "Discovering endpoints..." },
                        showProgress = true,
                        progress = null,
                    )
                }
                ConnectionPhase.Authenticating -> {
                    ConnectionPhaseContent(
                        icon = Icons.Default.Wifi,
                        title = "Authenticating",
                        subtitle = "Verifying credentials...",
                        showProgress = true,
                        progress = null,
                    )
                }
                ConnectionPhase.Connected -> {
                    ConnectionPhaseContent(
                        icon = Icons.Default.CheckCircle,
                        title = "Connected Successfully",
                        subtitle = "Ready to browse your media",
                        showProgress = false,
                        progress = null,
                        iconTint = MaterialTheme.colorScheme.primary,
                    )
                }
                ConnectionPhase.Error -> {
                    ConnectionPhaseContent(
                        icon = Icons.Default.Error,
                        title = "Connection Failed",
                        subtitle = connectionState.errorMessage ?: "Unable to connect to server",
                        showProgress = false,
                        progress = null,
                        iconTint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionPhaseContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    showProgress: Boolean,
    progress: Float?,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Status icon
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(48.dp),
            tint = iconTint,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (showProgress) {
            Spacer(modifier = Modifier.height(16.dp))

            if (progress != null) {
                // Linear progress for operations with known progress
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Circular progress for operations without known progress
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * Connection status indicator for the app bar or other UI areas
 */
@Composable
fun ConnectionStatusIndicator(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status icon
        Icon(
            imageVector = when (connectionState.connectionPhase) {
                ConnectionPhase.Connected -> Icons.Default.Wifi
                ConnectionPhase.Testing, ConnectionPhase.Authenticating -> Icons.Default.WifiOff
                ConnectionPhase.Error -> Icons.Default.Error
                else -> Icons.Default.WifiOff
            },
            contentDescription = "Connection status",
            modifier = Modifier.size(16.dp),
            tint = when (connectionState.connectionPhase) {
                ConnectionPhase.Connected -> MaterialTheme.colorScheme.primary
                ConnectionPhase.Testing, ConnectionPhase.Authenticating -> MaterialTheme.colorScheme.tertiary
                ConnectionPhase.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // Status text
        Text(
            text = when (connectionState.connectionPhase) {
                ConnectionPhase.Connected -> "Connected"
                ConnectionPhase.Testing -> "Testing..."
                ConnectionPhase.Authenticating -> "Authenticating..."
                ConnectionPhase.Error -> "Error"
                else -> "Disconnected"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Enhanced connection state with detailed phase information
 */
data class ConnectionState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
    val serverName: String? = null,
    val savedServerUrl: String = "",
    val savedUsername: String = "",
    val rememberLogin: Boolean = true,
    val isQuickConnectActive: Boolean = false,
    val quickConnectServerUrl: String = "",
    val quickConnectCode: String = "",
    val quickConnectSecret: String = "",
    val isQuickConnectPolling: Boolean = false,
    val quickConnectStatus: String = "",
    val hasSavedPassword: Boolean = false,
    val isBiometricAuthAvailable: Boolean = false,
    // Enhanced connection state fields
    val connectionPhase: ConnectionPhase = ConnectionPhase.Idle,
    val currentUrl: String = "",
    val progress: Float = 0f,
    val loadedCount: Int = 0,
    val totalCount: Int = 0,
    val pinningAlert: PinningAlertState? = null,
)

/**
 * Connection phases for detailed status tracking
 */
enum class ConnectionPhase {
    Idle,
    Testing,
    Authenticating,
    Connected,
    Error,
}

enum class PinningAlertReason {
    MISMATCH,
    EXPIRED,
}

data class PinningAlertState(
    val hostname: String,
    val reason: PinningAlertReason,
    val certificateDetails: List<CertificateDetails> = emptyList(),
    val firstSeenEpochMillis: Long? = null,
    val expiresAtEpochMillis: Long? = null,
    val attemptedPins: List<String> = emptyList(),
)
