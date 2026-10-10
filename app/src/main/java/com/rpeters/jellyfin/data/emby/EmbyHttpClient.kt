package com.rpeters.jellyfin.data.emby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A server answered an Emby request with a non-2xx status. The message carries the status code
 * so callers that inspect error text for "401" keep working.
 */
class EmbyHttpException(val code: Int, statusMessage: String) :
    IOException("HTTP $code ${statusMessage.ifBlank { "error" }}".trim())

/**
 * Sends requests to an Emby server over the app's shared [OkHttpClient], so they get the same
 * certificate pinning, connectivity checks, and client-identity and token headers
 * ([com.rpeters.jellyfin.network.JellyfinAuthInterceptor]) as every other server call.
 *
 * The client is injected lazily because the interceptor chain depends on the auth repository,
 * which in turn signs in to Emby through this class.
 */
@Singleton
class EmbyHttpClient @Inject constructor(
    private val okHttpClient: dagger.Lazy<OkHttpClient>,
) {
    /** @return the raw response body. Throws [EmbyHttpException] on a non-2xx status. */
    suspend fun postJson(serverUrl: String, path: String, body: JsonElement): String =
        execute(
            Request.Builder()
                .url(resolve(serverUrl, path))
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )

    suspend fun postEmpty(serverUrl: String, path: String): String =
        execute(
            Request.Builder()
                .url(resolve(serverUrl, path))
                .post(ByteArray(0).toRequestBody(null))
                .build(),
        )

    suspend fun get(serverUrl: String, path: String): String =
        execute(Request.Builder().url(resolve(serverUrl, path)).get().build())

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        okHttpClient.get().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw EmbyHttpException(response.code, response.message)
            response.body.string()
        }
    }

    private fun resolve(serverUrl: String, path: String): String =
        serverUrl.trimEnd('/') + "/" + path.trimStart('/')

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
