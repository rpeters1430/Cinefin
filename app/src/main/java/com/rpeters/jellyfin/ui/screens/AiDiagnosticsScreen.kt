package com.rpeters.jellyfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.viewmodel.AiDiagnosticsViewModel

@OptInAppExperimentalApis
@Composable
fun AiDiagnosticsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiDiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.aiBackendState.collectAsStateWithLifecycle()

    AiDiagnosticsScreenContent(
        isUsingNano = state.isUsingNano,
        nanoStatus = state.nanoStatus,
        isDownloading = state.isDownloading,
        downloadBytesProgress = state.downloadBytesProgress,
        canRetryDownload = state.canRetryDownload,
        errorCode = state.errorCode,
        onBackClick = onBackClick,
        onRetryClick = viewModel::retryNanoDownload,
        modifier = modifier,
    )
}

@OptInAppExperimentalApis
@Composable
private fun AiDiagnosticsScreenContent(
    isUsingNano: Boolean,
    nanoStatus: String,
    isDownloading: Boolean,
    downloadBytesProgress: String?,
    canRetryDownload: Boolean,
    errorCode: Int?,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Backend Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Current AI Backend Status",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                StatusCard(
                    isUsingNano = isUsingNano,
                    isDownloading = isDownloading,
                )
            }

            item {
                BackendDetailsCard(
                    nanoStatus = nanoStatus,
                    isDownloading = isDownloading,
                    downloadBytesProgress = downloadBytesProgress,
                    errorCode = errorCode,
                )
            }

            if (errorCode != null) {
                item {
                    ErrorExplanationCard(errorCode = errorCode)
                }
            }

            if (canRetryDownload) {
                item {
                    RetryCard(
                        onRetryClick = onRetryClick,
                        errorCode = errorCode,
                    )
                }
            }

            item {
                InfoCard()
            }
        }
    }
}

@Composable
private fun StatusCard(
    isUsingNano: Boolean,
    isDownloading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDownloading -> MaterialTheme.colorScheme.secondaryContainer
                isUsingNano -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = when {
                    isDownloading -> Icons.Default.Download
                    isUsingNano -> Icons.Default.Smartphone
                    else -> Icons.Default.Cloud
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = when {
                    isDownloading -> MaterialTheme.colorScheme.onSecondaryContainer
                    isUsingNano -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                },
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isDownloading -> "Downloading Model"
                        isUsingNano -> "On-Device AI (Gemini Nano)"
                        else -> "Cloud AI (Gemini 2.5 Flash)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        isDownloading -> "Installing on-device model..."
                        isUsingNano -> "Privacy-first • No internet required"
                        else -> "Reliable fallback • Internet required"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BackendDetailsCard(
    nanoStatus: String,
    isDownloading: Boolean,
    downloadBytesProgress: String?,
    errorCode: Int?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Technical Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            DetailRow(
                icon = Icons.Default.Info,
                label = "Status",
                value = nanoStatus,
            )

            if (isDownloading && downloadBytesProgress != null) {
                DetailRow(
                    icon = Icons.Default.Download,
                    label = "Progress",
                    value = downloadBytesProgress,
                )
            }

            if (errorCode != null) {
                DetailRow(
                    icon = Icons.Default.Error,
                    label = "Error Code",
                    value = errorCode.toString(),
                    isError = true,
                )
            }
        }
    }
}

@Composable
private fun ErrorExplanationCard(errorCode: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Error Explanation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            Text(
                text = getErrorExplanation(errorCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Text(
                text = getErrorSolution(errorCode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun RetryCard(
    onRetryClick: () -> Unit,
    errorCode: Int?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Retry Download",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = when (errorCode) {
                    606 -> "Try restarting the app or your device, then tap retry."
                    601 -> "Wait a few minutes for the AI quota to reset, then try again."
                    605 -> "Check your internet connection and available storage, then retry."
                    else -> "You can attempt to download the on-device model again."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Download")
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "About AI Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            InfoRow(
                icon = Icons.Default.Smartphone,
                title = "Gemini Nano",
                description = "On-device AI model for privacy and speed. No internet required. Available on Pixel 8+ and select Android 15+ devices.",
            )

            HorizontalDivider()

            InfoRow(
                icon = Icons.Default.Cloud,
                title = "Gemini 2.5 Flash",
                description = "Cloud-based AI for all devices. Automatic fallback when Nano is unavailable. Requires internet connection.",
            )

            HorizontalDivider()

            InfoRow(
                icon = Icons.Default.AutoAwesome,
                title = "Smart Fallback",
                description = "The app automatically uses the best available AI backend. Cloud API is always available as a reliable backup.",
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    isError: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun getErrorExplanation(errorCode: Int): String {
    return when (errorCode) {
        606 -> "Error 606 (FEATURE_NOT_FOUND): The device's AI configuration hasn't been downloaded yet, or your bootloader is unlocked. This is common on first run or after a system update."
        601 -> "Error 601 (BUSY): The on-device AI quota has been exceeded. There's a usage limit to prevent battery drain and overheating."
        603 -> "Error 603 (BACKGROUND_USE_BLOCKED): On-device AI can only run when the app is in the foreground for security and privacy reasons."
        605 -> "Error 605 (DOWNLOAD_FAILED): The model download was interrupted or failed. This could be due to network issues or insufficient storage."
        else -> "An unexpected error occurred (code: $errorCode). The app will use the cloud AI as a fallback."
    }
}

private fun getErrorSolution(errorCode: Int): String {
    return when (errorCode) {
        606 -> "Solution: Restart the app or your device to trigger configuration download, then retry."
        601 -> "Solution: Wait a few minutes for the quota to reset, or use cloud AI in the meantime."
        603 -> "Solution: Keep the app in the foreground when using AI features, or use cloud AI for background processing."
        605 -> "Solution: Check your internet connection and available storage (model is ~100MB), then retry download."
        else -> "Solution: The cloud AI is working as a reliable fallback. You can retry the on-device download later."
    }
}
