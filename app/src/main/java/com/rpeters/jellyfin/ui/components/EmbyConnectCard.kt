package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.data.emby.EmbyConnectServer
import com.rpeters.jellyfin.ui.viewmodel.EmbyConnectState

@Composable
fun EmbyConnectCard(
    state: EmbyConnectState,
    onSignIn: (String, String) -> Unit,
    onServerSelected: (EmbyConnectServer) -> Unit,
    onSkip: () -> Unit,
    enabled: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Emby Connect", style = MaterialTheme.typography.titleLarge)
            if (!expanded) {
                Text("Use your Emby account to find your linked servers, or enter a server address below.")
                OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text("Sign in with Emby Connect") }
            } else {
                if (state.servers.isEmpty()) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Emby username or email") },
                        singleLine = true, enabled = enabled && !state.isBusy, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("Emby account password") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        enabled = enabled && !state.isBusy, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { onSignIn(name, password); password = "" },
                        enabled = enabled && !state.isBusy && name.isNotBlank() && password.isNotBlank()) {
                        Text("Sign in")
                    }
                } else {
                    Text("Choose your Emby server", style = MaterialTheme.typography.titleMedium)
                    state.servers.forEach { server ->
                        OutlinedButton(
                            onClick = { onServerSelected(server) }, enabled = enabled && !state.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(server.name) }
                    }
                }
                if (state.isBusy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Connecting to Emby…")
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { password = ""; expanded = false; onSkip() }) {
                    Text("Skip — enter server manually")
                }
            }
        }
    }
}
