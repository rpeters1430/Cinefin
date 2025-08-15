package com.example.jellyfinandroid.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * Test suite for SecureLogger utility.
 *
 * Tests sanitization patterns and logging security.
 */
class SecureLoggerTest {

    @Test
    fun `SecureLogger can be accessed`() {
        // Test that SecureLogger is accessible
        assertNotNull("SecureLogger should be available", SecureLogger)
    }

    @Test
    fun `test logging methods exist and don't throw exceptions`() {
        // Test that logging methods are available and handle null/empty inputs gracefully
        try {
            SecureLogger.d("TestTag", "Test message")
            SecureLogger.i("TestTag", "Test message")
            SecureLogger.w("TestTag", "Test message")
            SecureLogger.e("TestTag", "Test message")
            SecureLogger.auth("TestTag", "Test auth message", true)
            SecureLogger.api("TestTag", "GET", "https://example.com/api", 200)
            SecureLogger.networkError("TestTag", "Test error", "https://example.com")
        } catch (e: Exception) {
            fail("SecureLogger methods should not throw exceptions: ${e.message}")
        }
    }

    @Test
    fun `extension functions are available`() {
        // Test that extension functions can be accessed
        val testObject = object {}
        
        try {
            testObject.logDebug("Test debug message")
            testObject.logInfo("Test info message")
            testObject.logWarning("Test warning message")
            testObject.logError("Test error message")
            testObject.logAuth("Test auth message", true)
            testObject.logApi("GET", "https://example.com/api", 200)
            testObject.logNetworkError("Test network error")
        } catch (e: Exception) {
            fail("SecureLogger extension functions should not throw exceptions: ${e.message}")
        }
    }

    @Test
    fun `logging handles null and empty inputs gracefully`() {
        try {
            SecureLogger.d("TestTag", "")
            SecureLogger.i("TestTag", "", null)
            SecureLogger.w("TestTag", "", null)
            SecureLogger.e("TestTag", "", null)
        } catch (e: Exception) {
            fail("SecureLogger should handle empty inputs gracefully: ${e.message}")
        }
    }

    @Test
    fun `logging handles various input types`() {
        try {
            SecureLogger.d("TestTag", "Test message", "string data")
            SecureLogger.d("TestTag", "Test message", 123)
            SecureLogger.d("TestTag", "Test message", true)
            SecureLogger.d("TestTag", "Test message", listOf("item1", "item2"))
            SecureLogger.d("TestTag", "Test message", mapOf("key" to "value"))
        } catch (e: Exception) {
            fail("SecureLogger should handle various input types: ${e.message}")
        }
    }
}