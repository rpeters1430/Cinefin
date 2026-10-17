package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.viewmodel.PlexServerOption

/** The servers on a plex.tv account: name, whose it is, and whether plex.tv sees it online. */
@Composable
fun PlexServerPicker(
    servers: List<PlexServerOption>,
    connectingTo: String?,
    onServerSelected: (PlexServerOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.plex_server_picker_title), style = MaterialTheme.typography.titleMedium)
        servers.forEach { server ->
            PlexServerRow(
                server = server,
                isConnecting = server.id == connectingTo,
                enabled = enabled && connectingTo == null,
                onClick = { onServerSelected(server) },
            )
        }
    }
}

@Composable
private fun PlexServerRow(
    server: PlexServerOption,
    isConnecting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val owner = when {
        server.owned -> stringResource(R.string.plex_server_owned)
        server.ownerName != null -> stringResource(R.string.plex_server_shared_by, server.ownerName)
        else -> stringResource(R.string.plex_server_shared)
    }
    val presence = stringResource(if (server.online) R.string.plex_server_online else R.string.plex_server_offline)
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.Dns, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.server_profiles_subtitle, owner, presence),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (server.online) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (isConnecting) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
