package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.offline.VideoQuality
import com.rpeters.jellyfin.ui.downloads.DownloadsViewModel
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun QualitySelectionDialog(
    item: BaseItemDto,
    onDismiss: () -> Unit,
    onQualitySelected: (VideoQuality) -> Unit,
    downloadsViewModel: DownloadsViewModel,
) {
    val presets = remember(item) { downloadsViewModel.getAvailableQualityPresets(item) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_download_quality)) },
        text = {
            Column {
                presets.forEach { quality ->
                    TextButton(
                        onClick = { onQualitySelected(quality) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            quality.label,
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
