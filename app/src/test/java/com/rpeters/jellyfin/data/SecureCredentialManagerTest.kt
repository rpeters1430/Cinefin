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
    private val generateKeyMethod = SecureCredentialManager::class.java.getDeclaredMethod(
        "generateKey",
        String::class.java,
        String::class.java,
    ).apply { isAccessible = true }

    private fun generateKey(url: String, username: String): String {
        return generateKeyMethod.invoke(manager, url, username) as String
    }

    @Test
    fun `generateKey normalizes server URL`() {
        val keyA = generateKey("HTTPS://Example.com/", "user")
        val keyB = generateKey("https://example.com", "user")
        assertEquals(keyA, keyB)
    }

    @Test
    fun `credentials saved with url variant are retrievable`() = runTest {
        val store = mutableMapOf<String, String>()
        val username = "user"
        val password = "secret"

        val variantWithSlash = "https://Example.com/"
        val variantUpper = "HTTPS://example.COM"

        store[generateKey(variantWithSlash, username)] = password

        val retrieved = store[generateKey(variantUpper, username)]
        assertEquals(password, retrieved)
    }

    @Test
    fun `normalizeServerUrl trims and lowercases`() {
        val normalized = normalizeServerUrl(" HTTPS://Example.com/ ")
        assertEquals("https://example.com", normalized)
    }
}
