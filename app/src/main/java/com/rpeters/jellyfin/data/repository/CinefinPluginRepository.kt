package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.data.model.CinefinPluginEpisodeRequest
import com.rpeters.jellyfin.data.model.CinefinPluginInfoResponse
import com.rpeters.jellyfin.data.model.CinefinPluginMediaRequest
import com.rpeters.jellyfin.data.model.CinefinPluginRequestResponse
import com.rpeters.jellyfin.data.model.CinefinPluginCredentialsResponse
import com.rpeters.jellyfin.data.model.CinefinPluginTestRequest
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.utils.SecureLogger
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
import javax.inject.Singleton

@Singleton
class CinefinPluginRepository(
    private val okHttpClient: OkHttpClient, // Provided by NetworkModule (already has auth interceptor)
    private val authRepository: IJellyfinAuthRepository,
    private val json: Json,
) {
    private var cachedApiService: CinefinPluginApiService? = null
    private var cachedBaseUrl: String? = null
    private val cacheMutex = Mutex()

    private suspend fun getApiService(): CinefinPluginApiService? = cacheMutex.withLock {
        val server = authRepository.currentServer.value ?: return@withLock null
        var baseUrl = server.url
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }

        if (cachedApiService != null && cachedBaseUrl == baseUrl) {
            return@withLock cachedApiService
        }

        try {
            val service = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(CinefinPluginApiService::class.java)
            cachedApiService = service
            cachedBaseUrl = baseUrl
            service
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to create CinefinPluginApiService with URL: $baseUrl", e)
            null
        }
    }

    suspend fun getPluginInfo(): ApiResult<CinefinPluginInfoResponse> {
        val service = getApiService() ?: return notConfiguredError()
        return retryNetworkCall {
            try {
                handleResponse(service.getPluginInfo())
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to get plugin info", e)
                ApiResult.Error("Network error checking plugin info", e, ErrorType.NETWORK)
            }
        }
    }

    suspend fun getCredentials(): ApiResult<CinefinPluginCredentialsResponse> {
        val service = getApiService() ?: return notConfiguredError()
        return retryNetworkCall {
            try {
                handleResponse(service.getCredentials())
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to get plugin credentials", e)
                ApiResult.Error("Network error syncing plugin credentials", e, ErrorType.NETWORK)
            }
        }
    }

    suspend fun requestMedia(
        externalId: String,
        mediaType: String,
        seasons: List<Int>? = null,
    ): ApiResult<CinefinPluginRequestResponse> {
        val service = getApiService() ?: return notConfiguredError()
        val request = CinefinPluginMediaRequest(externalId, mediaType, seasons)
        return retryNetworkCall {
            try {
                handleResponse(service.requestMedia(request))
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to request media: $externalId", e)
                ApiResult.Error("Network error requesting media", e, ErrorType.NETWORK)
            }
        }
    }

    suspend fun requestEpisode(seriesId: String, seasonNumber: Int, episodeNumber: Int): ApiResult<CinefinPluginRequestResponse> {
        val service = getApiService() ?: return notConfiguredError()
        val request = CinefinPluginEpisodeRequest(seriesId, seasonNumber, episodeNumber)
        return retryNetworkCall {
            try {
                handleResponse(service.requestEpisode(request))
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to request episode: S${seasonNumber}E$episodeNumber for series: $seriesId", e)
                ApiResult.Error("Network error requesting episode", e, ErrorType.NETWORK)
            }
        }
    }

    suspend fun testSonarr(url: String, apiKey: String): ApiResult<CinefinPluginRequestResponse> {
        val service = getApiService() ?: return notConfiguredError()
        return try {
            handleResponse(service.testSonarr(CinefinPluginTestRequest(url, apiKey)))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to test Sonarr connection", e)
            ApiResult.Error("Network error testing Sonarr: ${e.message}", e, ErrorType.NETWORK)
        }
    }

    suspend fun testRadarr(url: String, apiKey: String): ApiResult<CinefinPluginRequestResponse> {
        val service = getApiService() ?: return notConfiguredError()
        return try {
            handleResponse(service.testRadarr(CinefinPluginTestRequest(url, apiKey)))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to test Radarr connection", e)
            ApiResult.Error("Network error testing Radarr: ${e.message}", e, ErrorType.NETWORK)
        }
    }

    suspend fun testOverseerr(url: String, apiKey: String): ApiResult<CinefinPluginRequestResponse> {
        val service = getApiService() ?: return notConfiguredError()
        return try {
            handleResponse(service.testOverseerr(CinefinPluginTestRequest(url, apiKey)))
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to test Overseerr connection", e)
            ApiResult.Error("Network error testing Overseerr: ${e.message}", e, ErrorType.NETWORK)
        }
    }

    private suspend fun <T> retryNetworkCall(
        times: Int = 3,
        initialDelay: Long = 500,
        factor: Double = 2.0,
        block: suspend () -> ApiResult<T>,
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
        ApiResult.Error("Jellyfin server is not configured or unavailable", errorType = ErrorType.UNAUTHORIZED)

    private fun <T> handleResponse(response: Response<T>): ApiResult<T> {
        return if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error("Empty response body from Plugin", errorType = ErrorType.SERVER_ERROR)
            }
        } else {
            val errorType = when (response.code()) {
                400 -> ErrorType.BAD_REQUEST
                401 -> ErrorType.UNAUTHORIZED
                403 -> ErrorType.FORBIDDEN
                404 -> ErrorType.NOT_FOUND
                409 -> ErrorType.VALIDATION
                in 500..599 -> ErrorType.SERVER_ERROR
                else -> ErrorType.UNKNOWN
            }
            ApiResult.Error(
                message = readErrorMessage(response) ?: "Plugin API error: ${response.code()}",
                errorType = errorType,
            )
        }
    }

    private fun <T> readErrorMessage(response: Response<T>): String? {
        val rawBody = response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val body = json.parseToJsonElement(rawBody)
            (body as? JsonObject)?.get("message")?.jsonPrimitive?.content
        }.getOrNull() ?: rawBody
    }

    companion object {
        private const val TAG = "CinefinPluginRepository"
    }
}
