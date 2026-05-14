package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.SonarrAddOptions
import com.rpeters.jellyfin.data.model.SonarrAddSeriesRequest
import com.rpeters.jellyfin.data.model.SonarrCommand
import com.rpeters.jellyfin.data.model.SonarrSeasonItem
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
class SonarrRepository @Inject constructor(
    @Named("ArrHttpClient") private val okHttpClient: OkHttpClient,
    private val preferencesRepository: ArrPreferencesRepository,
    private val json: Json,
) {
    private var cachedService: SonarrApiService? = null
    private var cachedBaseUrl: String? = null
    private val mutex = Mutex()

    private suspend fun getService(): Pair<SonarrApiService, String>? = mutex.withLock {
        val prefs = preferencesRepository.sonarrPreferencesFlow.first()
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
                    .create(SonarrApiService::class.java)
                    .also {
                        cachedService = it
                        cachedBaseUrl = baseUrl
                    }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to create SonarrApiService", e)
                return@withLock null
            }
        }
        service to prefs.apiKey
    }

    suspend fun testConnection(): ApiResult<Unit> = retryNetworkCall {
        val (service, apiKey) = getService() ?: return@retryNetworkCall notConfiguredError()
        try {
            val response = service.getSystemStatus(apiKey)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("HTTP ${response.code()}", errorType = errorType(response.code()))
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Sonarr test connection failed", e)
            ApiResult.Error(e.message ?: "Connection failed", e, ErrorType.NETWORK)
        }
    }

    suspend fun addSeries(tvdbId: Int, requestedSeasons: List<Int>?): ApiResult<Unit> = retryNetworkCall {
        val (service, apiKey) = getService() ?: return@retryNetworkCall notConfiguredError()
        try {
            val lookupResponse = service.lookupSeriesByTvdb(apiKey, "tvdb:$tvdbId")
            if (!lookupResponse.isSuccessful || lookupResponse.body().isNullOrEmpty()) {
                return@retryNetworkCall ApiResult.Error("Series TVDB:$tvdbId not found in Sonarr", errorType = ErrorType.NOT_FOUND)
            }

            val series = lookupResponse.body()!![0]
            val rootFolderPath = series.rootFolderPath?.takeIf { it.isNotBlank() }
                ?: service.getRootFolders(apiKey).body()?.firstOrNull()?.path
                ?: "/tv"
            val qualityProfileId = series.qualityProfileId.takeIf { it != 0 }
                ?: service.getQualityProfiles(apiKey).body()?.firstOrNull()?.id
                ?: 1

            val seasons = series.seasons.map { s ->
                SonarrSeasonItem(
                    seasonNumber = s.seasonNumber,
                    monitored = if (requestedSeasons != null) {
                        s.seasonNumber in requestedSeasons && s.seasonNumber > 0
                    } else {
                        s.seasonNumber > 0
                    },
                )
            }

            val addRequest = SonarrAddSeriesRequest(
                title = series.title,
                titleSlug = series.titleSlug,
                tvdbId = series.tvdbId,
                seriesType = series.seriesType.ifBlank { "standard" },
                seasons = seasons,
                rootFolderPath = rootFolderPath,
                qualityProfileId = qualityProfileId,
                monitored = true,
                addOptions = SonarrAddOptions(searchForMissingEpisodes = true),
                images = series.images,
            )

            val addResponse = service.addSeries(apiKey, addRequest)
            if (addResponse.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("Sonarr rejected add: HTTP ${addResponse.code()}", errorType = errorType(addResponse.code()))
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Sonarr addSeries failed", e)
            ApiResult.Error(e.message ?: "Failed to add series", e, ErrorType.NETWORK)
        }
    }

    suspend fun requestEpisode(tvdbId: Int, seasonNumber: Int, episodeNumber: Int): ApiResult<Unit> = retryNetworkCall {
        val (service, apiKey) = getService() ?: return@retryNetworkCall notConfiguredError()
        try {
            val seriesResponse = service.getSeriesByTvdbId(apiKey, tvdbId)

            // If the series isn't in Sonarr yet, auto-add it with just the requested season
            // monitored so Sonarr knows about it before we trigger the episode search.
            val seriesId = if (!seriesResponse.isSuccessful || seriesResponse.body().isNullOrEmpty()) {
                val addResult = addSeries(tvdbId, listOf(seasonNumber))
                if (addResult is ApiResult.Error) return@retryNetworkCall addResult

                val refetch = service.getSeriesByTvdbId(apiKey, tvdbId)
                if (!refetch.isSuccessful || refetch.body().isNullOrEmpty()) {
                    return@retryNetworkCall ApiResult.Error(
                        "Could not find TVDB:$tvdbId in Sonarr after adding it",
                        errorType = ErrorType.NOT_FOUND,
                    )
                }
                refetch.body()!![0].id
            } else {
                seriesResponse.body()!![0].id
            }
            val episodesResponse = service.getEpisodes(apiKey, seriesId, seasonNumber)
            val episode = episodesResponse.body()?.firstOrNull { it.episodeNumber == episodeNumber }
                ?: return@retryNetworkCall ApiResult.Error(
                    "S${seasonNumber.toString().padStart(2,'0')}E${episodeNumber.toString().padStart(2,'0')} not found in Sonarr",
                    errorType = ErrorType.NOT_FOUND,
                )

            val cmdResponse = service.sendCommand(apiKey, SonarrCommand(name = "EpisodeSearch", episodeIds = listOf(episode.id)))
            if (cmdResponse.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("Sonarr command failed: HTTP ${cmdResponse.code()}", errorType = errorType(cmdResponse.code()))
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Sonarr requestEpisode failed", e)
            ApiResult.Error(e.message ?: "Failed to request episode", e, ErrorType.NETWORK)
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
        ApiResult.Error("Sonarr is not configured", errorType = ErrorType.BAD_REQUEST)

    private fun errorType(code: Int) = when (code) {
        401, 403 -> ErrorType.UNAUTHORIZED
        404 -> ErrorType.NOT_FOUND
        in 500..599 -> ErrorType.SERVER_ERROR
        else -> ErrorType.UNKNOWN
    }

    companion object {
        private const val TAG = "SonarrRepository"
    }
}
