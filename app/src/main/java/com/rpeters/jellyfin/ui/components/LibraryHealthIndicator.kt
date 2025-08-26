package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.data.repository.common.LibraryHealthStatus

/**
 * A simple health indicator for libraries that shows status and recommendations.
 */
@Composable
fun LibraryHealthIndicator(
    libraryName: String,
    healthStatus: LibraryHealthStatus,
    recommendations: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (healthStatus == LibraryHealthStatus.HEALTHY || healthStatus == LibraryHealthStatus.UNKNOWN) {
        return // Don't show anything for healthy libraries
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Status indicator
                val statusInfo = when (healthStatus) {
                    LibraryHealthStatus.BLOCKED -> Pair(Color.Red, Icons.Default.Error)
                    LibraryHealthStatus.UNHEALTHY -> Pair(Color(0xFFFF9800), Icons.Default.Warning) // Orange
                    LibraryHealthStatus.WARNING -> Pair(Color(0xFFFFEB3B), Icons.Default.Warning) // Yellow
                    else -> Pair(MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle)
                }
                val color = statusInfo.first
                val icon = statusInfo.second

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, CircleShape),
                )

                Text(
                    text = "$libraryName - ${healthStatus.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (recommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                recommendations.forEach { recommendation ->
                    Text(
                        text = "• $recommendation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Shows a summary of library health issues.
 */
@Composable
fun LibraryHealthSummary(
    healthMap: Map<String, LibraryHealthStatus>,
    getRecommendations: (String) -> List<String> = { emptyList() },
    modifier: Modifier = Modifier,
) {
    val problematicLibraries = healthMap.filterValues {
        it != LibraryHealthStatus.HEALTHY && it != LibraryHealthStatus.UNKNOWN
    }

    if (problematicLibraries.isEmpty()) {
        return // No issues to show
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Library Issues",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        problematicLibraries.forEach { (libraryId, status) ->
            LibraryHealthIndicator(
                libraryName = "Library $libraryId",
                healthStatus = status,
                recommendations = getRecommendations(libraryId),
            )
        }
    }
}
