package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.utils.isWatched
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Reusable bottom sheet for media item actions (Play, Delete, Refresh, Mark Watched).
 * This component provides a consistent UX for managing media items across the app.
 *
 * @param item The media item to perform actions on
 * @param sheetState The bottom sheet state
 * @param onDismiss Callback when the sheet is dismissed
 * @param onPlay Callback to play the item
 * @param onDelete Callback to delete the item (receives success and error message)
 * @param onRefreshMetadata Callback to refresh metadata (receives success and error message)
 * @param onToggleWatched Callback to toggle watched status
 * @param showPlay Whether to show the Play action
 * @param showDelete Whether to show the Delete action (requires management enabled)
 * @param showRefresh Whether to show the Refresh Metadata action (requires management enabled)
 * @param showToggleWatched Whether to show the Mark Watched/Unwatched action
 * @param managementEnabled Whether library management actions are enabled
 */
@OptInAppExperimentalApis
@Composable
fun MediaItemActionsSheet(
    item: BaseItemDto,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onDelete: ((Boolean, String?) -> Unit)? = null,
    onRefreshMetadata: ((Boolean, String?) -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    showPlay: Boolean = true,
    showDelete: Boolean = true,
    showRefresh: Boolean = true,
    showToggleWatched: Boolean = true,
    managementEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val itemName = item.name ?: stringResource(id = R.string.unknown)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(Dimens.Spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.Spacing12),
        ) {
            // Header
            Text(
                text = stringResource(id = R.string.media_actions_sheet_title, itemName),
                style = MaterialTheme.typography.titleMedium,
            )

            if (showDelete || showRefresh) {
                Text(
                    text = stringResource(id = R.string.media_actions_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Play Action
            if (showPlay && onPlay != null) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onPlay()
                    },
                    modifier = Modifier.padding(vertical = Dimens.Spacing4),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = Dimens.Spacing8),
                    )
                    Text(text = stringResource(id = R.string.play))
                }
            }

            // Toggle Watched Action
            if (showToggleWatched && onToggleWatched != null) {
                val isWatched = item.isWatched()
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onToggleWatched()
                    },
                    modifier = Modifier.padding(vertical = Dimens.Spacing4),
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Outlined.Circle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(end = Dimens.Spacing8),
                    )
                    Text(
                        text = stringResource(
                            id = if (isWatched) R.string.mark_as_unwatched else R.string.mark_as_watched,
                        ),
                    )
                }
            }

            // Management Actions Section
            if (managementEnabled && (showDelete || showRefresh)) {
                HorizontalDivider()

                Text(
                    text = stringResource(id = R.string.media_actions_management_section),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Refresh Metadata Action
                if (showRefresh && onRefreshMetadata != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onRefreshMetadata(false, "Not yet implemented")
                        },
                        modifier = Modifier.padding(vertical = Dimens.Spacing4),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Dimens.Spacing8),
                        )
                        Text(text = stringResource(id = R.string.library_actions_refresh_metadata))
                    }
                }

                // Delete Action
                if (showDelete && onDelete != null) {
                    Button(
                        onClick = {
                            showDeleteConfirmation = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.padding(vertical = Dimens.Spacing4),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Dimens.Spacing8),
                        )
                        Text(text = stringResource(id = R.string.library_actions_delete))
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(text = stringResource(id = R.string.library_actions_delete_confirm_title))
            },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.library_actions_delete_confirm_message,
                        itemName,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete(true, null) // Trigger deletion - caller handles the actual delete
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
        )
    }
}
