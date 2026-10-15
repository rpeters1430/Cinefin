package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.network.JellyfinAuthInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What a Plex Media Server says about itself at `/identity`, which needs no sign-in. */
data class PlexIdentity(
    val machineIdentifier: String,
    val version: String?,
)

/**
 * Recognises a Plex Media Server. Plex does not serve `/System/Info/Public`, so this is tried
 * when the Jellyfin/Emby probe finds nothing.
 */
@Singleton
class PlexIdentityProbe @Inject constructor(
    private val okHttpClient: dagger.Lazy<OkHttpClient>,
) {
    // The shared client's session interceptor would add the signed-in server's token to every
    // request, including probes of addresses that are not that server. `/identity` needs no auth.
    private val probeClient: OkHttpClient by lazy {
        okHttpClient.get().newBuilder().apply {
            interceptors().removeAll { it is JellyfinAuthInterceptor || it is HttpLoggingInterceptor }
            networkInterceptors().removeAll { it is JellyfinAuthInterceptor || it is HttpLoggingInterceptor }
            authenticator(Authenticator.NONE)
            callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.build()
    }

    /** @return the server's identity, or null when [serverUrl] is not a reachable Plex server. */
    suspend fun probe(serverUrl: String): PlexIdentity? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(serverUrl.trimEnd('/') + IDENTITY_PATH)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else parse(response.body.string())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val IDENTITY_PATH = "/identity"
        private const val PROBE_TIMEOUT_SECONDS = 5L
        private val json = Json { ignoreUnknownKeys = true }
        private val xmlAttribute = { name: String -> Regex("""\b$name="([^"]*)"""") }

        /**
         * Reads an `/identity` body: JSON when the server honoured `Accept: application/json`,
         * otherwise the XML Plex sends by default.
         *
         * @return null unless the body names a `machineIdentifier`.
         */
        fun parse(body: String): PlexIdentity? {
            val trimmed = body.trim()
            return if (trimmed.startsWith("{")) parseJson(trimmed) else parseXml(trimmed)
        }

        private fun parseJson(body: String): PlexIdentity? {
            val container = runCatching { json.parseToJsonElement(body) }.getOrNull()
                ?.let { (it as? JsonObject)?.get("MediaContainer") as? JsonObject } ?: return null
            val machineIdentifier = (container["machineIdentifier"] as? JsonPrimitive)?.content
                ?.takeIf { it.isNotBlank() } ?: return null
            return PlexIdentity(machineIdentifier, (container["version"] as? JsonPrimitive)?.content)
        }

        private fun parseXml(body: String): PlexIdentity? {
            val start = body.indexOf("<MediaContainer").takeIf { it >= 0 } ?: return null
            // Only the container's own attributes; the XML prolog has a "version" too.
            val tag = body.substring(start, body.indexOf('>', start).takeIf { it >= 0 } ?: body.length)
            val machineIdentifier = xmlAttribute("machineIdentifier").find(tag)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() } ?: return null
            return PlexIdentity(machineIdentifier, xmlAttribute("version").find(tag)?.groupValues?.get(1))
        }
    }
}
