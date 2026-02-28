package com.rpeters.jellyfin.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.jellyfin.OptInAppExperimentalApis
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.preferences.SubtitleAppearancePreferences
import com.rpeters.jellyfin.data.preferences.SubtitleBackground
import com.rpeters.jellyfin.data.preferences.SubtitleFont
import com.rpeters.jellyfin.data.preferences.SubtitleTextSize
import com.rpeters.jellyfin.ui.components.ExpressiveRadioListItem
import com.rpeters.jellyfin.ui.viewmodel.SubtitleAppearancePreferencesViewModel

/**
 * Enhanced Subtitle Settings screen with live preview and modern Material 3 Expressive UI.
 */
@OptInAppExperimentalApis
@Composable
fun SubtitleSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubtitleAppearancePreferencesViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.settings_subtitles_title),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Live Subtitle Preview
            SubtitlePreviewCard(preferences = preferences)

            // Text Size Section
            ExpressiveSubtitleSection(
                title = stringResource(R.string.settings_subtitles_text_size),
                icon = Icons.Default.FormatSize,
            ) {
                SubtitleSizeRow(
                    selectedSize = preferences.textSize,
                    onSizeSelect = { viewModel.setTextSize(it) }
                )
            }

            // Background Style Section
            ExpressiveSubtitleSection(
                title = stringResource(R.string.settings_subtitles_background),
                icon = Icons.Default.Layers,
            ) {
                SubtitleBackgroundRow(
                    selectedBackground = preferences.background,
                    onBackgroundSelect = { viewModel.setBackground(it) }
                )
            }

            // Font Selection Section
            ExpressiveSubtitleSection(
                title = stringResource(R.string.settings_subtitles_font),
                icon = Icons.Default.FontDownload,
            ) {
                SubtitleFont.entries.forEach { font ->
                    ExpressiveRadioListItem(
                        title = subtitleFontLabel(font),
                        selected = preferences.font == font,
                        onSelect = { viewModel.setFont(font) },
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SubtitlePreviewCard(
    preferences: SubtitleAppearancePreferences,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Mock Movie Background (Gradient + Icon to simulate a scene)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1A1A1A), Color(0xFF333333))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.1f)
                )
            }

            // Subtitle Rendering
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                val backgroundColor = when (preferences.background) {
                    SubtitleBackground.NONE -> Color.Transparent
                    SubtitleBackground.BLACK -> Color.Black
                    SubtitleBackground.SEMI_TRANSPARENT -> Color.Black.copy(alpha = 0.5f)
                }
                
                val fontFamily = when (preferences.font) {
                    SubtitleFont.SERIF, SubtitleFont.ROBOTO_SERIF -> FontFamily.Serif
                    SubtitleFont.MONOSPACE, SubtitleFont.ROBOTO_MONO -> FontFamily.Monospace
                    else -> FontFamily.SansSerif
                }

                Surface(
                    color = backgroundColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "The quick brown fox jumps over the lazy dog.",
                        color = Color.White,
                        fontSize = preferences.textSize.sizeSp.sp,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Preview Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SubtitleSizeRow(
    selectedSize: SubtitleTextSize,
    onSizeSelect: (SubtitleTextSize) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SubtitleTextSize.entries.forEach { size ->
            val isSelected = selectedSize == size
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                label = "color"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(containerColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.medium
                    )
                    .clickable { onSizeSelect(size) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = (12 + (SubtitleTextSize.entries.indexOf(size) * 4)).sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubtitleBackgroundRow(
    selectedBackground: SubtitleBackground,
    onBackgroundSelect: (SubtitleBackground) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubtitleBackground.entries.forEach { background ->
            val isSelected = selectedBackground == background
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                label = "color"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(containerColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.medium
                    )
                    .clickable { onBackgroundSelect(background) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Background visual representation
                Box(
                    modifier = Modifier
                        .size(40.dp, 24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val boxColor = when (background) {
                        SubtitleBackground.NONE -> Color.Transparent
                        SubtitleBackground.BLACK -> Color.Black
                        SubtitleBackground.SEMI_TRANSPARENT -> Color.Black.copy(alpha = 0.5f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(8.dp)
                            .background(boxColor)
                    )
                }
                Text(
                    text = subtitleBackgroundLabel(background).split(" ").first(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSubtitleSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                )
            }
            content()
        }
    }
}

@Composable
private fun subtitleFontLabel(font: SubtitleFont): String {
    return when (font) {
        SubtitleFont.DEFAULT -> stringResource(R.string.settings_subtitles_font_default)
        SubtitleFont.SANS_SERIF -> stringResource(R.string.settings_subtitles_font_sans_serif)
        SubtitleFont.SERIF -> stringResource(R.string.settings_subtitles_font_serif)
        SubtitleFont.MONOSPACE -> stringResource(R.string.settings_subtitles_font_monospace)
        SubtitleFont.ROBOTO -> stringResource(R.string.settings_subtitles_font_roboto)
        SubtitleFont.ROBOTO_FLEX -> stringResource(R.string.settings_subtitles_font_roboto_flex)
        SubtitleFont.ROBOTO_SERIF -> stringResource(R.string.settings_subtitles_font_roboto_serif)
        SubtitleFont.ROBOTO_MONO -> stringResource(R.string.settings_subtitles_font_roboto_mono)
    }
}

@Composable
private fun subtitleBackgroundLabel(background: SubtitleBackground): String {
    return when (background) {
        SubtitleBackground.NONE -> stringResource(R.string.settings_subtitles_background_none)
        SubtitleBackground.BLACK -> stringResource(R.string.settings_subtitles_background_black)
        SubtitleBackground.SEMI_TRANSPARENT -> stringResource(R.string.settings_subtitles_background_semi)
    }
}
