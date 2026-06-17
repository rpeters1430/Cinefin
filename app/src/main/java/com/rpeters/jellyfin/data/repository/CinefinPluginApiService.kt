package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.CinefinPluginEpisodeRequest
import com.rpeters.jellyfin.data.model.CinefinPluginInfoResponse
import com.rpeters.jellyfin.data.model.CinefinPluginMediaRequest
import com.rpeters.jellyfin.data.model.CinefinPluginRequestResponse
import com.rpeters.jellyfin.data.model.CinefinPluginCredentialsResponse
import com.rpeters.jellyfin.data.model.CinefinPluginConfigurationRequest
import com.rpeters.jellyfin.data.model.CinefinPluginTestRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CinefinPluginApiService {
    @GET("Cinefin/Info")
    suspend fun getPluginInfo(): Response<CinefinPluginInfoResponse>

    @GET("Cinefin/Credentials")
    suspend fun getCredentials(): Response<CinefinPluginCredentialsResponse>

    @POST("Cinefin/Request/Media")
    suspend fun requestMedia(
        @Body request: CinefinPluginMediaRequest
    ): Response<CinefinPluginRequestResponse>

    @POST("Cinefin/Request/Episode")
    suspend fun requestEpisode(
        @Body request: CinefinPluginEpisodeRequest
    ): Response<CinefinPluginRequestResponse>

    @POST("Cinefin/TestSonarr")
    suspend fun testSonarr(
        @Body request: CinefinPluginTestRequest
    ): Response<CinefinPluginRequestResponse>

    @POST("Cinefin/TestRadarr")
    suspend fun testRadarr(
        @Body request: CinefinPluginTestRequest
    ): Response<CinefinPluginRequestResponse>

    @POST("Cinefin/TestOverseerr")
    suspend fun testOverseerr(
        @Body request: CinefinPluginTestRequest
    ): Response<CinefinPluginRequestResponse>

    @POST("Cinefin/Configuration")
    suspend fun updateConfiguration(
        @Body request: CinefinPluginConfigurationRequest
    ): Response<CinefinPluginRequestResponse>
}
