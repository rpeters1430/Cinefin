package com.rpeters.jellyfin.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.theme.MotionTokens
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun GestureFeedbackOverlay(
    visible: Boolean,
    icon: ImageVector,
    text: String,
    overlayScrim: Color,
    overlayContent: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = overlayScrim,
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(120.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = overlayContent,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = text,
                    color = overlayContent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun SkipIntroOutroButtons(
    playerState: VideoPlayerState,
    currentPosMs: Long,
    overlayScrim: Color,
    overlayContent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSkipIntro = remember(playerState.introStartMs, playerState.introEndMs, currentPosMs) {
        val s = playerState.introStartMs
        val e = playerState.introEndMs
        s != null && e != null && currentPosMs in s..e
    }
    val showSkipOutro = remember(playerState.outroStartMs, currentPosMs) {
        val s = playerState.outroStartMs
        s != null && currentPosMs >= s
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showSkipIntro) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp),
            ) {
                Surface(
                    color = overlayScrim,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        text = "Skip Intro",
                        color = overlayContent,
                        modifier = Modifier
                            .clickable {
                                val target = playerState.introEndMs ?: (currentPosMs + 10_000)
                                onSeek(target)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (showSkipOutro) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 130.dp, end = 16.dp),
            ) {
                Surface(
                    color = overlayScrim,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        text = "Skip Credits",
                        color = overlayContent,
                        modifier = Modifier
                            .clickable {
                                val target = playerState.outroEndMs ?: (currentPosMs + 10_000) // Fallback
                                onSeek(target)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun NextEpisodeCountdownOverlay(
    visible: Boolean,
    nextEpisode: BaseItemDto?,
    countdown: Int,
    overlayScrim: Color,
    overlayContent: Color,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = MotionTokens.expressiveEnter),
        exit = fadeOut(animationSpec = MotionTokens.expressiveExit),
        modifier = modifier,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = overlayScrim,
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .padding(bottom = 100.dp)
                .fillMaxWidth(0.85f),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Next Episode",
                    color = overlayContent,
                    style = MaterialTheme.typography.headlineSmall,
                )
                nextEpisode?.let { nextEp ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = nextEp.name ?: "Episode ${nextEp.indexNumber}",
                        color = overlayContent,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Starting in $countdown...",
                    color = overlayContent.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = overlayContent,
                        ),
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = onPlayNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("Play Now")
                    }
                }
            }
        }
    }
}
