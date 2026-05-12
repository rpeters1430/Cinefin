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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

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
            delay(100)
            true
        }
        every { authRepository.getCurrentServer() } returns mockk {
            every { accessToken } returns "shared-token"
        }

        val tokens = (1..10).map {
            async(Dispatchers.Default) {
                refreshManager.refreshAfterUnauthorized(attempt = 1)
            }
        }.awaitAll()

        assertEquals(List(10) { "shared-token" }, tokens)
        coVerify(exactly = 1) { authRepository.forceReAuthenticate() }
    }

    @Test
    fun `returns null when all refresh attempts fail`() = runBlocking {
        coEvery { authRepository.forceReAuthenticate() } returns false

        val token = refreshManager.refreshAfterUnauthorized(attempt = 1)

        assertNull(token)
        coVerify(exactly = 3) { authRepository.forceReAuthenticate() }
    }
}
