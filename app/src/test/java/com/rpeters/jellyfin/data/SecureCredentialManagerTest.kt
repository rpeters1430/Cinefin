package com.rpeters.jellyfin.data

import android.content.Context
import com.rpeters.jellyfin.utils.normalizeServerUrl
import com.rpeters.jellyfin.utils.normalizeServerUrlLegacy
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureCredentialManagerTest {
    private val context: Context = mockk(relaxed = true)
    private val manager = SecureCredentialManager(context)

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
}
