package com.rpeters.jellyfin.data.cache

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for JellyfinCache to verify race condition fixes and thread safety.
 *
 * Specifically tests:
 * - Cache directory initialization race condition (KNOWN_ISSUES #1)
 * - Thread safety of memory cache operations (KNOWN_ISSUES #2)
 * - Concurrent cache operations don't cause crashes
 */
class JellyfinCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var applicationScope: CoroutineScope
    private lateinit var cache: JellyfinCache

    @Before
    fun setup() {
        applicationScope = CoroutineScope(testDispatcher)

        // Mock context with real temp directory
        mockContext = mockk(relaxed = true) {
            every { cacheDir } returns tempFolder.root
        }

        // Create cache instance
        cache = JellyfinCache(mockContext, applicationScope)
    }

    @Test
    fun `cacheItems and getCachedItems work correctly on first access`() = runTest(testDispatcher) {
        // Given - simulates first app launch scenario
        val key = "test_key"
        val testItems = listOf(
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "Test Item 1"
            },
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "Test Item 2"
            },
        )

        // When - cache items immediately (race condition test)
        val cacheResult = cache.cacheItems(key, testItems)

        // Then - should succeed without NPE
        assertTrue("Cache operation should succeed", cacheResult)

        // When - retrieve cached items
        val retrievedItems = cache.getCachedItems(key)

        // Then - should return the cached items
        assertNotNull("Should retrieve cached items", retrievedItems)
        assertEquals("Should have same number of items", testItems.size, retrievedItems!!.size)
    }

    @Test
    fun `concurrent cache operations don't cause race conditions`() = runTest(testDispatcher) {
        // Given - multiple cache operations happening concurrently
        val keys = (1..10).map { "concurrent_key_$it" }
        val testItems = listOf(
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "Concurrent Test Item"
            },
        )

        // When - perform concurrent cache operations
        val cacheJobs = keys.map { key ->
            async(Dispatchers.IO) {
                cache.cacheItems(key, testItems)
            }
        }

        // Wait for all operations to complete
        val results = cacheJobs.awaitAll()

        // Then - all operations should succeed without crashes
        assertTrue("All cache operations should succeed", results.all { it })

        // Verify all items can be retrieved
        val retrieveJobs = keys.map { key ->
            async(Dispatchers.IO) {
                cache.getCachedItems(key)
            }
        }

        val retrievedItems = retrieveJobs.awaitAll()

        // Then - all items should be retrievable
        assertEquals("Should retrieve all cached items", keys.size, retrievedItems.size)
        assertTrue("All retrieved items should be non-null", retrievedItems.all { it != null })
    }

    @Test
    fun `getCachedItems returns null for non-existent key`() = runTest(testDispatcher) {
        // Given - a key that doesn't exist in cache
        val nonExistentKey = "non_existent_key"

        // When - try to retrieve items
        val result = cache.getCachedItems(nonExistentKey)

        // Then - should return null without crashing
        assertNull("Should return null for non-existent key", result)
    }

    @Test
    fun `cache respects TTL expiration`() = runTest(testDispatcher) {
        // Given - items cached with very short TTL
        val key = "ttl_test_key"
        val testItems = listOf(
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "TTL Test Item"
            },
        )
        val shortTtl = 1L // 1 millisecond

        // When - cache items with short TTL
        cache.cacheItems(key, testItems, shortTtl)

        // Wait for TTL to expire
        Thread.sleep(10)

        // Then - should return null for expired cache
        val result = cache.getCachedItems(key)
        assertNull("Should return null for expired cache", result)
    }

    @Test
    fun `cache directory is created if it doesn't exist`() = runTest(testDispatcher) {
        // Given - fresh cache instance
        val key = "directory_test_key"
        val testItems = listOf(
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "Directory Test Item"
            },
        )

        // When - perform cache operation
        val result = cache.cacheItems(key, testItems)

        // Then - cache directory should be created
        assertTrue("Cache operation should succeed", result)
        val cacheDir = File(tempFolder.root, "jellyfin_cache")
        assertTrue("Cache directory should exist", cacheDir.exists())
        assertTrue("Cache directory should be a directory", cacheDir.isDirectory)
    }

    @Test
    fun `memory cache and disk cache work together`() = runTest(testDispatcher) {
        // Given - items to cache
        val key = "memory_disk_test_key"
        val testItems = listOf(
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "Memory Disk Test Item"
            },
        )

        // When - cache items (should go to both memory and disk)
        cache.cacheItems(key, testItems)

        // Then - first retrieval should hit memory cache
        val firstRetrieval = cache.getCachedItems(key)
        assertNotNull("First retrieval should succeed", firstRetrieval)

        // Create new cache instance (clears memory cache)
        val newCache = JellyfinCache(mockContext, applicationScope)

        // Then - second retrieval should hit disk cache
        val secondRetrieval = newCache.getCachedItems(key)
        assertNotNull("Second retrieval from disk should succeed", secondRetrieval)
        assertEquals("Should retrieve same number of items", testItems.size, secondRetrieval!!.size)
    }

    @Test
    fun `cache handles empty list correctly`() = runTest(testDispatcher) {
        // Given - empty list of items
        val key = "empty_list_key"
        val emptyList = emptyList<BaseItemDto>()

        // When - cache empty list
        val cacheResult = cache.cacheItems(key, emptyList)

        // Then - should succeed
        assertTrue("Should cache empty list", cacheResult)

        // When - retrieve empty list
        val retrievedItems = cache.getCachedItems(key)

        // Then - should return empty list, not null
        assertNotNull("Should return empty list, not null", retrievedItems)
        assertTrue("Should be empty", retrievedItems!!.isEmpty())
    }

    @Test
    fun `concurrent reads and writes don't cause ConcurrentModificationException`() = runTest(testDispatcher) {
        // Given - initial cached items
        val key = "concurrent_rw_key"
        val testItems = listOf(
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns java.util.UUID.randomUUID()
                every { name } returns "Concurrent RW Test Item"
            },
        )
        cache.cacheItems(key, testItems)

        // When - perform concurrent reads and writes
        val operations = (1..20).map { index ->
            async(Dispatchers.IO) {
                if (index % 2 == 0) {
                    // Write operation
                    cache.cacheItems("key_$index", testItems)
                } else {
                    // Read operation
                    cache.getCachedItems(key)
                }
            }
        }

        // Wait for all operations
        val results = operations.awaitAll()

        // Then - all operations should complete without exceptions
        assertNotNull("All operations should complete", results)
        assertEquals("Should have results for all operations", 20, results.size)
    }
}
