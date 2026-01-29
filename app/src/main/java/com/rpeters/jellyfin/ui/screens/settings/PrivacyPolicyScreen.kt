package com.rpeters.jellyfin.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R

@OptInAppExperimentalApis
@Composable
fun PrivacyPolicyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_policy_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
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
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.privacy_policy_last_updated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            PrivacyPolicySection(
                title = "1. Information We Collect",
                content = """
                    The App itself does not collect or store personal information such as names, email addresses, or payment details.
                    
                    However, the App may collect limited technical information for stability and performance purposes:
                    • Crash reports (via Firebase Crashlytics)
                    • Performance metrics (via Firebase Performance Monitoring)
                    • Device information such as Android version, device model, and app version
                    • Anonymous error logs related to app functionality
                    
                    This information is collected automatically and is not used to personally identify users.
                """.trimIndent(),
            )

            PrivacyPolicySection(
                title = "2. Media and Server Data",
                content = """
                    The App connects to a user-provided Jellyfin server to access media content.
                    
                    • Server URLs, authentication tokens, and user IDs are stored locally on the device only
                    • These credentials are never transmitted to the app developer
                    • All media streaming and account data flows directly between your device and your Jellyfin server
                    • The developer does not operate or control any Jellyfin servers.
                """.trimIndent(),
            )

            PrivacyPolicySection(
                title = "3. How Information Is Used",
                content = """
                    Collected information is used solely to:
                    • Improve app stability and performance
                    • Diagnose crashes and technical issues
                    • Ensure compatibility across devices and Android versions
                    
                    No data is sold, shared for advertising, or used for tracking users across apps.
                """.trimIndent(),
            )

            PrivacyPolicySection(
                title = "4. Third-Party Services",
                content = """
                    The App uses the following third-party services:
                    • Google Firebase
                      - Crashlytics
                      - Performance Monitoring
                    
                    These services may collect anonymous usage data in accordance with Google's Privacy Policy:
                    https://policies.google.com/privacy
                """.trimIndent(),
            )

            PrivacyPolicySection(
                title = "5. Data Retention",
                content = """
                    • Crash and performance data are retained only as long as necessary for diagnostics
                    • Locally stored server credentials remain on the device until the user clears app data or uninstalls the app
                """.trimIndent(),
            )

            PrivacyPolicySection(
                title = "6. Children's Privacy",
                content = """
                    The App does not knowingly collect personal information from children under 13.
                """.trimIndent(),
            )

            PrivacyPolicySection(
                title = "7. Changes to This Policy",
                content = """
                    This Privacy Policy may be updated from time to time. Changes will be reflected by updating the "Last updated" date.
                """.trimIndent(),
            )

            PrivacyPolicySection(
                title = "8. Contact",
                content = """
                    If you have questions about this Privacy Policy, you may contact the developer via the app's GitHub repository.
                """.trimIndent(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rpeters1430.github.io/JellyfinAndroid/privacy-policy.html"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.open_in_browser),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PrivacyPolicySection(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
