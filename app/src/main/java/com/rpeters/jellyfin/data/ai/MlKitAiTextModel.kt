package com.rpeters.jellyfin.data.ai

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.generativeai.GenerativeCompletion
import com.google.mlkit.nl.generativeai.GenerativeCompletionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * States for the On-Device AI model.
 */
enum class AiDownloadState {
    IDLE,
    NOT_SUPPORTED,
    SUPPORTED_NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    FAILED
}

/**
 * ML Kit implementation of AI generation using Gemini Nano (on-device).
 */
class MlKitAiTextModel(
    private val context: Context,
    private val temperature: Float = 0.5f,
    private val maxTokens: Int = 1024
) : AiTextModel {

    private val _downloadState = MutableStateFlow(AiDownloadState.IDLE)
    val downloadState: StateFlow<AiDownloadState> = _downloadState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val options = GenerativeCompletionOptions.builder()
        .setTemperature(temperature)
        .setMaxTokens(maxTokens)
        .build()

    private val client = GenerativeCompletion.getClient(context, options)

    /**
     * Initializes the model: checks availability and triggers download if supported.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (_downloadState.value == AiDownloadState.READY) return@withContext

        try {
            Log.d("MlKitAi", "Checking Gemini Nano status...")
            val status = client.checkFeatureStatus().await()
            
            when (status) {
                GenerativeCompletion.STATUS_AVAILABLE -> {
                    _downloadState.value = AiDownloadState.READY
                    Log.i("MlKitAi", "Gemini Nano is READY")
                }
                GenerativeCompletion.STATUS_DOWNLOADABLE -> {
                    _downloadState.value = AiDownloadState.SUPPORTED_NOT_DOWNLOADED
                    Log.i("MlKitAi", "Gemini Nano is SUPPORTED but needs download")
                    downloadModel()
                }
                GenerativeCompletion.STATUS_DOWNLOADING -> {
                    _downloadState.value = AiDownloadState.DOWNLOADING
                    Log.i("MlKitAi", "Gemini Nano is ALREADY DOWNLOADING")
                }
                GenerativeCompletion.STATUS_UNAVAILABLE -> {
                    _downloadState.value = AiDownloadState.NOT_SUPPORTED
                    Log.w("MlKitAi", "Gemini Nano is NOT SUPPORTED on this device")
                }
                else -> {
                    _downloadState.value = AiDownloadState.FAILED
                }
            }
        } catch (e: Exception) {
            Log.e("MlKitAi", "Failed to check Gemini Nano status", e)
            _downloadState.value = AiDownloadState.FAILED
        }
    }

    /**
     * Triggers the download of the model/adapter.
     */
    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        if (_downloadState.value == AiDownloadState.READY || 
            _downloadState.value == AiDownloadState.DOWNLOADING) return@withContext

        try {
            _downloadState.value = AiDownloadState.DOWNLOADING
            Log.d("MlKitAi", "Starting Gemini Nano download...")
            
            // ML Kit handles the actual download in the background via AICore
            client.downloadFeature().await()
            
            // Re-check status after download "completion"
            val status = client.checkFeatureStatus().await()
            if (status == GenerativeCompletion.STATUS_AVAILABLE) {
                _downloadState.value = AiDownloadState.READY
                Log.i("MlKitAi", "Gemini Nano download completed and READY")
            } else {
                _downloadState.value = AiDownloadState.FAILED
            }
        } catch (e: Exception) {
            Log.e("MlKitAi", "Gemini Nano download failed", e)
            _downloadState.value = AiDownloadState.FAILED
        }
    }

    override suspend fun generateText(prompt: String): String = withContext(Dispatchers.IO) {
        if (_downloadState.value != AiDownloadState.READY) {
            throw IllegalStateException("ML Kit model not ready. Current state: ${_downloadState.value}")
        }
        
        try {
            val result = client.generateContent(prompt).await()
            result.text ?: ""
        } catch (e: Exception) {
            Log.e("MlKitAi", "Error generating text with Nano", e)
            throw e
        }
    }

    override fun generateTextStream(prompt: String): Flow<String> = flow {
        emit(generateText(prompt))
    }
}
