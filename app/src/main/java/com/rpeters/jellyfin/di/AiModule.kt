package com.rpeters.jellyfin.di

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.rpeters.jellyfin.data.ai.AiBackendStateHolder
import com.rpeters.jellyfin.data.ai.AiTextModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Named
import javax.inject.Singleton
import com.google.mlkit.genai.prompt.GenerativeModel as MlKitGenerativeModel

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    private val nanoDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var nanoClientInstance: MlKitGenerativeModel? = null

    @Volatile
    private var stateHolderInstance: AiBackendStateHolder? = null

    /**
     * Result of checking Nano availability
     */
    private data class NanoAvailabilityResult(
        val isAvailable: Boolean,
        val statusMessage: String,
        val canRetry: Boolean,
        val errorCode: Int? = null,
    )

    /**
     * Provides the primary GenerativeModel with smart fallback:
     * 1. Tries Gemini Nano (on-device) if available
     * 2. Falls back to Gemini 2.5 Flash (cloud API) if Nano unavailable
     */
    @Provides
    @Singleton
    @Named("primary-model")
    fun providePrimaryModel(stateHolder: AiBackendStateHolder): AiTextModel {
        val nanoClient = createNanoClient()
        val cloud = FirebaseAiTextModel(createCloudFlashModel())
        val nano = MlKitNanoTextModel(nanoClient, stateHolder)

        // Store instances for retry functionality
        nanoClientInstance = nanoClient
        stateHolderInstance = stateHolder

        // Initialize asynchronously to avoid blocking app startup
        nanoDownloadScope.launch {
            val result = checkNanoAvailability(nanoClient, stateHolder)
            stateHolder.update(
                isUsingNano = result.isAvailable,
                nanoStatus = result.statusMessage,
                canRetryDownload = result.canRetry,
                errorCode = result.errorCode,
            )
            if (!result.isAvailable) {
                Log.d("AiModule", "Gemini Nano unavailable, using cloud API: ${result.statusMessage}")
            }
        }

        return DelegatingAiTextModel(
            nano = nano,
            cloud = cloud,
            stateHolder = stateHolder,
            nanoClient = nanoClient,
        )
    }

    /**
     * Retry downloading Gemini Nano model if previous attempt failed
     */
    fun retryNanoDownload() {
        val client = nanoClientInstance
        val holder = stateHolderInstance
        if (client != null && holder != null) {
            downloadStarted.set(false)
            nanoDownloadScope.launch {
                holder.update(
                    isUsingNano = false,
                    nanoStatus = "Retrying...",
                    canRetryDownload = false,
                )
                val result = checkNanoAvailability(client, holder)
                holder.update(
                    isUsingNano = result.isAvailable,
                    nanoStatus = result.statusMessage,
                    canRetryDownload = result.canRetry,
                    errorCode = result.errorCode,
                )
            }
        }
    }

    /**
     * Provides a cloud-based pro model for complex reasoning tasks
     */
    @Provides
    @Singleton
    @Named("pro-model")
    fun provideProModel(): AiTextModel {
        // Note: API key should be configured in Firebase project settings
        // or via google-services.json for cloud models
        return FirebaseAiTextModel(
            Firebase.ai.generativeModel(
                modelName = "gemini-2.5-flash",
                generationConfig = generationConfig {
                    temperature = 0.8f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 4096
                },
            ),
        )
    }

    private fun createNanoClient(): MlKitGenerativeModel {
        return Generation.getClient()
    }

    private fun createCloudFlashModel(): GenerativeModel {
        // Note: API key should be configured in Firebase project settings
        // or via google-services.json for cloud models
        return Firebase.ai.generativeModel(
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048
            },
        )
    }

    /**
     * Checks if Gemini Nano is actually available on this device
     */
    private suspend fun checkNanoAvailability(
        model: MlKitGenerativeModel,
        stateHolder: AiBackendStateHolder,
    ): NanoAvailabilityResult {
        return withContext(Dispatchers.IO) {
            try {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE ->
                        NanoAvailabilityResult(true, "Ready", false)
                    FeatureStatus.DOWNLOADABLE -> {
                        startNanoDownload(model, stateHolder)
                        NanoAvailabilityResult(false, "Starting download...", false)
                    }
                    FeatureStatus.DOWNLOADING ->
                        NanoAvailabilityResult(false, "Downloading...", false)
                    FeatureStatus.UNAVAILABLE ->
                        NanoAvailabilityResult(false, "Not available on this device", false)
                    else ->
                        NanoAvailabilityResult(false, "Status unknown", false)
                }
            } catch (e: GenAiException) {
                Log.e("AiModule", "Nano availability check failed", e)
                handleGenAiException(e)
            } catch (e: Exception) {
                Log.e("AiModule", "Unexpected error checking Nano availability", e)
                NanoAvailabilityResult(false, "Error: ${e.message}", true)
            }
        }
    }

    /**
     * Handles specific GenAI error codes with user-friendly messages
     * Error codes based on ML Kit GenAI documentation:
     * 606 = FEATURE_NOT_FOUND (config not ready or bootloader unlocked)
     * 601 = BUSY (quota exceeded)
     * 603 = BACKGROUND_USE_BLOCKED
     * 605 = DOWNLOAD_FAILED
     */
    private fun handleGenAiException(e: GenAiException): NanoAvailabilityResult {
        val errorCode = e.errorCode
        return when (errorCode) {
            606 -> {
                // FEATURE_NOT_FOUND: Device config not fetched or bootloader unlocked
                Log.w("AiModule", "Error 606: AICore config not ready or bootloader unlocked")
                NanoAvailabilityResult(
                    isAvailable = false,
                    statusMessage = "Initializing (restart may help)",
                    canRetry = true,
                    errorCode = 606,
                )
            }
            601 -> {
                // BUSY: Quota exceeded - implement backoff
                Log.w("AiModule", "Error 601: AI quota exceeded, using cloud fallback")
                NanoAvailabilityResult(
                    isAvailable = false,
                    statusMessage = "AI busy - using cloud",
                    canRetry = true,
                    errorCode = 601,
                )
            }
            603 -> {
                // BACKGROUND_USE_BLOCKED
                Log.w("AiModule", "Error 603: Background AI usage blocked")
                NanoAvailabilityResult(
                    isAvailable = false,
                    statusMessage = "Must be in foreground",
                    canRetry = false,
                    errorCode = 603,
                )
            }
            605 -> {
                // DOWNLOAD_FAILED
                Log.e("AiModule", "Error 605: Model download failed")
                NanoAvailabilityResult(
                    isAvailable = false,
                    statusMessage = "Download failed",
                    canRetry = true,
                    errorCode = 605,
                )
            }
            else -> {
                Log.e("AiModule", "GenAI error $errorCode: ${e.message}")
                NanoAvailabilityResult(
                    isAvailable = false,
                    statusMessage = "Error: $errorCode",
                    canRetry = true,
                    errorCode = errorCode,
                )
            }
        }
    }

    private fun startNanoDownload(model: MlKitGenerativeModel, stateHolder: AiBackendStateHolder) {
        if (!downloadStarted.compareAndSet(false, true)) return
        nanoDownloadScope.launch {
            try {
                model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted -> {
                            stateHolder.update(
                                isUsingNano = false,
                                nanoStatus = "Download started",
                                isDownloading = true,
                                downloadBytesProgress = "Starting...",
                                canRetryDownload = false,
                            )
                        }
                        is DownloadStatus.DownloadProgress -> {
                            val downloadedMB = status.totalBytesDownloaded / (1024f * 1024f)
                            stateHolder.update(
                                isUsingNano = false,
                                nanoStatus = "Downloading on-device AI model",
                                isDownloading = true,
                                downloadBytesProgress = "%.1f MB downloaded".format(downloadedMB),
                                canRetryDownload = false,
                            )
                        }
                        DownloadStatus.DownloadCompleted -> {
                            downloadStarted.set(false)
                            stateHolder.update(
                                isUsingNano = true,
                                nanoStatus = "Ready - using on-device AI",
                                isDownloading = false,
                                downloadBytesProgress = null,
                                canRetryDownload = false,
                            )
                            Log.i("AiModule", "Gemini Nano download completed successfully")
                        }
                        is DownloadStatus.DownloadFailed -> {
                            downloadStarted.set(false)
                            val errorMsg = status.e.message ?: "Unknown error"
                            stateHolder.update(
                                isUsingNano = false,
                                nanoStatus = "Download failed",
                                isDownloading = false,
                                downloadBytesProgress = null,
                                canRetryDownload = true,
                            )
                            Log.e("AiModule", "Gemini Nano download failed: $errorMsg", status.e)
                        }
                    }
                }
            } catch (e: Exception) {
                downloadStarted.set(false)
                stateHolder.update(
                    isUsingNano = false,
                    nanoStatus = "Download error",
                    isDownloading = false,
                    downloadBytesProgress = null,
                    canRetryDownload = true,
                )
                Log.e("AiModule", "Exception during Nano download", e)
            }
        }
    }

    private class FirebaseAiTextModel(
        private val model: GenerativeModel,
    ) : AiTextModel {
        override suspend fun generateText(prompt: String): String {
            return model.generateContent(prompt).text ?: ""
        }

        override fun generateTextStream(prompt: String): Flow<String> {
            return model.generateContentStream(prompt)
                .mapNotNull { it.text }
        }
    }

    private class MlKitNanoTextModel(
        private val model: MlKitGenerativeModel,
        private val stateHolder: AiBackendStateHolder,
    ) : AiTextModel {
        override suspend fun generateText(prompt: String): String {
            return try {
                val response = model.generateContent(prompt)
                response.candidates.firstOrNull()?.text.orEmpty()
            } catch (e: Exception) {
                Log.e("AiModule", "Nano generation failed", e)
                stateHolder.update(
                    isUsingNano = false,
                    nanoStatus = "Error during generation",
                    canRetryDownload = false,
                )
                ""
            }
        }

        override fun generateTextStream(prompt: String): Flow<String> {
            return model.generateContentStream(prompt)
                .mapNotNull { it.candidates.firstOrNull()?.text }
        }
    }

    private class DelegatingAiTextModel(
        private val nano: MlKitNanoTextModel,
        private val cloud: AiTextModel,
        private val stateHolder: AiBackendStateHolder,
        private val nanoClient: MlKitGenerativeModel,
    ) : AiTextModel {
        override suspend fun generateText(prompt: String): String {
            val state = stateHolder.state.value
            if (!state.isUsingNano && !state.isDownloading) {
                // Re-check availability in case Nano became available
                val result = checkNanoAvailability(nanoClient, stateHolder)
                stateHolder.update(
                    isUsingNano = result.isAvailable,
                    nanoStatus = result.statusMessage,
                    canRetryDownload = result.canRetry,
                    errorCode = result.errorCode,
                )
                if (!result.isAvailable) {
                    return cloud.generateText(prompt)
                }
            }

            // Use cloud if currently downloading
            return if (state.isDownloading) {
                cloud.generateText(prompt)
            } else {
                nano.generateText(prompt)
            }
        }

        override fun generateTextStream(prompt: String): Flow<String> {
            val state = stateHolder.state.value
            // Use cloud if Nano not ready or currently downloading
            return if (state.isUsingNano && !state.isDownloading) {
                nano.generateTextStream(prompt)
            } else {
                cloud.generateTextStream(prompt)
            }
        }
    }
}
