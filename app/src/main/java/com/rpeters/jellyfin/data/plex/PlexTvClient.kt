package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.network.DeviceIdentityProvider
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal interface PlexTvApi {
    @POST("api/v2/pins")
    suspend fun createPin(@Query("strong") strong: Boolean): Response<PlexPin>

    @GET("api/v2/pins/{id}")
    suspend fun getPin(@Path("id") id: Long): Response<PlexPin>

    @GET("api/v2/user")
    suspend fun getUser(@Header(PlexClientHeaders.TOKEN) accountToken: String): Response<PlexTvUser>

    @GET("api/v2/resources?includeHttps=1&includeRelay=1")
    suspend fun getResources(@Header(PlexClientHeaders.TOKEN) accountToken: String): Response<List<PlexResource>>
}

/**
 * plex.tv account calls: PIN sign-in, the signed-in user, and the account's servers.
 *
 * Uses its own OkHttp client, never the app's shared one, so the account token cannot pass
 * through the session interceptor or a body logger, and a Plex server token is never sent here.
 * Redirects are off so a token header cannot follow one to another host.
 */
@Singleton
class PlexTvClient internal constructor(
    identity: DeviceIdentityProvider,
    baseUrl: String,
) {
    @Inject
    constructor(identity: DeviceIdentityProvider) : this(identity, BASE_URL)

    private val api: PlexTvApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = PlexClientHeaders.applyTo(chain.request().newBuilder(), identity)
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PlexTvApi::class.java)

    /**
     * @param strong false gives the 4-character code a person can type at plex.tv/link; true
     *   gives a long code that only works through the app.plex.tv/auth link.
     */
    suspend fun createPin(strong: Boolean = false): ApiResult<PlexPin> = call { api.createPin(strong) }

    /** A [ErrorType.NOT_FOUND] result means the PIN expired or was never created by this client. */
    suspend fun getPin(id: Long): ApiResult<PlexPin> = call { api.getPin(id) }

    suspend fun getUser(accountToken: String): ApiResult<PlexTvUser> = call { api.getUser(accountToken) }

    /** The account's Plex Media Servers, owned and shared. Players and other clients are left out. */
    suspend fun getServers(accountToken: String): ApiResult<List<PlexResource>> =
        when (val result = call { api.getResources(accountToken) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.filter { it.isServer })
            is ApiResult.Error -> ApiResult.Error(result.message, result.cause, result.errorType)
            is ApiResult.Loading -> ApiResult.Loading()
        }

    /** Retrofit throws [SerializationException] for a body it cannot decode. */
    private suspend fun <T> call(request: suspend () -> Response<T>): ApiResult<T> = try {
        callOrNetworkError(request)
    } catch (e: SerializationException) {
        ApiResult.Error("plex.tv sent a response Cinefin could not read", e, ErrorType.SERVER_ERROR)
    }

    private suspend fun <T> callOrNetworkError(request: suspend () -> Response<T>): ApiResult<T> = try {
        val response = request()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Error(errorMessage(response), errorType = errorType(response.code()))
        }
    } catch (e: IOException) {
        ApiResult.Error("Could not reach plex.tv", e, ErrorType.NETWORK)
    }

    private fun errorMessage(response: Response<*>): String {
        val body = runCatching { response.errorBody()?.string() }.getOrNull()
        val plexMessage = body?.let { runCatching { json.decodeFromString<PlexTvErrors>(it) }.getOrNull() }
            ?.errors?.firstOrNull()?.message?.takeIf { it.isNotBlank() }
        return plexMessage ?: "plex.tv returned HTTP ${response.code()}"
    }

    private fun errorType(code: Int): ErrorType = when (code) {
        401 -> ErrorType.UNAUTHORIZED
        403 -> ErrorType.FORBIDDEN
        404 -> ErrorType.NOT_FOUND
        in 500..599 -> ErrorType.SERVER_ERROR
        else -> ErrorType.UNKNOWN
    }

    companion object {
        const val BASE_URL = "https://clients.plex.tv/"
        private const val CALL_TIMEOUT_SECONDS = 15L
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
