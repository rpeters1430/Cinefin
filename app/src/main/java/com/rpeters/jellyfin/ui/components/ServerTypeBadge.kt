package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.model.ServerType

/** The product name shown to users for a [ServerType]. */
@Composable
fun serverTypeLabel(serverType: ServerType): String = when (serverType) {
    ServerType.JELLYFIN -> stringResource(id = R.string.server_type_jellyfin)
    ServerType.EMBY -> stringResource(id = R.string.server_type_emby)
}

/** A small pill naming the server product (Jellyfin or Emby) the app is connected to. */
@Composable
fun ServerTypeBadge(
    serverType: ServerType,
    modifier: Modifier = Modifier,
) {
    val label = serverTypeLabel(serverType)
    val description = stringResource(id = R.string.server_type_badge_description, label)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
