package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.CinefinPluginEpisodeRequest
import com.rpeters.jellyfin.data.model.CinefinPluginInfoResponse
import com.rpeters.jellyfin.data.model.CinefinPluginMediaRequest
import com.rpeters.jellyfin.data.model.CinefinPluginRequestResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CinefinPluginApiService {
    @GET("Cinefin/Info")
    suspend fun getPluginInfo(): Response<CinefinPluginInfoResponse>

    @POST("Cinefin/Request/Media")
    suspend fun requestMedia(
        @Body request: CinefinPluginMediaRequest
    ): Response<CinefinPluginRequestResponse>

    @POST("Cinefin/Request/Episode")
    suspend fun requestEpisode(
        @Body request: CinefinPluginEpisodeRequest
    ): Response<CinefinPluginRequestResponse>
}
