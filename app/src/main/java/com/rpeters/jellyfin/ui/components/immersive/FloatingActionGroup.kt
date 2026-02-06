package com.rpeters.jellyfin.ui.components.immersive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.rpeters.jellyfin.ui.theme.Dimens
import com.rpeters.jellyfin.ui.theme.ImmersiveDimens

/**
 * Multi-button floating action button group for immersive screens.
 * Supports both vertical and horizontal layouts.
 */
@Composable
fun FloatingActionGroup(
    modifier: Modifier = Modifier,
    orientation: FabOrientation = FabOrientation.Vertical,
    visible: Boolean = true,
    primaryAction: FabAction? = null,
    secondaryActions: List<FabAction> = emptyList()
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        when (orientation) {
            FabOrientation.Vertical -> {
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(ImmersiveDimens.FabSpacing),
                    horizontalAlignment = Alignment.End
                ) {
                    secondaryActions.forEach { action ->
                        SmallFloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription
                            )
                        }
                    }

                    primaryAction?.let { action ->
                        FloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = Dimens.Spacing8
                            )
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription
                            )
                        }
                    }
                }
            }

            FabOrientation.Horizontal -> {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(ImmersiveDimens.FabSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    primaryAction?.let { action ->
                        FloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription
                            )
                        }
                    }

                    secondaryActions.forEach { action ->
                        SmallFloatingActionButton(
                            onClick = action.onClick,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.contentDescription
                            )
                        }
                    }
                }
            }
        }
    }
}

data class FabAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

enum class FabOrientation {
    Vertical,
    Horizontal
}
