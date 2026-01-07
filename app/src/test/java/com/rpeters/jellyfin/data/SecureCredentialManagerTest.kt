package com.rpeters.jellyfin.data

import android.content.Context
import com.rpeters.jellyfin.utils.SecureLogger
import com.rpeters.jellyfin.utils.normalizeServerUrl
import com.rpeters.jellyfin.utils.normalizeServerUrlLegacy
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureCredentialManagerTest {
    private val context: Context = mockk(relaxed = true)
    private val repository = mockk<CredentialSecurityPreferencesRepository>()
    private val manager = SecureCredentialManager(context, repository)

    init {
        every { repository.preferences } returns flowOf(CredentialSecurityPreferences.DEFAULT)
        coEvery { repository.currentPreferences() } returns CredentialSecurityPreferences.DEFAULT
    }

    @Test
    fun `generateKey produces consistent key for normalized URLs`() {
        val keyA = manager.generateKey(normalizeServerUrl("HTTPS://Example.com:8920/"), "user")
        val keyB = manager.generateKey(normalizeServerUrl("https://example.com:8920"), "user")
        assertEquals(keyA, keyB)
    }

    @Test
    fun `credentials saved with url variant are retrievable`() = runTest {
        val store = mutableMapOf<String, String>()
        val username = "user"
        val password = "secret"

        val variantWithPort = "https://Example.com:8096/"
        val variantCanonical = "https://example.com:8096"

        store[manager.generateKey(normalizeServerUrlLegacy(variantWithPort), username)] = password

        val retrieved = store[manager.generateKey(normalizeServerUrl(variantCanonical), username)]
        assertEquals(password, retrieved)
    }

    @Test
    fun `normalizeServerUrl trims and lowercases`() {
        val normalized = normalizeServerUrl(" HTTPS://Example.com/ ")
        assertEquals("https://example.com", normalized)
    }

    @Test
    fun `normalizeServerUrl retains port`() {
        val normalized = normalizeServerUrl("https://example.com:8096/")
        assertEquals("https://example.com:8096", normalized)
    }

    @Test
    fun `normalizeServerUrlLegacy retains port`() {
        val normalized = normalizeServerUrlLegacy(" HTTPS://Example.com:8096/ ")
        assertEquals("https://example.com:8096", normalized)
    }

    @Test
    fun `debug logs are suppressed when not in debug mode`() {
        mockkObject(SecureLogger)
        try {
            val originalFlag = manager.debugLoggingEnabled
            justRun { SecureLogger.d(any(), any(), any()) }
            manager.debugLoggingEnabled = false

            manager.logDebug { "should not log" }

            verify(exactly = 0) { SecureLogger.d(any(), any(), any()) }
        } finally {
            manager.debugLoggingEnabled = originalFlag
            unmockkObject(SecureLogger)
        }
    }
}
