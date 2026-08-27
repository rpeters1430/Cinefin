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

        // refreshAfterUnauthorized() blocks its caller's thread via runBlocking, so the 10
        // concurrent callers below occupy up to 10 Dispatchers.Default threads for the whole
        // call. That pool is small (~core count) on CI runners, so giving the manager's own
        // applicationScope the *same* shared pool starves the single-flight executeRefresh()
        // coroutine of a thread to run on, occasionally exceeding REFRESH_TIMEOUT_MS and
        // failing the assertion below. A dedicated pool for the manager avoids that contention.
        val refreshDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
        try {
            val testRefreshManager = JellyfinAuthRefreshManager(
                authRepository = authRepository,
                applicationScope = CoroutineScope(refreshDispatcher + Job()),
            )

            val tokens = (1..10).map {
                async(Dispatchers.Default) {
                    testRefreshManager.refreshAfterUnauthorized(attempt = 1)
                }
            }.awaitAll()

            assertEquals(List(10) { "shared-token" }, tokens)
            coVerify(exactly = 1) { authRepository.forceReAuthenticate() }
        } finally {
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
