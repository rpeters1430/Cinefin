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
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
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
    fun providePrimaryModel(
        stateHolder: AiBackendStateHolder,
        remoteConfig: RemoteConfigRepository,
    ): AiTextModel {
        val modelName = remoteConfig.getString("ai_primary_model_name").takeIf { it.isNotBlank() }
            ?: "gemini-2.5-flash"

        val nanoClient = createNanoClient()
        val cloud = FirebaseAiTextModel(createCloudModel(modelName, temperature = 0.7f, maxTokens = 2048))
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
                Log.d("AiModule", "Gemini Nano unavailable, using cloud API ($modelName): ${result.statusMessage}")
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
    fun provideProModel(remoteConfig: RemoteConfigRepository): AiTextModel {
        val modelName = remoteConfig.getString("ai_pro_model_name").takeIf { it.isNotBlank() }
            ?: "gemini-2.5-flash"

        return FirebaseAiTextModel(
            createCloudModel(
                modelName = modelName,
                temperature = 0.8f,
                maxTokens = 4096,
            ),
        )
    }

    private fun createNanoClient(): MlKitGenerativeModel {
        return Generation.getClient()
    }

    private fun createCloudModel(
        modelName: String,
        temperature: Float,
        maxTokens: Int,
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                this.temperature = temperature
                topK = 40
                topP = 0.95f
                maxOutputTokens = maxTokens
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
                    FeatureStatus.AVAILABLE -> {
                        // Verify Nano actually works with a simple test generation
                        // This prevents ErrorCode 4 issues where checkStatus() returns AVAILABLE
                        // but the model isn't actually ready
                        verifyNanoWorks(model)
                    }
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
            }
        }
    }

    /**
     * Verifies that Nano can actually generate content (not just report as available)
     * This catches ErrorCode 4 and other initialization issues.
     *
     * Uses a realistic test that mimics actual app usage to catch policy/safety issues.
     */
    private suspend fun verifyNanoWorks(model: MlKitGenerativeModel): NanoAvailabilityResult {
        return try {
            // Realistic test prompt that mimics actual viewing habits analysis
            // This catches ErrorCode 4 policy issues that a simple "Say OK" test misses
            val testPrompt = """
                Based on the following list of recently watched media, describe the viewing mood in one sentence:
                - The Matrix (Movie)
                - Inception (Movie)
                - Breaking Bad (Series)
            """.trimIndent()

            val response = model.generateContent(testPrompt)
            val hasContent = response.candidates.firstOrNull()?.text?.isNotBlank() == true

            if (hasContent) {
                Log.i("AiModule", "Nano verification successful - model is ready")
                NanoAvailabilityResult(true, "Ready", false)
            } else {
                Log.w("AiModule", "Nano verification failed - empty response")
                NanoAvailabilityResult(false, "Not ready - using cloud", false, errorCode = null)
            }
        } catch (e: GenAiException) {
            Log.w("AiModule", "Nano verification failed with error ${e.errorCode}: ${e.message}")
            // Handle specific error codes
            when (e.errorCode) {
                4 -> {
                    // REQUEST_PROCESSING_ERROR - strict policy/safety checks blocking prompts
                    // This is common on Pixel devices with Gemini Nano
                    Log.w("AiModule", "Nano has strict policy checks preventing usage (ErrorCode 4) - using cloud fallback")
                    NanoAvailabilityResult(false, "Safety filters too strict - using cloud", false, errorCode = 4)
                }
                else -> handleGenAiException(e)
            }
        } catch (e: Exception) {
            Log.w("AiModule", "Nano verification failed: ${e.message}")
            NanoAvailabilityResult(false, "Verification failed - using cloud", false)
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
            } catch (e: GenAiException) {
                Log.e("AiModule", "Nano generation failed: ${e.message}", e)
                // Handle ErrorCode 4 specially - it means policy/safety filters are blocking content
                // This is permanent and won't be fixed by retrying
                val (statusMessage, errorCode) = when (e.errorCode) {
                    4 -> {
                        Log.w("AiModule", "Nano blocked by safety filters (ErrorCode 4) - permanently using cloud")
                        "Safety filters blocking content" to 4
                    }
                    else -> "Error during generation - using cloud" to e.errorCode
                }

                stateHolder.update(
                    isUsingNano = false,
                    nanoStatus = statusMessage,
                    canRetryDownload = false,
                    errorCode = errorCode,
                )
                // Re-throw so DelegatingAiTextModel falls back to cloud
                throw e
            } catch (e: Exception) {
                Log.e("AiModule", "Nano generation failed: ${e.message}", e)
                stateHolder.update(
                    isUsingNano = false,
                    nanoStatus = "Error during generation - using cloud",
                    canRetryDownload = false,
                )
                throw e
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
        @Volatile
        private var lastNanoCheckTime: Long = 0L
        private val nanoRecheckIntervalMs = 5 * 60 * 1000L // 5 minutes

        override suspend fun generateText(prompt: String): String {
            val state = stateHolder.state.value
            val now = System.currentTimeMillis()

            // Only re-check Nano availability every 5 minutes to avoid latency on every request
            if (!state.isUsingNano && !state.isDownloading &&
                (now - lastNanoCheckTime) >= nanoRecheckIntervalMs) {
                lastNanoCheckTime = now
                val result = checkNanoAvailability(nanoClient, stateHolder)
                stateHolder.update(
                    isUsingNano = result.isAvailable,
                    nanoStatus = result.statusMessage,
                    canRetryDownload = result.canRetry,
                    errorCode = result.errorCode,
                )
            }

            // Try Nano if available, but fall back to cloud on error
            return if (state.isUsingNano && !state.isDownloading) {
                try {
                    val result = nano.generateText(prompt)
                    if (result.isBlank()) {
                        // Nano returned empty - fall back to cloud
                        Log.w("AiModule", "Nano returned empty response, falling back to cloud")
                        cloud.generateText(prompt)
                    } else {
                        result
                    }
                } catch (e: Exception) {
                    // Nano failed - fall back to cloud
                    Log.w("AiModule", "Nano generation failed, falling back to cloud: ${e.message}")
                    cloud.generateText(prompt)
                }
            } else {
                // Use cloud if Nano not ready or currently downloading
                cloud.generateText(prompt)
            }
        }

        override fun generateTextStream(prompt: String): Flow<String> {
            val state = stateHolder.state.value
            // Try Nano if available, but use cloud as fallback
            return if (state.isUsingNano && !state.isDownloading) {
                // Note: For streaming, we can't catch errors mid-stream easily,
                // so we rely on the caller to handle errors. The state will be updated
                // if Nano fails, and subsequent calls will use cloud.
                nano.generateTextStream(prompt)
            } else {
                cloud.generateTextStream(prompt)
            }
        }
    }
}
