package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.network.JellyfinAuthInterceptor
import com.rpeters.jellyfin.utils.ServerUrlValidator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.model.api.AuthenticationResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** These credentials remain in memory until a server is selected. Never log their contents. */
data class EmbyConnectServer(
    val name: String,
    val systemId: String,
    val addresses: List<String>,
    val userId: String,
    val accessKey: String,
) {
    override fun toString(): String = "EmbyConnectServer(credentials=redacted)"
}

@Singleton
class EmbyConnectClient @Inject constructor(
    private val sharedClient: dagger.Lazy<OkHttpClient>,
    private val identity: com.rpeters.jellyfin.network.DeviceIdentityProvider,
) {
    // Cloud account credentials must never pass through the server auth interceptor or body logger.
    private val cloudClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(15, TimeUnit.SECONDS).build()
    private val serverClient by lazy {
        sharedClient.get().newBuilder().apply {
            interceptors().removeAll { it is JellyfinAuthInterceptor || it is HttpLoggingInterceptor }
            networkInterceptors().removeAll { it is JellyfinAuthInterceptor || it is HttpLoggingInterceptor }
            authenticator(okhttp3.Authenticator.NONE)
            addInterceptor { chain ->
                val authorization = "MediaBrowser Client=\"${identity.clientName()}\", " +
                    "Device=\"${identity.deviceName()}\", DeviceId=\"${identity.deviceId()}\", " +
                    "Version=\"${identity.clientVersion()}\""
                chain.proceed(chain.request().newBuilder().header("X-Emby-Authorization", authorization).build())
            }
            followRedirects(false)
            followSslRedirects(false)
            cache(null)
            callTimeout(10, TimeUnit.SECONDS)
        }.build()
    }

    suspend fun signIn(name: String, password: String): List<EmbyConnectServer> {
        val body = buildJsonObject { put("nameOrEmail", name); put("rawpw", password) }
        val account = execute(cloudClient, cloudRequest("user/authenticate")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()).jsonObject
        val userId = account.requiredString("ConnectUserId")
        val token = account.requiredString("ConnectAccessToken")
        val url = "$CONNECT_URL/servers".toHttpUrl().newBuilder().addQueryParameter("userId", userId).build()
        val servers = execute(cloudClient, Request.Builder().url(url)
            .header("X-Application", "Cinefin/${BuildConfig.VERSION_NAME}")
            .header("X-Connect-UserToken", token).build()).jsonArray
        return parseServers(servers, userId)
    }

    suspend fun exchange(serverUrl: String, userId: String, accessKey: String): AuthenticationResult {
        val url = (serverUrl.trimEnd('/') + "/Connect/Exchange").toHttpUrl().newBuilder()
            .addQueryParameter("format", "json").addQueryParameter("ConnectUserId", userId).build()
        val exchange = execute(serverClient, Request.Builder().url(url)
            .header("X-Emby-Token", accessKey).build()).jsonObject
        val localId = exchange.requiredString("LocalUserId")
        val token = exchange.requiredString("AccessToken")
        require(token.isNotBlank()) { "Emby returned an empty access token" }
        val userUrl = serverUrl.trimEnd('/') + "/Users/" + localId
        val user = execute(serverClient, Request.Builder().url(userUrl).header("X-Emby-Token", token).build())
        return EmbyJsonNormalizer.decode(AuthenticationResult.serializer(), buildJsonObject {
            put("AccessToken", token)
            put("User", user)
        })
    }

    private fun cloudRequest(path: String) = Request.Builder().url("$CONNECT_URL/$path")
        .header("X-Application", "Cinefin/${BuildConfig.VERSION_NAME}")

    private fun JsonObject.requiredString(key: String): String =
        (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw kotlinx.serialization.SerializationException("Missing Emby credential field")

    private suspend fun execute(client: OkHttpClient, request: Request): JsonElement = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val result = response.use {
                    runCatching {
                        if (!it.isSuccessful) throw EmbyHttpException(it.code, "Emby sign-in failed")
                        Json.parseToJsonElement(it.body.string())
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }

    companion object {
        private const val CONNECT_URL = "https://connect.emby.media/service"
        internal fun parseServers(servers: JsonArray, userId: String): List<EmbyConnectServer> = servers.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            fun field(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val id = field("SystemId")
            val key = field("AccessKey")
            val addresses = listOf(field("LocalAddress"), field("Url"))
                .mapNotNull(ServerUrlValidator::validateAndNormalizeUrl).distinct()
            if (id.isBlank() || key.isBlank() || addresses.isEmpty()) null
            else EmbyConnectServer(field("Name").ifBlank { "Emby Server" }, id, addresses, userId, key)
        }
    }
}
