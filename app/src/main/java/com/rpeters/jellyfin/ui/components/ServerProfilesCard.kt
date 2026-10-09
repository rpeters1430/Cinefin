package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.model.ServerProfile
import com.rpeters.jellyfin.data.model.ServerType

/**
 * Lists the saved server profiles so the user can switch between them, remove one, or add
 * another server. Jellyfin and Emby profiles appear in the same list.
 *
 * @param activeProfileId the profile behind the running session, or null when signed out.
 * @param onAddServer hidden when null (the connection screen is already the add-server form).
 */
@OptInAppExperimentalApis
@Composable
fun ServerProfilesCard(
    profiles: List<ServerProfile>,
    activeProfileId: String?,
    onSwitch: (ServerProfile) -> Unit,
    onRemove: (ServerProfile) -> Unit,
    onAddServer: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }

    ExpressiveContentCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SwitchAccount,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(id = R.string.server_profiles_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.size(4.dp))

            profiles.forEach { profile ->
                ServerProfileRow(
                    profile = profile,
                    isActive = profile.id == activeProfileId,
                    enabled = enabled,
                    onSwitch = { onSwitch(profile) },
                    onRemove = { pendingRemoval = profile.id },
                )
            }

            if (onAddServer != null) {
                Spacer(modifier = Modifier.size(4.dp))
                FilledTonalButton(
                    onClick = onAddServer,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.server_profiles_add))
                }
            }
        }
    }

    val removalTarget = profiles.firstOrNull { it.id == pendingRemoval }
    if (removalTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(id = R.string.server_profiles_remove_title)) },
            text = {
                Text(
                    stringResource(
                        id = R.string.server_profiles_remove_message,
                        removalTarget.username,
                        removalTarget.displayServerName(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemove(removalTarget)
                    },
                ) {
                    Text(stringResource(id = R.string.server_profiles_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(id = R.string.cancel))
                }
            },
        )
    }
}

/**
 * Sign-in currently stores the user's name as the server name, which cannot tell two servers
 * apart, so fall back to the host in that case.
 */
private fun ServerProfile.displayServerName(): String =
    serverName.takeIf { it.isNotBlank() && it != username }
        ?: runCatching { java.net.URI(serverUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: serverUrl

@Composable
private fun ServerProfileRow(
    profile: ServerProfile,
    isActive: Boolean,
    enabled: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    val typeLabel = stringResource(
        id = when (profile.serverType) {
            ServerType.JELLYFIN -> R.string.server_type_jellyfin
            ServerType.EMBY -> R.string.server_type_emby
        },
    )
    val switchLabel = stringResource(id = R.string.server_profiles_switch_to, profile.username, profile.displayServerName())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                enabled = enabled && !isActive,
                onClickLabel = switchLabel,
                role = Role.Button,
                onClick = onSwitch,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Dns,
            contentDescription = if (isActive) stringResource(id = R.string.server_profiles_active) else null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayServerName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(id = R.string.server_profiles_subtitle, profile.username, typeLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(
                    id = R.string.server_profiles_remove_description,
                    profile.username,
                    profile.displayServerName(),
                ),
            )
        }
    }
}
