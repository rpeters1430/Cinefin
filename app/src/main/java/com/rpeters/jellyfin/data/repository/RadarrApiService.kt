package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.RadarrAddMovieRequest
import com.rpeters.jellyfin.data.model.RadarrQualityProfile
import com.rpeters.jellyfin.data.model.RadarrRootFolder
import com.rpeters.jellyfin.data.model.RadarrMovieLookupResult
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface RadarrApiService {
    @GET("api/v3/system/status")
    suspend fun getSystemStatus(@Header("X-Api-Key") apiKey: String): Response<JsonObject>

    @GET("api/v3/movie/lookup")
    suspend fun lookupMovieByTmdb(
        @Header("X-Api-Key") apiKey: String,
        @Query("term") term: String,
    ): Response<List<RadarrMovieLookupResult>>

    @GET("api/v3/rootFolder")
    suspend fun getRootFolders(@Header("X-Api-Key") apiKey: String): Response<List<RadarrRootFolder>>

    @GET("api/v3/qualityProfile")
    suspend fun getQualityProfiles(@Header("X-Api-Key") apiKey: String): Response<List<RadarrQualityProfile>>

    @POST("api/v3/movie")
    suspend fun addMovie(
        @Header("X-Api-Key") apiKey: String,
        @Body request: RadarrAddMovieRequest,
    ): Response<JsonObject>
}
