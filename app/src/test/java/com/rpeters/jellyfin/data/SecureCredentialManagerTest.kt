package com.rpeters.jellyfin.data

import android.content.Context
import com.rpeters.jellyfin.utils.normalizeServerUrl
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureCredentialManagerTest {
    private val context: Context = mockk(relaxed = true)
    private val manager = SecureCredentialManager(context)

    @Test
    fun `generateKey normalizes server URL`() {
        val keyA = manager.generateKey("HTTPS://Example.com/", "user")
        val keyB = manager.generateKey("https://example.com", "user")
        assertEquals(keyA, keyB)
    }

    @Test
    fun `credentials saved with url variant are retrievable`() = runTest {
        val store = mutableMapOf<String, String>()
        val username = "user"
        val password = "secret"

        val variantWithSlash = "https://Example.com/"
        val variantUpper = "HTTPS://example.COM"

        store[manager.generateKey(variantWithSlash, username)] = password

        val retrieved = store[manager.generateKey(variantUpper, username)]
        assertEquals(password, retrieved)
    }

    @Test
    fun `normalizeServerUrl trims and lowercases`() {
        val normalized = normalizeServerUrl(" HTTPS://Example.com/ ")
        assertEquals("https://example.com", normalized)
    }
}
