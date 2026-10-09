package com.rpeters.jellyfin.data.emby

import com.rpeters.jellyfin.data.repository.common.ErrorType
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class EmbyConnectFailureTest {
    @Test
    fun incorrectAccountCredentials_andRevokedServerLink_haveDifferentMessages() {
        val error = EmbyHttpException(401, "sensitive-response")
        assertEquals(ErrorType.AUTHENTICATION, EmbyConnectFailure.type(error))
        assertTrue(EmbyConnectFailure.message(error, true).contains("username/email"))
        assertTrue(EmbyConnectFailure.message(error).contains("no longer has access"))
        assertFalse(EmbyConnectFailure.message(error).contains("sensitive-response"))
    }

    @Test
    fun connectionFailures_distinguishDnsTimeoutAndCertificate() {
        assertEquals(ErrorType.DNS_RESOLUTION, EmbyConnectFailure.type(UnknownHostException("secret-address")))
        assertEquals(ErrorType.TIMEOUT, EmbyConnectFailure.type(SocketTimeoutException()))
        assertEquals(ErrorType.PINNING, EmbyConnectFailure.type(SSLHandshakeException("secret-certificate")))
        assertTrue(EmbyConnectFailure.message(SSLHandshakeException("certificate")).contains("certificate"))
    }

    @Test
    fun linkedServer_toString_neverIncludesItsCredentials() {
        val server = EmbyConnectServer("NAS", "id", emptyList(), "private-user", "private-key")
        assertFalse(server.toString().contains("private-key"))
        assertFalse(server.toString().contains("private-user"))
    }
}
