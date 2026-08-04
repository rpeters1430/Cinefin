package com.rpeters.jellyfin.data.ai

import android.os.Build
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

private const val PROMPT = "prompt"
private const val CLOUD_RESPONSE = "cloud response"
private const val NANO_RESPONSE = "nano response"
private const val NANO_BOOM = "nano boom"
private val STREAM_CHUNKS = listOf("cloud", "stream")

/**
 * Covers HybridAiTextModel's routing logic: the forceCloud bypass, falling back to cloud
 * when Nano is unsupported/inactive, and the circuit breaker that permanently switches a
 * flaky Nano backend to cloud for the rest of the session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HybridAiTextModelTest {

    @Suppress("LateinitUsage")
    private lateinit var remoteConfig: RemoteConfigRepository

    @Suppress("LateinitUsage")
    private lateinit var cloudModel: AiTextModel

    @Suppress("LateinitUsage")
    private lateinit var nanoModel: MlKitAiTextModel

    private fun setDeviceIsPixel(isPixel: Boolean) {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", if (isPixel) "Google" else "samsung")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", if (isPixel) "Pixel 9 Pro" else "SM-S928U")
    }

    @Before
    fun setUp() {
        remoteConfig = mockk()
        cloudModel = mockk()
        nanoModel = mockk()
        every { remoteConfig.getBoolean("enable_on_device_ai") } returns true
        every { nanoModel.downloadState } returns MutableStateFlow(AiDownloadState.READY)
        setDeviceIsPixel(true)
    }

    private fun buildModel() = HybridAiTextModel(
        remoteConfig = remoteConfig,
        cloudModel = cloudModel,
        label = "test",
        nanoModel = nanoModel,
    )

    @Test
    fun `forceCloud true skips Nano even when Nano is ready and supported`() = runTest {
        coEvery { cloudModel.generateText(any(), any()) } returns CLOUD_RESPONSE
        val model = buildModel()

        val result = model.generateText(PROMPT, forceCloud = true)

        assertEquals(CLOUD_RESPONSE, result)
        coVerify(exactly = 0) { nanoModel.generateText(any(), any()) }
    }

    @Test
    fun `forceCloud true on stream skips Nano`() = runTest {
        every { cloudModel.generateTextStream(any(), any()) } returns STREAM_CHUNKS.asFlow()
        val model = buildModel()

        val result = model.generateTextStream(PROMPT, forceCloud = true).toList()

        assertEquals(STREAM_CHUNKS, result)
    }

    @Test
    fun `uses Nano when supported, enabled, and ready`() = runTest {
        coEvery { nanoModel.generateText(any(), any()) } returns NANO_RESPONSE
        val model = buildModel()

        val result = model.generateText(PROMPT)

        assertEquals(NANO_RESPONSE, result)
        coVerify(exactly = 0) { cloudModel.generateText(any(), any()) }
        assertTrue(model.isNanoActive.value)
    }

    @Test
    fun `falls back to cloud on non-Pixel device even when Nano is ready`() = runTest {
        setDeviceIsPixel(false)
        coEvery { cloudModel.generateText(any(), any()) } returns CLOUD_RESPONSE
        val model = buildModel()

        val result = model.generateText(PROMPT)

        assertEquals(CLOUD_RESPONSE, result)
        coVerify(exactly = 0) { nanoModel.generateText(any(), any()) }
        assertFalse(model.isNanoActive.value)
    }

    @Test
    fun `falls back to cloud when remote config disables on-device AI`() = runTest {
        every { remoteConfig.getBoolean("enable_on_device_ai") } returns false
        coEvery { cloudModel.generateText(any(), any()) } returns CLOUD_RESPONSE
        val model = buildModel()

        val result = model.generateText(PROMPT)

        assertEquals(CLOUD_RESPONSE, result)
        assertFalse(model.isNanoActive.value)
    }

    @Test
    fun `single Nano failure falls back to cloud for that request but keeps Nano active`() = runTest {
        coEvery { nanoModel.generateText(any(), any()) } throws RuntimeException(NANO_BOOM)
        coEvery { cloudModel.generateText(any(), any()) } returns CLOUD_RESPONSE
        val model = buildModel()

        val result = model.generateText(PROMPT)

        assertEquals(CLOUD_RESPONSE, result)
        assertTrue("Nano should still be considered active after a single failure", model.isNanoActive.value)
    }

    @Test
    fun `circuit breaks after three consecutive Nano failures and stays on cloud`() = runTest {
        coEvery { nanoModel.generateText(any(), any()) } throws RuntimeException(NANO_BOOM)
        coEvery { cloudModel.generateText(any(), any()) } returns CLOUD_RESPONSE
        val model = buildModel()

        repeat(3) { model.generateText(PROMPT) }

        assertFalse("Circuit should be broken after 3 consecutive failures", model.isNanoActive.value)
        coVerify(exactly = 3) { nanoModel.generateText(any(), any()) }

        // Further calls should go straight to cloud without touching Nano again.
        val result = model.generateText(PROMPT)
        assertEquals(CLOUD_RESPONSE, result)
        coVerify(exactly = 3) { nanoModel.generateText(any(), any()) }
    }

    @Test
    fun `retryDownload resets the circuit breaker`() = runTest {
        coEvery { nanoModel.generateText(any(), any()) } throws RuntimeException(NANO_BOOM)
        coEvery { cloudModel.generateText(any(), any()) } returns CLOUD_RESPONSE
        coEvery { nanoModel.downloadModel() } returns Unit
        val model = buildModel()

        repeat(3) { model.generateText(PROMPT) }
        assertFalse(model.isNanoActive.value)

        model.retryDownload()

        coVerify(exactly = 1) { nanoModel.downloadModel() }
        assertTrue("isNanoActive should recover once the circuit breaker is reset", model.isNanoActive.value)

        coEvery { nanoModel.generateText(any(), any()) } returns NANO_RESPONSE
        val result = model.generateText(PROMPT)
        assertEquals(NANO_RESPONSE, result)
    }

    @Test
    fun `downloadState delegates to the shared nano model`() = runTest {
        val states = MutableStateFlow(AiDownloadState.DOWNLOADING)
        every { nanoModel.downloadState } returns states
        val model = buildModel()

        assertEquals(AiDownloadState.DOWNLOADING, model.downloadState.value)
        states.value = AiDownloadState.READY
        assertEquals(AiDownloadState.READY, model.downloadState.value)
    }
}
