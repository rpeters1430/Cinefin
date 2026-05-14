package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.SonarrAddSeriesRequest
import com.rpeters.jellyfin.data.model.SonarrCommand
import com.rpeters.jellyfin.data.model.SonarrEpisodeItem
import com.rpeters.jellyfin.data.model.SonarrQualityProfile
import com.rpeters.jellyfin.data.model.SonarrRootFolder
import com.rpeters.jellyfin.data.model.SonarrSeriesLookupResult
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface SonarrApiService {
    @GET("api/v3/system/status")
    suspend fun getSystemStatus(@Header("X-Api-Key") apiKey: String): Response<JsonObject>

    @GET("api/v3/series/lookup")
    suspend fun lookupSeriesByTvdb(
        @Header("X-Api-Key") apiKey: String,
        @Query("term") term: String,
    ): Response<List<SonarrSeriesLookupResult>>

    @GET("api/v3/series")
    suspend fun getSeriesByTvdbId(
        @Header("X-Api-Key") apiKey: String,
        @Query("tvdbId") tvdbId: Int,
    ): Response<List<SonarrSeriesLookupResult>>

    @GET("api/v3/rootFolder")
    suspend fun getRootFolders(@Header("X-Api-Key") apiKey: String): Response<List<SonarrRootFolder>>

    @GET("api/v3/qualityProfile")
    suspend fun getQualityProfiles(@Header("X-Api-Key") apiKey: String): Response<List<SonarrQualityProfile>>

    @GET("api/v3/episode")
    suspend fun getEpisodes(
        @Header("X-Api-Key") apiKey: String,
        @Query("seriesId") seriesId: Int,
        @Query("seasonNumber") seasonNumber: Int,
    ): Response<List<SonarrEpisodeItem>>

    @POST("api/v3/series")
    suspend fun addSeries(
        @Header("X-Api-Key") apiKey: String,
        @Body request: SonarrAddSeriesRequest,
    ): Response<JsonObject>

    @POST("api/v3/command")
    suspend fun sendCommand(
        @Header("X-Api-Key") apiKey: String,
        @Body command: SonarrCommand,
    ): Response<JsonObject>
}
