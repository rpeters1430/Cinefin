package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.SeerrMovieDetails
import com.rpeters.jellyfin.data.model.SeerrRequestRequest
import com.rpeters.jellyfin.data.model.SeerrRequestResponse
import com.rpeters.jellyfin.data.model.SeerrRequestsResponse
import com.rpeters.jellyfin.data.model.SeerrSeason
import com.rpeters.jellyfin.data.model.SeerrSearchResult
import com.rpeters.jellyfin.data.model.SeerrTvDetails
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.utils.SecureLogger
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

internal data class SeerrConnection(val service: SeerrApiService, val apiKey: String)

@Singleton
open class SeerrRepository @Inject constructor(
    @Named("SeerrHttpClient") private val okHttpClient: OkHttpClient,
    private val preferencesRepository: SeerrPreferencesRepository,
    private val json: Json
) {
    private var cachedApiService: SeerrApiService? = null
    private var cachedBaseUrl: String? = null
    private val cacheMutex = Mutex()

    @VisibleForTesting
    internal open suspend fun getConnection(): SeerrConnection? = cacheMutex.withLock {
        val prefs = preferencesRepository.seerrPreferencesFlow.first()
        if (!prefs.isValid) return@withLock null

        val baseUrl = if (prefs.baseUrl.endsWith("/")) prefs.baseUrl else "${prefs.baseUrl}/"

        val service = if (cachedApiService != null && cachedBaseUrl == baseUrl) {
            cachedApiService!!
        } else {
            try {
                val newService = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(SeerrApiService::class.java)
                cachedApiService = newService
                cachedBaseUrl = baseUrl
                newService
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to create SeerrApiService with URL: $baseUrl", e)
                return@withLock null
            }
        }

        SeerrConnection(service, prefs.apiKey)
    }

    private suspend fun <T> retryNetworkCall(
        times: Int = 3,
        initialDelay: Long = 500,
        factor: Double = 2.0,
        block: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        var currentDelay = initialDelay
        for (i in 1..times) {
            when (val result = block()) {
                is ApiResult.Success -> return result
                is ApiResult.Error -> {
                    if (result.errorType == ErrorType.NETWORK && i < times) {
                        kotlinx.coroutines.delay(currentDelay)
                        currentDelay = (currentDelay * factor).toLong()
                    } else {
                        return result
                    }
                }
                else -> return result
            }
        }
        return ApiResult.Error("Max retries reached", errorType = ErrorType.NETWORK)
    }

    private fun <T> notConfiguredError(): ApiResult<T> =
        ApiResult.Error("Seerr is not configured or URL is invalid", errorType = ErrorType.BAD_REQUEST)

    suspend fun testConnection(): ApiResult<SeerrSearchResult> {
        val (service, apiKey) = getConnection() ?: return notConfiguredError()
        return try {
            handleResponse(service.getTrending(apiKey, page = 1))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Connection test failed", e)
            ApiResult.Error("Could not reach Seerr: ${e.message}", e, ErrorType.NETWORK)
        }
    }

    suspend fun search(query: String, page: Int = 1): ApiResult<SeerrSearchResult> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            handleResponse(service.search(apiKey, query, page))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Search failed for query: $query", e)
            ApiResult.Error("Network error during Seerr search", e, ErrorType.NETWORK)
        }
    }

    suspend fun getTrending(page: Int = 1): ApiResult<SeerrSearchResult> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            handleResponse(service.getTrending(apiKey, page))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Trending fetch failed", e)
            ApiResult.Error("Network error during Seerr trending fetch", e, ErrorType.NETWORK)
        }
    }

    suspend fun request(request: SeerrRequestRequest): ApiResult<SeerrRequestResponse> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            handleResponse(service.request(apiKey, request))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Request failed for mediaId: ${request.mediaId}", e)
            ApiResult.Error("Network error during Seerr request", e, ErrorType.NETWORK)
        }
    }

    suspend fun getTvDetails(tvId: Int): ApiResult<SeerrTvDetails> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            handleResponse(service.getTvDetails(apiKey, tvId))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "TV details lookup failed for tvId: $tvId", e)
            ApiResult.Error("Network error loading Seerr TV details", e, ErrorType.NETWORK)
        }
    }

    suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int): ApiResult<SeerrSeason> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            handleResponse(service.getTvSeasonDetails(apiKey, tvId, seasonNumber))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "TV season lookup failed for tvId: $tvId season: $seasonNumber", e)
            ApiResult.Error("Network error loading Seerr season details", e, ErrorType.NETWORK)
        }
    }

    suspend fun getMovieDetails(movieId: Int): ApiResult<SeerrMovieDetails> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            handleResponse(service.getMovieDetails(apiKey, movieId))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Movie details lookup failed for movieId: $movieId", e)
            ApiResult.Error("Network error loading Seerr movie details", e, ErrorType.NETWORK)
        }
    }

    suspend fun getRequests(filter: String = "all", page: Int = 1, take: Int = 20): ApiResult<SeerrRequestsResponse> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            handleResponse(service.getRequests(apiKey, take = take, skip = (page - 1) * take, filter = filter))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Get requests failed for filter: $filter", e)
            ApiResult.Error("Network error loading Seerr requests", e, ErrorType.NETWORK)
        }
    }

    suspend fun deleteRequest(requestId: Int): ApiResult<Unit> = retryNetworkCall {
        val (service, apiKey) = getConnection() ?: return@retryNetworkCall notConfiguredError()
        try {
            val response = service.deleteRequest(apiKey, requestId)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(
                    message = readErrorMessage(response) ?: "Failed to cancel request: HTTP ${response.code()}",
                    errorType = errorTypeForCode(response.code()),
                )
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Delete request failed for requestId: $requestId", e)
            ApiResult.Error("Network error canceling Seerr request", e, ErrorType.NETWORK)
        }
    }

    private fun <T> handleResponse(response: Response<T>): ApiResult<T> {
        return if (response.code() == 202) {
            ApiResult.Error("No seasons available to request", errorType = ErrorType.VALIDATION)
        } else if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error("Empty response body from Seerr", errorType = ErrorType.SERVER_ERROR)
            }
        } else {
            ApiResult.Error(
                message = readErrorMessage(response) ?: "Seerr API error: ${response.code()}",
                errorType = errorTypeForCode(response.code())
            )
        }
    }

    private fun errorTypeForCode(code: Int): ErrorType = when (code) {
        400 -> ErrorType.BAD_REQUEST
        401 -> ErrorType.UNAUTHORIZED
        403 -> ErrorType.FORBIDDEN
        404 -> ErrorType.NOT_FOUND
        409 -> ErrorType.VALIDATION
        in 500..599 -> ErrorType.SERVER_ERROR
        else -> ErrorType.UNKNOWN
    }

    private fun <T> readErrorMessage(response: Response<T>): String? {
        val rawBody = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val body = json.parseToJsonElement(rawBody)
            (body as? JsonObject)?.get("message")?.jsonPrimitive?.content
        }.getOrNull() ?: rawBody
    }

    companion object {
        private const val TAG = "SeerrRepository"
    }
}
