package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.viewmodel.PlexServerOption
import com.rpeters.jellyfin.ui.viewmodel.PlexServerProblem
import com.rpeters.jellyfin.ui.viewmodel.PlexSignInStep

/**
 * "Sign in with Plex" on the connection screen: the PIN code with a link to approve it on
 * plex.tv, then the account's servers to pick from.
 *
 * @param plexUrlEntered the address typed into the sign-in form is a Plex server, so this card
 *   explains that Plex servers are added here instead.
 */
@Composable
fun PlexSignInCard(
    step: PlexSignInStep,
    onStart: () -> Unit,
    onServerSelected: (PlexServerOption) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    plexUrlEntered: Boolean = false,
    enabled: Boolean = true,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.plex_sign_in_title), style = MaterialTheme.typography.titleLarge)
            PlexSignInStepContent(step, onStart, onServerSelected, plexUrlEntered, enabled)
            if (step != PlexSignInStep.Idle) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.plex_sign_in_cancel)) }
            }
        }
    }
}

@Composable
private fun ColumnScope.PlexSignInStepContent(
    step: PlexSignInStep,
    onStart: () -> Unit,
    onServerSelected: (PlexServerOption) -> Unit,
    plexUrlEntered: Boolean,
    enabled: Boolean,
) {
    when (step) {
        PlexSignInStep.Idle -> StartSignIn(onStart, plexUrlEntered, enabled)
        PlexSignInStep.Starting -> Busy(stringResource(R.string.plex_sign_in_starting))
        is PlexSignInStep.WaitingForApproval -> PinCode(step)
        PlexSignInStep.LoadingServers -> Busy(stringResource(R.string.plex_sign_in_loading_servers))
        is PlexSignInStep.ChooseServer -> ChooseServer(step, onServerSelected, enabled)
        PlexSignInStep.NoServers -> ErrorText(stringResource(R.string.plex_sign_in_no_servers))
        PlexSignInStep.Expired -> RetryAfter(stringResource(R.string.plex_sign_in_expired), onStart, enabled)
        is PlexSignInStep.Failed -> RetryAfter(step.message, onStart, enabled)
    }
}

@Composable
private fun StartSignIn(onStart: () -> Unit, plexUrlEntered: Boolean, enabled: Boolean) {
    val label = stringResource(R.string.plex_sign_in_start)
    if (plexUrlEntered) {
        Text(stringResource(R.string.plex_sign_in_url_detected))
        Button(onClick = onStart, enabled = enabled) { Text(label) }
    } else {
        Text(stringResource(R.string.plex_sign_in_intro))
        OutlinedButton(onClick = onStart, enabled = enabled) { Text(label) }
    }
}

@Composable
private fun ChooseServer(
    step: PlexSignInStep.ChooseServer,
    onServerSelected: (PlexServerOption) -> Unit,
    enabled: Boolean,
) {
    PlexServerPicker(
        servers = step.servers,
        connectingTo = step.connectingTo,
        onServerSelected = onServerSelected,
        enabled = enabled,
    )
    step.connectingTo?.let { id ->
        val name = step.servers.firstOrNull { it.id == id }?.name.orEmpty()
        Busy(stringResource(R.string.plex_server_connecting, name))
    }
    step.problem?.let { problem ->
        val message = when (problem) {
            PlexServerProblem.UNREACHABLE -> R.string.plex_server_unreachable
            PlexServerProblem.NO_ACCESS -> R.string.plex_server_no_access
        }
        ErrorText(stringResource(message, step.problemServerName.orEmpty()))
    }
}

@Composable
private fun RetryAfter(message: String, onStart: () -> Unit, enabled: Boolean) {
    ErrorText(message)
    Button(onClick = onStart, enabled = enabled) { Text(stringResource(R.string.plex_sign_in_retry)) }
}

@Composable
private fun ColumnScope.PinCode(step: PlexSignInStep.WaitingForApproval) {
    val uriHandler = LocalUriHandler.current
    val codeDescription = stringResource(R.string.plex_sign_in_code_description, step.code.toList().joinToString(" "))
    Text(stringResource(R.string.plex_sign_in_code_instructions))
    Text(
        text = step.code,
        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .semantics { contentDescription = codeDescription },
    )
    Button(
        onClick = {
            // No browser installed: the plex.tv/link fallback below still works from another device.
            runCatching { uriHandler.openUri(step.authUrl) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.plex_sign_in_open))
    }
    Text(
        stringResource(R.string.plex_sign_in_link_fallback),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Busy(stringResource(R.string.plex_sign_in_waiting))
}

@Composable
private fun Busy(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(label)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
