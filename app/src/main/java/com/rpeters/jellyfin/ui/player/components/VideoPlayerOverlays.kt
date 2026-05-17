package com.rpeters.jellyfin.ui.player.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.ui.player.*

@UnstableApi
@Composable
internal fun BoxScope.VideoPlayerOverlays(
    state: VideoPlayerState,
    overlayState: VideoPlayerOverlayState,
    feedbackVisible: Boolean,
    feedbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    feedbackText: String,
    overlayScrim: Color,
    overlayContent: Color,
    currentPosMs: Long,
    onIntent: (VideoPlayerIntent) -> Unit,
    onClose: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onSyncPlayClick: () -> Unit,
    supportsPip: Boolean,
) {
    GestureFeedbackOverlay(
        visible = feedbackVisible,
        icon = feedbackIcon,
        text = feedbackText,
        overlayScrim = overlayScrim,
        overlayContent = overlayContent,
        modifier = Modifier.align(Alignment.Center),
    )

    SkipIntroOutroButtons(
        playerState = state,
        currentPosMs = currentPosMs,
        overlayScrim = overlayScrim,
        overlayContent = overlayContent,
        onSeek = { onIntent(VideoPlayerIntent.SeekTo(it)) },
    )

    NextEpisodeCountdownOverlay(
        visible = overlayState.showNextEpisodePrompt || state.showNextEpisodeCountdown,
        nextEpisode = state.nextEpisode,
        countdown = state.nextEpisodeCountdown,
        isCountdownActive = state.showNextEpisodeCountdown,
        overlayScrim = overlayScrim,
        overlayContent = overlayContent,
        onCancel = {
            if (state.showNextEpisodeCountdown) {
                onIntent(VideoPlayerIntent.CancelNextEpisodeCountdown)
            } else {
                onIntent(VideoPlayerIntent.DismissNextEpisodePrompt)
            }
        },
        onPlayNow = { onIntent(VideoPlayerIntent.PlayNextEpisode) },
        modifier = Modifier.align(Alignment.CenterEnd),
    )

    ExpressiveVideoControls(
        playerState = state,
        showPrimaryLoadingUi = overlayState.showPrimaryLoadingUi,
        onPlayPause = { onIntent(VideoPlayerIntent.TogglePlayPause) },
        onSeek = { onIntent(VideoPlayerIntent.SeekTo(it)) },
        onSeekBy = { delta ->
            onIntent(VideoPlayerIntent.SeekTo((currentPosMs + delta).coerceIn(0L, state.duration)))
        },
        onQualityClick = { onIntent(VideoPlayerIntent.ShowQualityDialog) },
        onAudioClick = { onIntent(VideoPlayerIntent.ShowAudioDialog) },
        onToggleMute = { onIntent(VideoPlayerIntent.ToggleMute) },
        onCastClick = { onIntent(VideoPlayerIntent.HandleCastButtonClick) },
        onSyncPlayClick = onSyncPlayClick,
        onSubtitlesClick = { onIntent(VideoPlayerIntent.ShowSubtitleDialog) },
        onAspectRatioChange = { onIntent(VideoPlayerIntent.ChangeAspectRatio(it)) },
        onPlaybackSpeedChange = { onIntent(VideoPlayerIntent.SetPlaybackSpeed(it)) },
        onBackClick = onClose,
        onPictureInPictureClick = onPictureInPictureClick,
        supportsPip = supportsPip,
        isVisible = state.isControlsVisible,
        overlayContent = overlayContent,
        overlayScrim = overlayScrim,
    )

    state.qualityRecommendation?.let { recommendation ->
        QualityRecommendationNotification(
            recommendation = recommendation,
            onAccept = { onIntent(VideoPlayerIntent.AcceptQualityRecommendation) },
            onDismiss = { onIntent(VideoPlayerIntent.DismissQualityRecommendation) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )
    }
}
