package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.SeerrMovieDetails
import com.rpeters.jellyfin.data.model.SeerrRequestRequest
import com.rpeters.jellyfin.data.model.SeerrRequestResponse
import com.rpeters.jellyfin.data.model.SeerrRequestsResponse
import com.rpeters.jellyfin.data.model.SeerrSearchResult
import com.rpeters.jellyfin.data.model.SeerrTvDetails
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SeerrApiService {

    @GET("api/v1/search")
    suspend fun search(
        @Header("X-Api-Key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<SeerrSearchResult>

    @GET("api/v1/discover/trending")
    suspend fun getTrending(
        @Header("X-Api-Key") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<SeerrSearchResult>

    @GET("api/v1/discover/movies/upcoming")
    suspend fun getUpcomingMovies(
        @Header("X-Api-Key") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<SeerrSearchResult>

    @GET("api/v1/discover/tv/upcoming")
    suspend fun getUpcomingTv(
        @Header("X-Api-Key") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<SeerrSearchResult>

    @GET("api/v1/discover/movies")
    suspend fun getPopularMovies(
        @Header("X-Api-Key") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<SeerrSearchResult>

    @GET("api/v1/discover/tv")
    suspend fun getPopularTv(
        @Header("X-Api-Key") apiKey: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<SeerrSearchResult>

    @POST("api/v1/request")
    suspend fun request(
        @Header("X-Api-Key") apiKey: String,
        @Body request: SeerrRequestRequest
    ): Response<SeerrRequestResponse>

    @GET("api/v1/tv/{tvId}")
    suspend fun getTvDetails(
        @Header("X-Api-Key") apiKey: String,
        @Path("tvId") tvId: Int
    ): Response<SeerrTvDetails>

    @GET("api/v1/tv/{tvId}/season/{seasonNumber}")
    suspend fun getTvSeasonDetails(
        @Header("X-Api-Key") apiKey: String,
        @Path("tvId") tvId: Int,
        @Path("seasonNumber") seasonNumber: Int
    ): Response<com.rpeters.jellyfin.data.model.SeerrSeason>

    @GET("api/v1/movie/{movieId}")
    suspend fun getMovieDetails(
        @Header("X-Api-Key") apiKey: String,
        @Path("movieId") movieId: Int
    ): Response<SeerrMovieDetails>

    @GET("api/v1/request")
    suspend fun getRequests(
        @Header("X-Api-Key") apiKey: String,
        @Query("take") take: Int = 20,
        @Query("skip") skip: Int = 0,
        @Query("filter") filter: String = "all",
        @Query("sort") sort: String = "added"
    ): Response<SeerrRequestsResponse>

    @DELETE("api/v1/request/{requestId}")
    suspend fun deleteRequest(
        @Header("X-Api-Key") apiKey: String,
        @Path("requestId") requestId: Int
    ): Response<Unit>
}
