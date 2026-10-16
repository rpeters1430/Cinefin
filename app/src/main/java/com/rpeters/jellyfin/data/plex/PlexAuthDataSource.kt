package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.network.DeviceIdentityProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Where a plex.tv PIN sign-in is. [SignedIn], [Expired] and [Failed] end the flow. */
sealed interface PlexSignInState {
    /** Show [code] and offer [authUrl]; the flow keeps polling until the user approves. */
    data class WaitingForApproval(val code: String, val authUrl: String) : PlexSignInState

    data class SignedIn(val accountToken: String) : PlexSignInState {
        override fun toString(): String = "SignedIn(accountToken=redacted)"
    }

    /** The PIN ran out before it was approved. Start a new sign-in to get a new code. */
    data object Expired : PlexSignInState

    data class Failed(val message: String, val errorType: ErrorType) : PlexSignInState
}

/**
 * plex.tv PIN sign-in with the legacy account token (KD6): create a PIN, show its code, poll until
 * the user approves it on plex.tv. Polling slows from every [INITIAL_POLL_MS] to every
 * [MAX_POLL_MS] and stops when the PIN expires. Cancelling the collector cancels the sign-in.
 */
@Singleton
class PlexAuthDataSource @Inject constructor(
    private val plexTvClient: PlexTvClient,
    private val identity: DeviceIdentityProvider,
) {
    fun signIn(): Flow<PlexSignInState> = flow {
        val pin = when (val created = plexTvClient.createPin()) {
            is ApiResult.Success -> created.data
            is ApiResult.Error -> return@flow emit(PlexSignInState.Failed(created.message, created.errorType))
            is ApiResult.Loading -> return@flow
        }
        emit(PlexSignInState.WaitingForApproval(pin.code, authUrl(pin.code)))
        pollUntilApproved(pin)
    }

    private suspend fun FlowCollector<PlexSignInState>.pollUntilApproved(pin: PlexPin) {
        val lifetimeMs = (pin.expiresIn ?: DEFAULT_PIN_LIFETIME_SECONDS) * MILLIS_PER_SECOND
        var waitedMs = 0L
        var intervalMs = INITIAL_POLL_MS
        var consecutiveFailures = 0
        while (waitedMs < lifetimeMs) {
            delay(intervalMs)
            waitedMs += intervalMs
            intervalMs = (intervalMs * 2).coerceAtMost(MAX_POLL_MS)
            when (val polled = plexTvClient.getPin(pin.id)) {
                is ApiResult.Success -> {
                    consecutiveFailures = 0
                    val token = polled.data.authToken
                    if (!token.isNullOrBlank()) return emit(PlexSignInState.SignedIn(token))
                }
                is ApiResult.Error -> when (polled.errorType) {
                    ErrorType.NOT_FOUND -> return emit(PlexSignInState.Expired)
                    ErrorType.NETWORK, ErrorType.SERVER_ERROR, ErrorType.TIMEOUT -> {
                        if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            return emit(PlexSignInState.Failed(polled.message, polled.errorType))
                        }
                    }
                    else -> return emit(PlexSignInState.Failed(polled.message, polled.errorType))
                }
                is ApiResult.Loading -> Unit
            }
        }
        emit(PlexSignInState.Expired)
    }

    /** The plex.tv page where the user approves [code], already signed in to their account. */
    fun authUrl(code: String): String {
        fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
        return "$AUTH_URL#?clientID=${encode(identity.plexClientIdentifier())}&code=${encode(code)}" +
            "&context%5Bdevice%5D%5Bproduct%5D=${encode(PlexClientHeaders.PRODUCT_NAME)}"
    }

    companion object {
        const val INITIAL_POLL_MS = 1_000L
        const val MAX_POLL_MS = 5_000L
        const val MAX_CONSECUTIVE_FAILURES = 5
        private const val DEFAULT_PIN_LIFETIME_SECONDS = 900
        private const val MILLIS_PER_SECOND = 1_000L
        private const val AUTH_URL = "https://app.plex.tv/auth"
    }
}
