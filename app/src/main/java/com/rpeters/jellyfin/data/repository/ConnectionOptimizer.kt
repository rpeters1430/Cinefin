package com.rpeters.jellyfin.data.repository

import android.content.Context
import android.util.Log
import com.rpeters.jellyfin.BuildConfig
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.di.JellyfinClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.PublicSystemInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimized connection management for Jellyfin servers.
 * Implements parallel server discovery with intelligent URL prioritization.
 */
@Singleton
class ConnectionOptimizer @Inject constructor(
    private val clientFactory: JellyfinClientFactory,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ConnectionOptimizer"
        private const val CONNECTION_TIMEOUT_MS = 5000L
        private const val MAX_PARALLEL_REQUESTS = 4
    }

    /**
     * Test server connection with parallel URL discovery
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return withContext(Dispatchers.IO) {
            logDebug("Starting parallel server discovery for: $serverUrl")

            val urlVariations = getUrlVariations(serverUrl)
            val prioritizedUrls = prioritizeUrls(urlVariations)

            logDebug("Testing ${prioritizedUrls.size} URL variations in parallel")

            // Test URLs in parallel with limited concurrency
            val results = prioritizedUrls
                .take(MAX_PARALLEL_REQUESTS)
                .map { url ->
                    async {
                        logDebug("Testing endpoint: $url")
                        testSingleEndpoint(url)
                    }
                }

            // Return first successful result
            results.forEachIndexed { index, deferred ->
                try {
                    val result = deferred.await()
                    if (result is ApiResult.Success) {
                        logDebug("Successfully connected to: ${prioritizedUrls[index]}")
                        return@withContext result
                    }
                } catch (e: Exception) {
                    logDebug("Failed to connect to ${prioritizedUrls[index]}: ${e.message}")
                }
            }

            logDebug("No working endpoints found")
            ApiResult.Error("No working endpoints found for $serverUrl")
        }
    }

    /**
     * Get URL variations to test
     */
    private fun getUrlVariations(baseUrl: String): List<String> {
        val urls = mutableListOf<String>()

        // Normalize base URL
        val normalizedUrl = baseUrl.trim().removeSuffix("/")

        // Add HTTPS variations
        if (normalizedUrl.startsWith("https://")) {
            urls.add(normalizedUrl)
            urls.add(normalizedUrl.replace("https://", "http://"))
        } else if (normalizedUrl.startsWith("http://")) {
            urls.add(normalizedUrl.replace("http://", "https://"))
            urls.add(normalizedUrl)
        } else {
            // No protocol specified
            urls.add("https://$normalizedUrl")
            urls.add("http://$normalizedUrl")
        }

        // Add port variations
        val urlsWithPorts = mutableListOf<String>()
        urls.forEach { url ->
            urlsWithPorts.add(url)
            if (!url.contains(":")) {
                urlsWithPorts.add("$url:8096") // Default Jellyfin port
                urlsWithPorts.add("$url:443") // HTTPS port
                urlsWithPorts.add("$url:80") // HTTP port
            }
        }

        return urlsWithPorts.distinct()
    }

    /**
     * Prioritize URLs for faster discovery
     */
    private fun prioritizeUrls(urls: List<String>): List<String> {
        return urls.sortedBy { url ->
            when {
                url.startsWith("https://") -> 0 // HTTPS first
                url.startsWith("http://") -> 1 // HTTP second
                url.contains(":8096") -> 2 // Default Jellyfin port
                url.contains(":443") -> 3 // Standard HTTPS port
                url.contains(":80") -> 4 // Standard HTTP port
                else -> 5 // Other ports last
            }
        }
    }

    /**
     * Test a single endpoint with timeout
     */
    private suspend fun testSingleEndpoint(url: String): ApiResult<PublicSystemInfo> {
        return try {
            withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                val client = clientFactory.getClient(url)
                val response = client.systemApi.getPublicSystemInfo()
                ApiResult.Success(response.content)
            } ?: ApiResult.Error("Connection timeout for $url")
        } catch (e: Exception) {
            ApiResult.Error("Connection failed for $url: ${e.message}")
        }
    }

    /**
     * Helper function for debug logging that only logs in debug builds
     */
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
