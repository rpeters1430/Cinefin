package com.rpeters.jellyfin.data.repository

import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinAuthRefreshManagerTest {
    private lateinit var authRepository: IJellyfinAuthRepository
    private lateinit var refreshManager: JellyfinAuthRefreshManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        authRepository = mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        // Use a scope that uses the test dispatcher
        refreshManager = JellyfinAuthRefreshManager(
            authRepository = authRepository,
            applicationScope = CoroutineScope(testDispatcher + Job()),
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `simultaneous unauthorized refresh requests execute a single refresh`() = runBlocking {
        coEvery { authRepository.forceReAuthenticate() } coAnswers {
            delay(50)
            true
        }
        every { authRepository.getCurrentServerSync() } returns mockk {
            every { accessToken } returns "shared-token"
        }

        // The single-flight coalescing this test verifies only holds while all 10 calls are
        // actually in flight together: refreshAfterUnauthorized() blocks its caller's thread
        // via runBlocking, and executeRefresh() completes in ~50ms (one mocked
        // forceReAuthenticate() call, no retry needed). Dispatching all 10 callers on the
        // shared Dispatchers.Default pool (sized ~core count on CI runners) means the last
        // few can still be queued, waiting for a free thread, by the time the first refresh
        // has already completed and cleared its in-flight marker -- so they start a second,
        // independent refresh instead of joining the first, and forceReAuthenticate() gets
        // called more than once. Dedicated pools sized well above the 10 callers (and
        // separately for the manager's own work) guarantee true concurrency regardless of the
        // runner's core count.
        val callerDispatcher = Executors.newFixedThreadPool(16).asCoroutineDispatcher()
        val refreshDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        try {
            val testRefreshManager = JellyfinAuthRefreshManager(
                authRepository = authRepository,
                applicationScope = CoroutineScope(refreshDispatcher + Job()),
            )

            val tokens = (1..10).map {
                async(callerDispatcher) {
                    testRefreshManager.refreshAfterUnauthorized(attempt = 1)
                }
            }.awaitAll()

            assertEquals(List(10) { "shared-token" }, tokens)
            coVerify(exactly = 1) { authRepository.forceReAuthenticate() }
        } finally {
            callerDispatcher.close()
            refreshDispatcher.close()
        }
    }

    @Test
    fun `returns null when all refresh attempts fail`() = runBlocking {
        coEvery { authRepository.forceReAuthenticate() } returns false
        every { authRepository.getCurrentServerSync() } returns null

        val testRefreshManager = JellyfinAuthRefreshManager(
            authRepository = authRepository,
            applicationScope = CoroutineScope(Dispatchers.Default + Job()),
        )

        val token = testRefreshManager.refreshAfterUnauthorized(attempt = 1)

        assertNull(token)
        coVerify(exactly = 3) { authRepository.forceReAuthenticate() }
    }
}
