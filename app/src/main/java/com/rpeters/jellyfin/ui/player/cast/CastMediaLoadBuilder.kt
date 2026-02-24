package com.rpeters.jellyfin.ui.player.cast

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.images.WebImage
import com.rpeters.jellyfin.R
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.JellyfinStreamRepository
import com.rpeters.jellyfin.ui.player.SubtitleSpec
import com.rpeters.jellyfin.utils.AppResources
import com.rpeters.jellyfin.utils.SecureLogger
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Responsible for constructing the media URLs, metadata, and track information
 * required for a Cast load request.
 */
@UnstableApi
@Singleton
class CastMediaLoadBuilder @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val repository: JellyfinRepository,
    private val streamRepository: JellyfinStreamRepository,
) {

    data class PlaybackData(
        val url: String,
        val mimeType: String,
        val playSessionId: String?,
        val mediaSourceId: String?,
        val urlType: String,
    )

    /**
     * Resolves the playback URL and configuration for a Jellyfin item.
     */
    suspend fun resolvePlaybackData(itemId: String, castContext: CastContext?): PlaybackData? {
        val deviceInfo = castContext?.sessionManager?.currentCastSession?.castDevice
        val modelName = deviceInfo?.modelName ?: ""
        val friendlyName = deviceInfo?.friendlyName ?: ""

        // Detect SHIELD or Android TV to allow Direct Play
        val isShieldOrAndroidTV = modelName.lowercase(Locale.ROOT).contains("shield") ||
            friendlyName.lowercase(Locale.ROOT).contains("shield") ||
            modelName.lowercase(Locale.ROOT).contains("android tv") ||
            modelName.lowercase(Locale.ROOT).contains("chromecast with google tv")

        return try {
            val playbackInfo = repository.getCastPlaybackInfo(itemId, isShieldOrAndroidTV)
            val mediaSource = playbackInfo.mediaSources.firstOrNull() ?: return null
            val serverUrl = authRepository.getCurrentServer()?.url ?: return null
            val token = authRepository.getCurrentServer()?.accessToken

            val streamPath = when {
                !mediaSource.transcodingUrl.isNullOrBlank() -> mediaSource.transcodingUrl!!
                else -> "Videos/$itemId/stream?static=true"
            }

            val fullUrl = "$serverUrl/${streamPath.trimStart('/')}"
            
            // Add auth token to URL
            val authenticatedUrl = if (!token.isNullOrBlank()) {
                val separator = if (fullUrl.contains("?")) "&" else "?"
                "$fullUrl${separator}api_key=$token"
            } else {
                fullUrl
            }

            val mimeType = resolveMimeType(mediaSource.transcodingUrl, mediaSource.container, streamPath)

            PlaybackData(
                url = authenticatedUrl,
                mimeType = mimeType,
                playSessionId = playbackInfo.playSessionId,
                mediaSourceId = mediaSource.id?.toString(),
                urlType = if (!mediaSource.transcodingUrl.isNullOrBlank()) "transcode" else "direct",
            )
        } catch (e: Exception) {
            SecureLogger.e("CastMediaLoadBuilder", "Failed to resolve playback data", e)
            null
        }
    }

    /**
     * Builds the MediaMetadata for the Cast notification and receiver UI.
     */
    fun buildMetadata(item: BaseItemDto): MediaMetadata {
        return MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, item.name ?: AppResources.getString(R.string.unknown))
            putString(MediaMetadata.KEY_SUBTITLE, item.overview ?: "")
            
            // Attach Artwork
            val token = authRepository.getCurrentServer()?.accessToken
            
            val backdropUrl = buildImageUrl(item, ImageType.BACKDROP, token)
            val primaryUrl = buildImageUrl(item, ImageType.PRIMARY, token)

            backdropUrl?.let { addImage(WebImage(it.toUri())) }
            primaryUrl?.let { addImage(WebImage(it.toUri())) }
        }
    }

    /**
     * Converts side-loaded subtitle specs into Cast MediaTracks.
     */
    fun buildTracks(sideLoadedSubs: List<SubtitleSpec>): List<MediaTrack> {
        return sideLoadedSubs.mapIndexed { idx, spec ->
            MediaTrack.Builder(idx + 1L, MediaTrack.TYPE_TEXT)
                .setContentId(spec.url)
                .setContentType(spec.mimeType)
                .setLanguage(spec.language)
                .setName(spec.label ?: spec.language?.uppercase() ?: "Subtitles")
                .setSubtype(resolveTrackSubtype(spec.mimeType))
                .build()
        }
    }

    private fun resolveMimeType(transcodingUrl: String?, container: String?, streamPath: String): String {
        return if (!transcodingUrl.isNullOrBlank()) {
            when {
                streamPath.contains(".m3u8", true) -> "application/x-mpegURL"
                streamPath.contains(".mpd", true) -> "application/dash+xml"
                else -> "application/x-mpegURL"
            }
        } else {
            when (container?.lowercase(Locale.ROOT)) {
                "ts", "m3u8", "hls" -> "application/x-mpegURL"
                "webm" -> "video/webm"
                else -> "video/mp4"
            }
        }
    }

    private fun resolveTrackSubtype(mimeType: String): Int {
        return when (mimeType) {
            androidx.media3.common.MimeTypes.TEXT_VTT,
            androidx.media3.common.MimeTypes.APPLICATION_SUBRIP -> MediaTrack.SUBTYPE_SUBTITLES
            else -> MediaTrack.SUBTYPE_CAPTIONS
        }
    }

    private fun buildImageUrl(item: BaseItemDto, imageType: ImageType, token: String?): String? {
        val itemId = item.id.toString()
        val url = when (imageType) {
            ImageType.BACKDROP -> streamRepository.getBackdropUrl(item)
            else -> {
                if (imageType == ImageType.PRIMARY && item.type == BaseItemKind.EPISODE) {
                    streamRepository.getSeriesImageUrl(item)
                } else {
                    val tag = item.imageTags?.get(imageType) ?: item.imageTags?.get(ImageType.PRIMARY)
                    streamRepository.getImageUrl(itemId, imageType.name, tag)
                }
            }
        } ?: return null

        return if (!token.isNullOrBlank()) {
            val separator = if (url.contains("?")) "&" else "?"
            "$url${separator}api_key=$token"
        } else {
            url
        }
    }
}
