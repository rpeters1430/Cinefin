package com.rpeters.jellyfin.data.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Uses the genai-prompt API (com.google.mlkit:genai-prompt).
 */
class MlKitAiTextModel : AiTextModel {

    companion object {
        private const val TAG = "MlKitAi"
    }

    private val _downloadState = MutableStateFlow(AiDownloadState.IDLE)
    val downloadState: StateFlow<AiDownloadState> = _downloadState.asStateFlow()

    private val client = Generation.getClient()

    // Shared between the primary and pro HybridAiTextModel wrappers, which may both
    // call initialize() around the same time on app startup.
    private val initMutex = Mutex()

    /**
     * Initializes the model: checks availability and triggers download if supported.
     * Suspends until the model is either READY, FAILED, or NOT_SUPPORTED.
     */
    // client.checkStatus() doesn't document a specific thrown exception type for this preview
    // API, and this is a top-level startup guard that must not crash the app on any failure.
    @Suppress("TooGenericExceptionCaught")
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (_downloadState.value == AiDownloadState.READY) return@withContext
        initMutex.withLock {
            if (_downloadState.value == AiDownloadState.READY) return@withLock

            try {
                Log.d(TAG, "Checking Gemini Nano status...")
                val status = client.checkStatus()

                when (status) {
                    FeatureStatus.AVAILABLE -> {
                        _downloadState.value = AiDownloadState.READY
                        Log.i(TAG, "Gemini Nano is READY")
                    }
                    FeatureStatus.DOWNLOADABLE -> {
                        _downloadState.value = AiDownloadState.SUPPORTED_NOT_DOWNLOADED
                        Log.i(TAG, "Gemini Nano is SUPPORTED but needs download")
                        downloadModel()
                    }
                    FeatureStatus.DOWNLOADING -> {
                        Log.i(TAG, "Gemini Nano is ALREADY DOWNLOADING — waiting for completion")
                        _downloadState.value = AiDownloadState.DOWNLOADING
                        collectDownloadFlow()
                    }
                    FeatureStatus.UNAVAILABLE -> {
                        _downloadState.value = AiDownloadState.NOT_SUPPORTED
                        Log.w(TAG, "Gemini Nano is NOT SUPPORTED on this device")
                    }
                    else -> {
                        _downloadState.value = AiDownloadState.FAILED
                        Log.e(TAG, "Unexpected Gemini Nano status: $status")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check Gemini Nano status", e)
                _downloadState.value = AiDownloadState.FAILED
            }
        }
    }

    /**
     * Triggers the download of the model. Suspends until download completes or fails.
     */
    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        if (_downloadState.value == AiDownloadState.READY ||
            _downloadState.value == AiDownloadState.DOWNLOADING) return@withContext

        try {
            _downloadState.value = AiDownloadState.DOWNLOADING
            Log.d(TAG, "Starting Gemini Nano download...")
            collectDownloadFlow()
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Nano download failed", e)
            _downloadState.value = AiDownloadState.FAILED
        }
    }

    private suspend fun collectDownloadFlow() {
        client.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    Log.d(TAG, "Download started, total bytes: ${status.bytesToDownload}")
                }
                is DownloadStatus.DownloadProgress -> {
                    Log.d(TAG, "Download progress: ${status.totalBytesDownloaded} bytes")
                }
                is DownloadStatus.DownloadCompleted -> {
                    _downloadState.value = AiDownloadState.READY
                    Log.i(TAG, "Gemini Nano download completed and READY")
                }
                is DownloadStatus.DownloadFailed -> {
                    _downloadState.value = AiDownloadState.FAILED
                    Log.e(TAG, "Gemini Nano download failed: ${status.e.message}")
                }
            }
        }
    }

    override suspend fun generateText(prompt: String, forceCloud: Boolean): String = withContext(Dispatchers.IO) {
        if (_downloadState.value != AiDownloadState.READY) {
            throw IllegalStateException("ML Kit model not ready. Current state: ${_downloadState.value}")
        }

        try {
            val response = client.generateContent(prompt)
            response.candidates.firstOrNull()?.text ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text with Nano", e)
            throw e
        }
    }

    override fun generateTextStream(prompt: String, forceCloud: Boolean): Flow<String> =
        client.generateContentStream(prompt).map { response ->
            response.candidates.firstOrNull()?.text ?: ""
        }

    fun close() {
        client.close()
    }
}
