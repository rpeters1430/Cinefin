package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.di.ApplicationScope
import com.rpeters.jellyfin.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class JellyfinAuthRefreshManager @Inject constructor(
    private val authRepository: IJellyfinAuthRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : IJellyfinAuthRefreshManager {

    private val singleFlightMutex = Mutex()

    @Volatile
    private var inFlightRefresh: Deferred<RefreshResult>? = null

    override fun currentAccessToken(): String? {
        return authRepository.getCurrentServerSync()?.accessToken
    }

    override fun scheduleProactiveRefreshIfNeeded() {
        if (!authRepository.isUserAuthenticated() || !authRepository.shouldRefreshToken()) {
            return
        }

        applicationScope.launch {
            runSingleFlightRefresh(trigger = REFRESH_TRIGGER_PROACTIVE)
        }
    }

    override fun refreshAfterUnauthorized(attempt: Int): String? {
        val trigger = "$REFRESH_TRIGGER_401-$attempt"
        val result = runBlocking {
            try {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    runSingleFlightRefresh(trigger = trigger)
                } ?: run {
                    clearInFlightRefresh()
                    logTelemetry("failure", trigger, attempt, "timeout")
                    RefreshResult.Failure("timeout")
                }
            } catch (error: Throwable) {
                if (error is TimeoutCancellationException) {
                    clearInFlightRefresh()
                    logTelemetry("failure", trigger, attempt, "timeout_exception")
                    return@runBlocking RefreshResult.Failure("timeout_exception")
                }
                clearInFlightRefresh()
                SecureLogger.w(TAG, "Auth refresh exception for trigger=$trigger", error)
                RefreshResult.Failure("exception")
            }
        }

        return when (result) {
            is RefreshResult.Success -> result.token
            is RefreshResult.Failure -> {
                logTelemetry("failure", trigger, attempt, result.reason)
                null
            }
        }
    }

    private suspend fun runSingleFlightRefresh(trigger: String): RefreshResult {
        val deferred = singleFlightMutex.withLock {
            inFlightRefresh?.let { return@withLock it }
            val created = applicationScope.async {
                executeRefresh(trigger)
            }
            inFlightRefresh = created
            created.invokeOnCompletion {
                if (singleFlightMutex.tryLock()) {
                    try {
                        if (inFlightRefresh === created) {
                            inFlightRefresh = null
                        }
                    } finally {
                        singleFlightMutex.unlock()
                    }
                } else {
                    applicationScope.launch { clearInFlightRefresh(expected = created, cancel = false) }
                }
            }
            created
        }

        return deferred.await()
    }

    private suspend fun executeRefresh(trigger: String): RefreshResult {
        val elapsed = measureTimeMillis {
            repeat(RETRY_BACKOFF_MS.size) { retryIndex ->
                val delayMs = RETRY_BACKOFF_MS.getOrElse(retryIndex) { RETRY_BACKOFF_MS.last() }
                if (delayMs > 0L) {
                    delay(delayMs)
                }

                val refreshed = authRepository.forceReAuthenticate()
                if (refreshed) {
                    val token = authRepository.getCurrentServerSync()?.accessToken
                    if (!token.isNullOrBlank()) {
                        logTelemetry("success", trigger, retryIndex + 1, null)
                        return RefreshResult.Success(token)
                    }
                    logTelemetry("failure", trigger, retryIndex + 1, "missing_token")
                } else {
                    logTelemetry("failure", trigger, retryIndex + 1, "reauth_failed")
                }
            }
        }

        SecureLogger.w(TAG, "Auth refresh exhausted retries for trigger=$trigger durationMs=$elapsed")
        return RefreshResult.Failure("retry_exhausted")
    }

    private suspend fun clearInFlightRefresh(expected: Deferred<RefreshResult>? = null, cancel: Boolean = true) {
        singleFlightMutex.withLock {
            val current = inFlightRefresh ?: return
            if (expected != null && current !== expected) {
                return
            }
            if (cancel) {
                current.cancel()
            }
            inFlightRefresh = null
        }
    }

    private fun logTelemetry(
        outcome: String,
        trigger: String,
        attempt: Int,
        reason: String?,
    ) {
        val reasonPart = reason?.let { " reason=$it" } ?: ""
        SecureLogger.i(TAG, "AuthRefreshEvent outcome=$outcome trigger=$trigger attempt=$attempt$reasonPart")
    }

    private sealed class RefreshResult {
        data class Success(val token: String) : RefreshResult()
        data class Failure(val reason: String) : RefreshResult()
    }

    companion object {
        private const val TAG = "JellyfinAuthRefreshManager"
        private const val REFRESH_TRIGGER_PROACTIVE = "proactive"
        private const val REFRESH_TRIGGER_401 = "http_401"
        private const val REFRESH_TIMEOUT_MS = 10_000L
        private val RETRY_BACKOFF_MS = longArrayOf(0L, 100L, 500L)
    }
}
