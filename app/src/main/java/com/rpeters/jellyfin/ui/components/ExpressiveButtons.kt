package com.rpeters.jellyfin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rpeters.jellyfin.OptInAppExperimentalApis

/**
 * Material 3 Expressive Buttons
 *
 * Enhanced button components with Material 3 Expressive styling, animations, and interactions.
 * These buttons provide better visual hierarchy and more engaging interactions.
 */

/**
 * Expressive Filled Button - Highest emphasis
 *
 * Use for primary actions like "Play", "Download", "Save"
 *
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param shape Shape of the button
 * @param colors Button colors
 * @param elevation Button elevation
 * @param border Border for the button
 * @param contentPadding Padding for button content
 * @param interactionSource Interaction source for the button
 * @param content Button content (text, icon, etc.)
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Expressive Elevated Button - High emphasis with shadow
 *
 * Use for important secondary actions like "Add to Favorites", "Share"
 *
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param shape Shape of the button
 * @param colors Button colors
 * @param elevation Button elevation (defaults to elevated appearance)
 * @param border Border for the button
 * @param contentPadding Padding for button content
 * @param interactionSource Interaction source for the button
 * @param content Button content (text, icon, etc.)
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Expressive Tonal Button - Medium emphasis
 *
 * Use for secondary actions in context like "View Details", "More Info"
 *
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param shape Shape of the button
 * @param colors Button colors
 * @param elevation Button elevation
 * @param border Border for the button
 * @param contentPadding Padding for button content
 * @param interactionSource Interaction source for the button
 * @param content Button content (text, icon, etc.)
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Expressive Outlined Button - Medium-low emphasis
 *
 * Use for alternative actions like "Cancel", "Not Now"
 *
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param shape Shape of the button
 * @param colors Button colors
 * @param elevation Button elevation
 * @param border Border for the button (defaults to outlined style)
 * @param contentPadding Padding for button content
 * @param interactionSource Interaction source for the button
 * @param content Button content (text, icon, etc.)
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Expressive Text Button - Lowest emphasis
 *
 * Use for tertiary actions like "Learn More", "Skip"
 *
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param shape Shape of the button
 * @param colors Button colors
 * @param elevation Button elevation
 * @param border Border for the button
 * @param contentPadding Padding for button content
 * @param interactionSource Interaction source for the button
 * @param content Button content (text, icon, etc.)
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

// ===== Convenience Buttons with Icons =====

/**
 * Expressive Icon Button - Filled style with icon and text
 *
 * Common pattern for media actions
 *
 * @param text Button text
 * @param icon Icon to display
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveIconButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ExpressiveFilledButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        Text(text)
    }
}

/**
 * Expressive Play Button - Pre-styled for video/audio playback
 *
 * Prominent filled button optimized for play actions
 *
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param text Button text (defaults to "Play")
 */
@OptInAppExperimentalApis
@Composable
fun ExpressivePlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Play",
) {
    ExpressiveFilledButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Expressive More Info Button - Pre-styled for detail/info actions
 *
 * Tonal button optimized for navigating to details
 *
 * @param onClick Called when button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param text Button text (defaults to "More Info")
 */
@OptInAppExperimentalApis
@Composable
fun ExpressiveMoreInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "More Info",
) {
    ExpressiveTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
