package com.rpeters.jellyfin.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.components.ExpressiveSwitchListItem
import com.rpeters.jellyfin.ui.viewmodel.PrivacySettingsViewModel

@OptInAppExperimentalApis
@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PrivacySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.settings_privacy_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_privacy_biometric_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                ExpressiveSwitchListItem(
                    title = stringResource(id = R.string.settings_privacy_biometric_toggle),
                    subtitle = stringResource(id = R.string.settings_privacy_biometric_description),
                    checked = state.biometricEnabled,
                    onCheckedChange = viewModel::setBiometricLoginEnabled,
                    enabled = state.biometricAvailable || state.biometricEnabled,
                    leadingIcon = Icons.Default.Fingerprint,
                )

                if (!state.biometricAvailable) {
                    Text(
                        text = stringResource(id = R.string.settings_privacy_biometric_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (state.biometricEnabled) {
                    ExpressiveSwitchListItem(
                        title = stringResource(id = R.string.biometric_require_strong),
                        subtitle = stringResource(id = R.string.biometric_require_strong_description),
                        checked = state.requireStrongBiometric,
                        onCheckedChange = viewModel::setRequireStrongBiometric,
                        enabled = (state.biometricAvailable || state.requireStrongBiometric) &&
                            !state.requireDeviceAuthForCredentials,
                        leadingIcon = Icons.Default.Security,
                    )

                    if (state.biometricWeakOnly) {
                        Text(
                            text = stringResource(id = R.string.biometric_weak_only_notice_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_credential_security_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                ExpressiveSwitchListItem(
                    title = stringResource(id = R.string.settings_credential_security_toggle),
                    subtitle = stringResource(id = R.string.settings_credential_security_description),
                    checked = state.requireDeviceAuthForCredentials,
                    onCheckedChange = viewModel::setRequireDeviceAuthForCredentials,
                    enabled = (state.biometricAvailable || state.requireDeviceAuthForCredentials) &&
                        !state.isUpdatingCredentialSecurity,
                    leadingIcon = Icons.Default.Security,
                )

                Text(
                    text = stringResource(id = R.string.settings_credential_security_tradeoff),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
