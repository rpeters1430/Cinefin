package com.rpeters.jellyfin.data.ai

import android.util.Log
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A hybrid AI model that prioritizes on-device Gemini Nano (via ML Kit)
 * but falls back to cloud Gemini (via Firebase AI) when unavailable.
 */
class HybridAiTextModel(
    private val remoteConfig: RemoteConfigRepository,
    private val cloudModel: AiTextModel,
    private val label: String,
) : AiTextModel {

    private val nanoModel = MlKitAiTextModel()

    val downloadState: StateFlow<AiDownloadState> = nanoModel.downloadState

    private val _isNanoActive = MutableStateFlow(false)
    val isNanoActive: StateFlow<Boolean> = _isNanoActive.asStateFlow()

    /**
     * Checks if Nano is available and starts download if necessary.
     * Suspends until the model reaches a terminal state (READY, FAILED, NOT_SUPPORTED).
     */
    suspend fun initialize() {
        if (remoteConfig.getBoolean("enable_on_device_ai")) {
            nanoModel.initialize()
            updateActiveState()
        } else {
            Log.d("HybridAi", "[$label] On-Device AI disabled via Remote Config")
        }
    }

    /**
     * Manually triggers a download retry.
     */
    suspend fun retryDownload() {
        nanoModel.downloadModel()
        updateActiveState()
    }

    private fun updateActiveState() {
        _isNanoActive.value = remoteConfig.getBoolean("enable_on_device_ai") &&
            nanoModel.downloadState.value == AiDownloadState.READY
    }

    private fun getActiveModel(): AiTextModel {
        updateActiveState()
        return if (_isNanoActive.value) nanoModel else cloudModel
    }

    override suspend fun generateText(prompt: String): String {
        val model = getActiveModel()
        val isNano = model is MlKitAiTextModel
        Log.d("HybridAi", "[$label] Generating text using ${if (isNano) "On-Device (Nano)" else "Cloud"}")

        return try {
            model.generateText(prompt)
        } catch (e: Exception) {
            if (isNano) {
                Log.w("HybridAi", "[$label] Nano failed, falling back to cloud", e)
                cloudModel.generateText(prompt)
            } else {
                throw e
            }
        }
    }

    override fun generateTextStream(prompt: String): Flow<String> {
        val model = getActiveModel()
        val isNano = model is MlKitAiTextModel
        Log.d("HybridAi", "[$label] Streaming using ${if (isNano) "On-Device (Nano)" else "Cloud"}")
        return model.generateTextStream(prompt)
    }
}
