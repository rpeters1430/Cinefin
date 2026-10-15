package com.rpeters.jellyfin.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.model.ServerType

/** The string resource naming a [ServerType]'s product. */
@get:StringRes
val ServerType.labelRes: Int
    get() = when (this) {
        ServerType.JELLYFIN -> R.string.server_type_jellyfin
        ServerType.EMBY -> R.string.server_type_emby
        ServerType.PLEX -> R.string.server_type_plex
    }

/** The product name shown to users for a [ServerType]. */
@Composable
fun serverTypeLabel(serverType: ServerType): String = stringResource(id = serverType.labelRes)

/** A small pill naming the server product (Jellyfin, Emby or Plex) the app is connected to. */
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
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
