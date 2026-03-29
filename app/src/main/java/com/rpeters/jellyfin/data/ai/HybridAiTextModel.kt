package com.rpeters.jellyfin.data.ai

import android.util.Log
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.atomic.AtomicInteger

/**
 * A hybrid AI model that prioritizes on-device Gemini Nano (via ML Kit)
 * but falls back to cloud Gemini (via Firebase AI) when unavailable.
 * Includes a "Circuit Breaker" to permanently fallback to cloud if Nano is flaky.
 */
class HybridAiTextModel(
    private val remoteConfig: RemoteConfigRepository,
    private val cloudModel: AiTextModel,
    private val label: String,
) : AiTextModel {

    private val nanoModel = MlKitAiTextModel()
    private val failureCount = AtomicInteger(0)
    private var circuitBroken = false

    val downloadState: StateFlow<AiDownloadState> = nanoModel.downloadState

    private val _isNanoActive = MutableStateFlow(false)
    val isNanoActive: StateFlow<Boolean> = _isNanoActive.asStateFlow()

    /**
     * Checks if Nano is available and starts download if necessary.
     */
    suspend fun initialize() {
        if (remoteConfig.getBoolean("enable_on_device_ai")) {
            try {
                nanoModel.initialize()
                updateActiveState()
            } catch (e: Exception) {
                Log.e("HybridAi", "[$label] Nano initialization failed: ${e.message}")
            }
        } else {
            Log.d("HybridAi", "[$label] On-Device AI disabled via Remote Config")
        }
    }

    /**
     * Manually triggers a download retry.
     */
    suspend fun retryDownload() {
        circuitBroken = false
        failureCount.set(0)
        nanoModel.downloadModel()
        updateActiveState()
    }

    private fun updateActiveState() {
        _isNanoActive.value = !circuitBroken && 
            remoteConfig.getBoolean("enable_on_device_ai") &&
            nanoModel.downloadState.value == AiDownloadState.READY
    }

    private fun getActiveModel(): AiTextModel {
        updateActiveState()
        return if (_isNanoActive.value) nanoModel else cloudModel
    }

    override suspend fun generateText(prompt: String): String {
        val model = getActiveModel()
        val isNano = model is MlKitAiTextModel
        
        return try {
            val result = model.generateText(prompt)
            if (isNano) failureCount.set(0) // Reset on success
            result
        } catch (e: Exception) {
            if (isNano) {
                val currentFailures = failureCount.incrementAndGet()
                Log.w("HybridAi", "[$label] Nano failed ($currentFailures/3), falling back to cloud", e)
                
                if (currentFailures >= 3) {
                    Log.e("HybridAi", "[$label] Circuit broken for Nano! Switching to Cloud for this session.")
                    circuitBroken = true
                    updateActiveState()
                }
                
                // Immediate fallback to cloud for this request
                cloudModel.generateText(prompt)
            } else {
                Log.e("HybridAi", "[$label] Cloud AI failed: ${e.message}")
                throw e
            }
        }
    }

    override fun generateTextStream(prompt: String): Flow<String> {
        val model = getActiveModel()
        val isNano = model is MlKitAiTextModel
        
        return if (isNano) {
            model.generateTextStream(prompt).catch { e ->
                Log.w("HybridAi", "[$label] Nano stream failed, falling back to cloud", e)
                val currentFailures = failureCount.incrementAndGet()
                if (currentFailures >= 3) {
                    circuitBroken = true
                    updateActiveState()
                }
                // Emit fallback from cloud
                emitAll(cloudModel.generateTextStream(prompt))
            }
        } else {
            cloudModel.generateTextStream(prompt)
        }
    }
}

// Extension to allow emitAll in catch block
private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(flow: Flow<T>) {
    flow.collect { value -> emit(value) }
}
