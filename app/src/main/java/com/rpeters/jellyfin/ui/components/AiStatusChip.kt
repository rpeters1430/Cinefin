package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.data.ai.AiDownloadState

/**
 * A small chip that displays the current AI processing mode (On-Device vs Cloud).
 */
@Composable
fun AiStatusChip(
    state: AiDownloadState,
    isNanoActive: Boolean,
    modifier: Modifier = Modifier
) {
    val (color, icon, text) = when {
        isNanoActive -> Triple(
            Color(0xFF4CAF50), // Green
            Icons.Default.Memory,
            "On-Device"
        )
        state == AiDownloadState.DOWNLOADING -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Sync,
            "Downloading AI..."
        )
        state == AiDownloadState.FAILED -> Triple(
            MaterialTheme.colorScheme.error,
            Icons.Default.Sync,
            "Download Failed"
        )
        else -> Triple(
            MaterialTheme.colorScheme.primary,
            Icons.Default.Cloud,
            "Cloud AI"
        )
    }

    Surface(
        color = color.copy(alpha = 0.9f),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
