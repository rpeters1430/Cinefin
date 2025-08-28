package com.rpeters.jellyfin.network

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class CachePolicyInterceptor(
    private val connectivity: ConnectivityChecker,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val url = req.url.toString()
        val isWrite = req.method != "GET"
        val isAuth = url.contains("/Users/Authenticate", true) ||
            url.contains("/Sessions", true)
        val isImage = url.contains("/Items/", true) &&
            url.contains("/Images", true)

        val newReq = when {
            isWrite || isAuth -> req.newBuilder()
                .header("Cache-Control", "no-cache")
                .build()
            !connectivity.isOnline() -> req.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(7, TimeUnit.DAYS)
                        .build(),
                )
                .build()
            else -> req
        }

        val resp = chain.proceed(newReq)

        if (isImage && resp.header("Cache-Control").isNullOrBlank()) {
            return resp.newBuilder()
                .header("Cache-Control", "public, max-age=2592000")
                .build()
        }
        return resp
    }
}
