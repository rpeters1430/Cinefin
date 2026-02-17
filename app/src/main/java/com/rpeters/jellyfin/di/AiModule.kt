package com.rpeters.jellyfin.di

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.generationConfig
import com.rpeters.jellyfin.data.ai.AiTextModel
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
    private const val BASELINE_MODEL = "gemini-2.5-flash"

    @Provides
    @Singleton
    @Named("primary-model")
    fun providePrimaryModel(remoteConfig: RemoteConfigRepository): AiTextModel {
        return FirebaseAiTextModel(
            remoteConfig = remoteConfig,
            modelNameKey = "ai_primary_model_name",
            temperatureKey = "ai_primary_model_temperature",
            maxTokensKey = "ai_primary_model_max_tokens",
            label = "primary",
        )
    }

    @Provides
    @Singleton
    @Named("pro-model")
    fun provideProModel(remoteConfig: RemoteConfigRepository): AiTextModel {
        return FirebaseAiTextModel(
            remoteConfig = remoteConfig,
            modelNameKey = "ai_pro_model_name",
            temperatureKey = "ai_pro_model_temperature",
            maxTokensKey = "ai_pro_model_max_tokens",
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

        private fun getModel() = Firebase.ai(
            backend = GenerativeBackend.googleAI(),
        ).generativeModel(
            modelName = remoteConfig.getString(modelNameKey).ifBlank { BASELINE_MODEL },
            generationConfig = generationConfig {
                temperature = remoteConfig.getDouble(temperatureKey).toFloat().coerceIn(0f, 2f)
                topK = 40
                topP = 0.95f
                maxOutputTokens = remoteConfig.getLong(maxTokensKey).toInt().coerceAtLeast(1)
            },
            requestOptions = RequestOptions(timeoutInMillis = 30_000L),
        )

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
