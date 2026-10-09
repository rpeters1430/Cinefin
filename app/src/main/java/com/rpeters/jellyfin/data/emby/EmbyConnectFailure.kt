package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.data.repository.common.ErrorType
import com.rpeters.jellyfin.data.security.PinningValidationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** User-facing messages never include response bodies, URLs, passwords or access keys. */
object EmbyConnectFailure {
    fun type(error: Throwable): ErrorType = when {
        generateSequence(error) { it.cause }.take(12).any { it is PinningValidationException || it is SSLException } -> ErrorType.PINNING
        error is EmbyHttpException && error.code in listOf(401, 403) -> ErrorType.AUTHENTICATION
        error is UnknownHostException -> ErrorType.DNS_RESOLUTION
        error is SocketTimeoutException -> ErrorType.TIMEOUT
        error is EmbyHttpException -> ErrorType.SERVER_ERROR
        error is SerializationException || error is IllegalArgumentException -> ErrorType.SERVER_ERROR
        error is IOException -> ErrorType.NETWORK
        else -> ErrorType.UNKNOWN
    }

    fun message(error: Throwable, accountSignIn: Boolean = false): String = when (type(error)) {
        ErrorType.AUTHENTICATION -> if (accountSignIn) {
            "Emby Connect did not accept this username/email and password. Check your Emby account details."
        } else {
            "Your Emby account no longer has access to this server. Check its account link, then sign in again."
        }
        ErrorType.PINNING -> "The server certificate could not be verified. Check the certificate or saved trust settings before retrying."
        ErrorType.DNS_RESOLUTION -> "The Emby address could not be found. Check your network connection and server address."
        ErrorType.TIMEOUT -> "Emby did not respond in time. Check that the server is running and reachable, then retry."
        ErrorType.NETWORK -> "Could not reach Emby. Check your network connection and server access, then retry."
        ErrorType.SERVER_ERROR -> if (error is EmbyHttpException) {
            "Emby returned HTTP ${error.code}. Try again or check the server status."
        } else {
            "Emby returned an unexpected response. Check the server version and retry."
        }
        else -> "Emby sign-in could not be completed. Try again or enter the server manually."
    }
}
