package com.rpeters.jellyfin.di

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.generationConfig
import com.rpeters.jellyfin.data.ai.AiTextModel
import com.rpeters.jellyfin.data.ai.HybridAiTextModel
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    private const val TAG = "AiModule"
    private const val BASELINE_MODEL = "gemini-3-flash-preview"

    @Provides
    @Singleton
    @Named("primary-model")
    fun providePrimaryModel(
        remoteConfig: RemoteConfigRepository,
    ): AiTextModel {
        val cloudModel = FirebaseAiTextModel(
            remoteConfig = remoteConfig,
            modelNameKey = "ai_primary_model_name",
            temperatureKey = "ai_primary_model_temperature",
            maxTokensKey = "ai_primary_model_max_tokens",
            label = "primary",
        )
        return HybridAiTextModel(
            remoteConfig = remoteConfig,
            cloudModel = cloudModel,
            label = "primary",
        )
    }

    @Provides
    @Singleton
    @Named("pro-model")
    fun provideProModel(
        remoteConfig: RemoteConfigRepository,
    ): AiTextModel {
        val cloudModel = FirebaseAiTextModel(
            remoteConfig = remoteConfig,
            modelNameKey = "ai_pro_model_name",
            temperatureKey = "ai_pro_model_temperature",
            maxTokensKey = "ai_pro_model_max_tokens",
            label = "pro",
        )
        // Pro model wraps cloud with Nano fallback for consistency
        return HybridAiTextModel(
            remoteConfig = remoteConfig,
            cloudModel = cloudModel,
            label = "pro",
        )
    }

    private class FirebaseAiTextModel(
        private val remoteConfig: RemoteConfigRepository,
        private val modelNameKey: String,
        private val temperatureKey: String,
        private val maxTokensKey: String,
        private val label: String,
    ) : AiTextModel {

        private fun getModel(): com.google.firebase.ai.GenerativeModel {
            val modelName = remoteConfig.getString(modelNameKey).ifBlank { BASELINE_MODEL }
            val temperature = remoteConfig.getDouble(temperatureKey).let {
                if (it <= 0.0) 0.7f else it.toFloat().coerceIn(0f, 2f)
            }
            val maxTokens = remoteConfig.getLong(maxTokensKey).let {
                if (it <= 0L) 2048 else it.toInt().coerceAtMost(8192)
            }
            return Firebase.ai(
                backend = GenerativeBackend.googleAI(),
            ).generativeModel(
                modelName = modelName,
                generationConfig = generationConfig {
                    this.temperature = temperature
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = maxTokens
                },
                requestOptions = RequestOptions(timeoutInMillis = 30_000L),
            )
        }

        override suspend fun generateText(prompt: String): String {
            val model = getModel()
            val currentModelName = remoteConfig.getString(modelNameKey).ifBlank { BASELINE_MODEL }
            Log.i(
                TAG,
                "[AiModule.kt] Firebase AI request source=$label backend=googleAI model=$currentModelName promptChars=${prompt.length} file=app/src/main/java/com/rpeters/jellyfin/di/AiModule.kt",
            )
            return try {
                val response = model.generateContent(prompt)
                val text = response.text.orEmpty()
                Log.i(
                    TAG,
                    "[AiModule.kt] Firebase AI success source=$label backend=googleAI model=$currentModelName responseChars=${text.length}",
                )
                text
            } catch (t: Throwable) {
                Log.e(
                    TAG,
                    "[AiModule.kt] Firebase AI failed source=$label backend=googleAI model=$currentModelName error=${t::class.java.simpleName} message=${t.message}",
                    t,
                )
                throw t
            }
        }

        override fun generateTextStream(prompt: String): Flow<String> {
            val model = getModel()
            val currentModelName = remoteConfig.getString(modelNameKey).ifBlank { BASELINE_MODEL }
            Log.i(
                TAG,
                "[AiModule.kt] Firebase AI stream start source=$label backend=googleAI model=$currentModelName promptChars=${prompt.length}",
            )
            return model.generateContentStream(prompt).map { it.text.orEmpty() }
        }
    }
}
