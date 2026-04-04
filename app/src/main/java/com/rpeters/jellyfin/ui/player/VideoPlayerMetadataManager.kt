package com.rpeters.jellyfin.ui.player

import androidx.media3.common.util.UnstableApi
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * Handles metadata loading, skip markers, and next episode logic.
 */
@UnstableApi
class VideoPlayerMetadataManager @Inject constructor(
    private val repository: IJellyfinRepository,
    private val stateManager: VideoPlayerStateManager
) {
    private var countdownJob: Job? = null

    suspend fun loadSkipMarkers(itemId: String): BaseItemDto? {
        return try {
            val item = when (val ep = repository.getEpisodeDetails(itemId)) {
                is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> ep.data
                else -> when (val mv = repository.getMovieDetails(itemId)) {
                    is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> mv.data
                    else -> null
                }
            }

            if (item == null) {
                stateManager.updateState { it.copy(
                    introStartMs = null,
                    introEndMs = null,
                    outroStartMs = null,
                    outroEndMs = null,
                ) }
                return null
            }

            val chapters = item.chapters ?: emptyList()
            if (chapters.isEmpty()) {
                stateManager.updateState { it.copy(
                    introStartMs = null,
                    introEndMs = null,
                    outroStartMs = null,
                    outroEndMs = null,
                ) }
                return item
            }

            fun ticksToMs(ticks: Long?): Long? = ticks?.let { it / 10_000 }

            var introStart: Long? = null
            var introEnd: Long? = null
            var outroStart: Long? = null
            var outroEnd: Long? = null

            chapters.forEachIndexed { index, ch ->
                val name = ch.name?.lowercase() ?: ""
                val startMs = ticksToMs(ch.startPositionTicks)
                val nextStartMs = chapters.getOrNull(index + 1)?.startPositionTicks?.let { it / 10_000 }
                val endMs = nextStartMs

                if (introStart == null && ("intro" in name || "opening" in name)) {
                    introStart = startMs
                    introEnd = endMs
                }
                if (outroStart == null && ("credits" in name || "outro" in name || "ending" in name)) {
                    outroStart = startMs
                    outroEnd = endMs
                }
            }

            stateManager.updateState { it.copy(
                introStartMs = introStart,
                introEndMs = introEnd,
                outroStartMs = outroStart,
                outroEndMs = outroEnd,
            ) }

            item
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadNextEpisodeIfAvailable(metadata: BaseItemDto?) {
        if (metadata == null) return

        val seasonId = metadata.seasonId?.toString() ?: return
        val currentEpisodeIndex = metadata.indexNumber ?: return

        try {
            when (val result = repository.getEpisodesForSeason(seasonId)) {
                is com.rpeters.jellyfin.data.repository.common.ApiResult.Success -> {
                    val episodes = result.data.sortedBy { it.indexNumber }
                    val currentIndex = episodes.indexOfFirst { it.indexNumber == currentEpisodeIndex }
                    val nextEpisode = if (currentIndex >= 0) episodes.getOrNull(currentIndex + 1) else null
                    stateManager.updateState { it.copy(nextEpisode = nextEpisode) }
                }
                else -> {
                    stateManager.updateState { it.copy(nextEpisode = null) }
                }
            }
        } catch (e: Exception) {
            SecureLogger.e("VideoPlayerMetadata", "Failed to load next episode", e)
            stateManager.updateState { it.copy(nextEpisode = null) }
        }
    }

    /**
     * Extract subtitle specifications from item metadata for casting support
     * @param item The media item metadata
     * @param playbackInfo Pre-fetched playback info to avoid redundant API calls
     */
    suspend fun extractSubtitleSpecs(
        item: BaseItemDto?,
        playbackInfo: org.jellyfin.sdk.model.api.PlaybackInfoResponse?,
    ): List<SubtitleSpec> {
        if (item == null || playbackInfo == null) return emptyList()

        return try {
            val subtitleSpecs = mutableListOf<SubtitleSpec>()
            val itemId = item.id.toString()
            val serverUrl = repository.getCurrentServerSync()?.url ?: return emptyList()

            val mediaSource = playbackInfo.mediaSources.firstOrNull() ?: return emptyList()

            // Extract subtitle streams
            mediaSource.mediaStreams
                ?.filter { stream -> stream.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE }
                ?.forEach { stream ->
                    val codec = stream.codec?.lowercase() ?: return@forEach
                    val language = stream.language ?: "und"
                    val displayTitle = stream.displayTitle ?: stream.title ?: language.uppercase()

                    // Map codec to MIME type - Convert all text formats to VTT for compatibility
                    val (mimeType, extension) = when (codec) {
                        "srt", "subrip", "vtt", "webvtt", "ass", "ssa", "ttml" ->
                            androidx.media3.common.MimeTypes.TEXT_VTT to "vtt"
                        else -> null to null
                    }

                    if (mimeType != null && extension != null) {
                        // Build subtitle URL WITHOUT api_key in query params
                        // AuthInterceptor will handle authentication via headers for these server requests
                        val subtitleUrl = if (stream.isExternal && !stream.deliveryUrl.isNullOrBlank()) {
                            buildServerUrl(serverUrl, stream.deliveryUrl!!)
                        } else {
                            "$serverUrl/Videos/$itemId/${mediaSource.id}/Subtitles/${stream.index}/Stream.$extension"
                        }

                        subtitleSpecs.add(
                            SubtitleSpec(
                                url = subtitleUrl,
                                mimeType = mimeType,
                                language = language,
                                label = displayTitle,
                                isForced = stream.isForced == true,
                            ),
                        )

                        SecureLogger.d("VideoPlayerMetadata", "Added subtitle spec: $displayTitle ($language) - $codec -> $extension [External: ${stream.isExternal}]")
                    }
                }

            subtitleSpecs
        } catch (e: Exception) {
            SecureLogger.e("VideoPlayerMetadata", "Failed to extract subtitle specs", e)
            emptyList()
        }
    }

    fun extractSubtitleTracks(
        playbackInfo: org.jellyfin.sdk.model.api.PlaybackInfoResponse?,
        selectedSubtitleIndex: Int? = null,
    ): List<TrackInfo> {
        val mediaSource = playbackInfo?.mediaSources?.firstOrNull() ?: return emptyList()

        return mediaSource.mediaStreams
            ?.filter { stream -> stream.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE }
            ?.map { stream ->
                val streamIndex = stream.index
                val language = stream.language ?: "und"
                val displayTitle = stream.displayTitle ?: stream.title ?: language.uppercase()
                val format = androidx.media3.common.Format.Builder()
                    .setId(streamIndex.toString())
                    .setLanguage(language)
                    .setLabel(displayTitle)
                    .build()

                TrackInfo(
                    groupIndex = -1,
                    trackIndex = streamIndex,
                    format = format,
                    isSelected = if (selectedSubtitleIndex != null) {
                        selectedSubtitleIndex == streamIndex
                    } else {
                        stream.isDefault
                    },
                    displayName = displayTitle,
                )
            }
            .orEmpty()
    }

    private fun buildServerUrl(serverUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val normalizedServer = serverUrl.removeSuffix("/")
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizedServer + normalizedPath
    }

    fun startNextEpisodeCountdown(scope: CoroutineScope, onCountdownFinished: () -> Unit) {
        val countdownSeconds = 10
        stateManager.updateState { it.copy(
            showNextEpisodeCountdown = true,
            nextEpisodeCountdown = countdownSeconds,
        ) }

        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (i in countdownSeconds downTo 1) {
                stateManager.updateState { it.copy(nextEpisodeCountdown = i) }
                delay(1000)
            }
            onCountdownFinished()
        }
    }

    fun cancelNextEpisodeCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        stateManager.updateState { it.copy(
            showNextEpisodeCountdown = false,
            nextEpisodeCountdown = 0,
        ) }
    }
}
