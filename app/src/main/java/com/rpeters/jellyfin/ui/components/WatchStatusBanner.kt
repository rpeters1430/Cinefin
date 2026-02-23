package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.utils.getWatchedPercentage
import com.rpeters.jellyfin.utils.isPartiallyWatched
import com.rpeters.jellyfin.utils.isWatched
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.math.roundToInt

@Composable
fun WatchStatusBanner(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
) {
    val isWatched = item.isWatched()
    val isPartiallyWatched = item.isPartiallyWatched()

    if (!isWatched && !isPartiallyWatched) return

    val statusText = if (isWatched) {
        "Watched"
    } else {
        "${item.getWatchedPercentage().roundToInt()}% watched"
    }

    val statusIcon = if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.PlayCircle

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
