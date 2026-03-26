package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.JellyfinServer
import com.rpeters.jellyfin.data.model.QuickConnectResult
import com.rpeters.jellyfin.data.repository.common.ApiResult
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.PublicSystemInfo

interface IJellyfinRepository {
    fun getCurrentServer(): JellyfinServer?
    fun isUserAuthenticated(): Boolean
    fun getDownloadUrl(itemId: String): String?
    suspend fun getPlaybackInfo(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackInfoResponse
    fun getStreamUrl(itemId: String): String?
    suspend fun authenticateUser(serverUrl: String?, username: String?, password: String?): ApiResult<AuthenticationResult>
    
    // Additional methods needed by other components
    suspend fun getTranscodingProgress(deviceId: String, jellyfinItemId: String? = null): TranscodingProgressInfo?
    fun getSeriesImageUrl(item: BaseItemDto): String?
    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String?
}
