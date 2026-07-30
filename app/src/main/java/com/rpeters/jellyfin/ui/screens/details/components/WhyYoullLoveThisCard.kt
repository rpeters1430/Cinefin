@file:OptInAppExperimentalApis

package com.rpeters.jellyfin.ui.screens.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.ui.components.ExpressiveWavyLinearLoading
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "Why You'll Love This" AI-powered personalized pitch card.
 * Shows 3 lines by default; tap to expand to full text.
 */
@Composable
fun WhyYoullLoveThisCard(
    pitch: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "why_love_chevron_rotation",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (pitch != null && !isLoading) Modifier.clickable { isExpanded = !isExpanded } else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Generated",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Why You'll Love This",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (pitch != null && !isLoading) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Show less" else "Show more",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation),
                    )
                }
            }

            // Content
            when {
                isLoading -> {
                    ExpressiveWavyLinearLoading(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                pitch != null -> {
                    // Always show 3 lines; expand on tap to reveal full text
                    Text(
                        text = pitch,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.times(1.5f),
                        textAlign = TextAlign.Center,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
