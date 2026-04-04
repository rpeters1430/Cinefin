package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.ServerInfo
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.network.ConnectivityChecker
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PlaybackInfoResponse

data class TranscodingProgressInfo(
    val completionPercentage: Double,
    val bitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

interface IJellyfinRepository {
    val currentServerFlow: Flow<JellyfinServer?>
    val isConnectedFlow: Flow<Boolean>
    val connectivityChecker: ConnectivityChecker

    fun getCurrentServerSync(): JellyfinServer?
    fun isUserAuthenticatedSync(): Boolean
    fun getDownloadUrl(itemId: String): String?
    suspend fun getPlaybackInfo(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackInfoResponse
    fun getStreamUrl(itemId: String): String?
    fun getTranscodedStreamUrl(
        itemId: String,
        maxBitrate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        videoCodec: String = "h264",
        audioCodec: String = "aac",
        container: String = "mp4",
        audioBitrate: Int? = null,
        audioChannels: Int = 2,
        allowVideoStreamCopy: Boolean = true,
    ): String?
    suspend fun authenticateUser(serverUrl: String?, username: String?, password: String?): ApiResult<AuthenticationResult>

    suspend fun getServerInfo(): ApiResult<ServerInfo>
    suspend fun getTranscodingProgress(deviceId: String, jellyfinItemId: String? = null): TranscodingProgressInfo?
    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        fields: List<ItemFields>? = null,
    ): ApiResult<List<BaseItemDto>>
    suspend fun getItemsByPerson(
        personId: String,
        includeTypes: List<BaseItemKind>? = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        limit: Int = 100,
    ): ApiResult<List<BaseItemDto>>
    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>>
    suspend fun getSeriesDetails(seriesId: String): ApiResult<BaseItemDto>
    suspend fun getMovieDetails(movieId: String): ApiResult<BaseItemDto>
    suspend fun getEpisodeDetails(episodeId: String): ApiResult<BaseItemDto>
    suspend fun getItemDetails(itemId: String): ApiResult<BaseItemDto>
    suspend fun getRecentlyAdded(limit: Int = 10): ApiResult<List<BaseItemDto>>
    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = 10): ApiResult<List<BaseItemDto>>
    suspend fun getFavorites(): ApiResult<List<BaseItemDto>>
    suspend fun validateAndRefreshTokenManually()
    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean>
    suspend fun searchItems(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        limit: Int = 50,
    ): ApiResult<List<BaseItemDto>>
    fun getSeriesImageUrl(item: BaseItemDto): String?
    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String?
}
