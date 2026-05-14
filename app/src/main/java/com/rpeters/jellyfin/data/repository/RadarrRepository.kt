package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.RadarrAddMovieRequest
import com.rpeters.jellyfin.data.model.RadarrAddOptions
import com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class RadarrRepository @Inject constructor(
    @Named("ArrHttpClient") private val okHttpClient: OkHttpClient,
    private val preferencesRepository: ArrPreferencesRepository,
    private val json: Json,
) {
    private var cachedService: RadarrApiService? = null
    private var cachedBaseUrl: String? = null
    private val mutex = Mutex()

    private suspend fun getService(): Pair<RadarrApiService, String>? = mutex.withLock {
        val prefs = preferencesRepository.radarrPreferencesFlow.first()
        if (!prefs.isValid) return@withLock null
        val baseUrl = prefs.baseUrl.trimEnd('/') + "/"
        val service = if (cachedService != null && cachedBaseUrl == baseUrl) {
            cachedService!!
        } else {
            try {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(RadarrApiService::class.java)
                    .also { cachedService = it; cachedBaseUrl = baseUrl }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to create RadarrApiService", e)
                return@withLock null
            }
        }
        service to prefs.apiKey
    }

    suspend fun testConnection(): ApiResult<Unit> = retryNetworkCall {
        val (service, apiKey) = getService() ?: return@retryNetworkCall notConfiguredError()
        try {
            val response = service.getSystemStatus(apiKey)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("HTTP ${response.code()}", errorType = errorType(response.code()))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Radarr test connection failed", e)
            ApiResult.Error(e.message ?: "Connection failed", e, ErrorType.NETWORK)
        }
    }

    suspend fun addMovie(tmdbId: Int): ApiResult<Unit> = retryNetworkCall {
        val (service, apiKey) = getService() ?: return@retryNetworkCall notConfiguredError()
        try {
            val lookupResponse = service.lookupMovieByTmdb(apiKey, "tmdb:$tmdbId")
            if (!lookupResponse.isSuccessful || lookupResponse.body().isNullOrEmpty())
                return@retryNetworkCall ApiResult.Error("Movie TMDB:$tmdbId not found in Radarr", errorType = ErrorType.NOT_FOUND)

            val movie = lookupResponse.body()!![0]
            val rootFolderPath = service.getRootFolders(apiKey).body()?.firstOrNull()?.path ?: "/movies"
            val qualityProfileId = service.getQualityProfiles(apiKey).body()?.firstOrNull()?.id ?: 1

            val addRequest = RadarrAddMovieRequest(
                title = movie.title,
                titleSlug = movie.titleSlug,
                tmdbId = movie.tmdbId,
                year = movie.year,
                images = movie.images,
                rootFolderPath = rootFolderPath,
                qualityProfileId = qualityProfileId,
                monitored = true,
                addOptions = RadarrAddOptions(searchForMovie = true),
            )

            val addResponse = service.addMovie(apiKey, addRequest)
            if (addResponse.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error("Radarr rejected add: HTTP ${addResponse.code()}", errorType = errorType(addResponse.code()))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Radarr addMovie failed", e)
            ApiResult.Error(e.message ?: "Failed to add movie", e, ErrorType.NETWORK)
        }
    }

    private suspend fun <T> retryNetworkCall(
        times: Int = 3,
        initialDelay: Long = 500,
        block: suspend () -> ApiResult<T>,
    ): ApiResult<T> {
        var currentDelay = initialDelay
        for (i in 1..times) {
            val result = block()
            if (result !is ApiResult.Error || result.errorType != ErrorType.NETWORK || i == times) return result
            delay(currentDelay)
            currentDelay *= 2
        }
        return ApiResult.Error("Max retries reached", errorType = ErrorType.NETWORK)
    }

    private fun <T> notConfiguredError(): ApiResult<T> =
        ApiResult.Error("Radarr is not configured", errorType = ErrorType.BAD_REQUEST)

    private fun errorType(code: Int) = when (code) {
        401, 403 -> ErrorType.UNAUTHORIZED
        404 -> ErrorType.NOT_FOUND
        in 500..599 -> ErrorType.SERVER_ERROR
        else -> ErrorType.UNKNOWN
    }

    companion object {
        private const val TAG = "RadarrRepository"
    }
}
