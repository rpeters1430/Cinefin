@file:Suppress("LateinitUsage")

package com.rpeters.jellyfin.data.repository

import android.os.Build
import com.rpeters.jellyfin.data.ai.AiDownloadState
import com.rpeters.jellyfin.data.ai.HybridAiTextModel
import com.rpeters.jellyfin.data.ai.MlKitAiTextModel
import com.rpeters.jellyfin.utils.AnalyticsHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Covers GenerativeAiRepository's startup Nano-init wiring and the forceCloud contract on the
 * "avoid Nano safety filter issues" call sites: even with a ready, active on-device Nano
 * backend, these must still route to the cloud model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GenerativeAiRepositoryTest {

    private lateinit var remoteConfig: RemoteConfigRepository
    private lateinit var analytics: AnalyticsHelper
    private lateinit var primaryNanoModel: MlKitAiTextModel
    private lateinit var primaryCloudModel: com.rpeters.jellyfin.data.ai.AiTextModel
    private lateinit var proNanoModel: MlKitAiTextModel
    private lateinit var proCloudModel: com.rpeters.jellyfin.data.ai.AiTextModel
    private lateinit var primaryModel: HybridAiTextModel
    private lateinit var proModel: HybridAiTextModel

    @Before
    fun setUp() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 9 Pro")

        remoteConfig = mockk()
        every { remoteConfig.getBoolean("enable_on_device_ai") } returns true
        every { remoteConfig.getBoolean("ai_force_pro_model") } returns false
        every { remoteConfig.getBoolean("enable_ai_features") } returns true
        every { remoteConfig.getLong(any()) } returns 0L
        every { remoteConfig.getString(any()) } returns ""
        every { remoteConfig.getDouble(any()) } returns 0.0

        analytics = mockk(relaxed = true)

        // Both backends start with Nano READY/active, so any forceCloud bypass is provable:
        // if the call reached cloudModel instead of nanoModel, forceCloud actually worked.
        primaryNanoModel = mockk()
        every { primaryNanoModel.downloadState } returns MutableStateFlow(AiDownloadState.READY)
        primaryCloudModel = mockk()
        primaryModel = HybridAiTextModel(remoteConfig, primaryCloudModel, "primary", primaryNanoModel)

        proNanoModel = mockk()
        every { proNanoModel.downloadState } returns MutableStateFlow(AiDownloadState.READY)
        proCloudModel = mockk()
        proModel = HybridAiTextModel(remoteConfig, proCloudModel, "pro", proNanoModel)
    }

    private fun buildRepository() = GenerativeAiRepository(
        primaryModel = primaryModel,
        proModel = proModel,
        remoteConfig = remoteConfig,
        analytics = analytics,
    )

    @Test
    fun `initialize triggers Nano availability check on both backends`() = runTest {
        coEvery { primaryNanoModel.initialize() } returns Unit
        coEvery { proNanoModel.initialize() } returns Unit
        val repository = buildRepository()

        repository.initialize()

        coVerify(exactly = 1) { primaryNanoModel.initialize() }
        coVerify(exactly = 1) { proNanoModel.initialize() }
    }

    @Test
    fun `checkCloudApiHealth forces cloud even though Nano is ready and active`() = runTest {
        coEvery { primaryCloudModel.generateText(any(), any()) } returns "OK"
        val repository = buildRepository()

        val result = repository.checkCloudApiHealth()

        assertTrue(result.isHealthy)
        coVerify(exactly = 0) { primaryNanoModel.generateText(any(), any()) }
        coVerify(exactly = 1) { primaryCloudModel.generateText("Reply with exactly: OK", true) }
    }

    @Test
    fun `smartSearchQuery forces cloud even though Nano is ready and active`() = runTest {
        coEvery { proCloudModel.generateText(any(), any()) } returns """["Matrix", "Sci-Fi"]"""
        val repository = buildRepository()

        val keywords = repository.smartSearchQuery("find me a sci-fi movie")

        assertTrue(keywords.isNotEmpty())
        coVerify(exactly = 0) { proNanoModel.generateText(any(), any()) }
        coVerify(exactly = 1) { proCloudModel.generateText(any(), eq(true)) }
    }
}
