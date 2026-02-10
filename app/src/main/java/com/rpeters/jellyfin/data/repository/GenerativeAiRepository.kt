package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.rpeters.jellyfin.data.ai.AiBackendStateHolder
import com.rpeters.jellyfin.data.ai.AiTextModel
import com.rpeters.jellyfin.di.AiModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Repository for all AI/Gemini functionality.
 *
 * This repository abstracts AI features so they can be used across the app
 * (phone screens, TV screens, etc.) without duplicating logic.
 *
 * The backend automatically uses:
 * - Gemini Nano (on-device) when available for privacy and speed
 * - Gemini API (cloud) as fallback when Nano isn't available
 */
@Singleton
class GenerativeAiRepository @Inject constructor(
    @Named("primary-model") private val primaryModel: AiTextModel,
    @Named("pro-model") private val proModel: AiTextModel,
    private val backendStateHolder: AiBackendStateHolder,
    private val remoteConfig: RemoteConfigRepository,
    private val analytics: com.rpeters.jellyfin.utils.AnalyticsHelper,
) {
    private fun getBackendName(usesPrimaryModel: Boolean): String {
        return if (usesPrimaryModel) {
            if (backendStateHolder.state.value.isUsingNano) "nano" else "cloud"
        } else {
            "pro_cloud"
        }
    }

    private fun logModelUsage(operation: String, usesPrimaryModel: Boolean) {
        val backend = getBackendName(usesPrimaryModel)
        Log.d("GenerativeAi", "AI request [$operation] using $backend")
    }

    private fun isAiEnabled(): Boolean {
        // Default to true if config is missing
        val enabled = try {
            remoteConfig.getBoolean("enable_ai_features")
        } catch (e: Exception) {
            true
        }
        if (!enabled) Log.d("GenerativeAi", "AI features are disabled via Remote Config")
        return enabled
    }

    private fun getPrimaryModel(): AiTextModel {
        return if (remoteConfig.getBoolean("ai_force_pro_model")) {
            Log.v("GenerativeAi", "Using Pro model as primary (Turbo Mode)")
            proModel
        } else {
            primaryModel
        }
    }

    /**
     * Simple chat with the model.
     * Uses the primary model (Nano if available, cloud otherwise)
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isAiEnabled()) return@withContext "AI features are currently disabled."
        logModelUsage("generateResponse", usesPrimaryModel = true)

        val systemPrompt = remoteConfig.getString("ai_chat_system_prompt").takeIf { it.isNotBlank() }
            ?: """
            You are Cinefin AI Assistant.
            Answer the user's request clearly and briefly (max 120 words).
            If recommending media, suggest at most 5 titles.
            """.trimIndent()

        val fullPrompt = """
            $systemPrompt
            
            User request: $prompt
        """.trimIndent()

        try {
            val response = getPrimaryModel().generateText(fullPrompt)
            val success = response.isNotBlank()
            analytics.logAiEvent("chat", success, getBackendName(true))
            response.ifBlank { "No response generated." }
        } catch (e: Exception) {
            analytics.logAiEvent("chat", false, getBackendName(true))
            val message = e.message.orEmpty()
            if (message.contains("MAX_TOKENS", ignoreCase = true)) {
                "I hit a response length limit. Try asking for a shorter answer, like: \"5 movies like Ballerina\"."
            } else {
                "Sorry, I couldn't generate a response right now. Please try again."
            }
        }
    }

    /**
     * Stream response for real-time UI updates.
     * Uses the primary model (Nano if available, cloud otherwise)
     */
    fun generateResponseStream(prompt: String): Flow<String> {
        if (!isAiEnabled()) {
            return kotlinx.coroutines.flow.flowOf("AI features are currently disabled.")
        }
        logModelUsage("generateResponseStream", usesPrimaryModel = true)
        // Note: Tracking success for streams is more complex, logging initiation for now
        analytics.logAiEvent("chat_stream", true, getBackendName(true))
        return getPrimaryModel().generateTextStream(prompt)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Analyzes a list of user's recent items and suggests a mood or category.
     * Uses the primary model (Nano if available, cloud otherwise)
     */
    suspend fun analyzeViewingHabits(recentItems: List<BaseItemDto>): String = withContext(Dispatchers.IO) {
        if (!isAiEnabled()) return@withContext "AI analysis disabled."
        if (recentItems.isEmpty()) return@withContext "No viewing history available to analyze."
        logModelUsage("analyzeViewingHabits", usesPrimaryModel = true)

        val contextSize = remoteConfig.getLong("ai_history_context_size").toInt().coerceAtLeast(1)
        // Fallback to 10 if config is 0 (which is default for missing numbers)
        val finalContextSize = if (contextSize == 0) 10 else contextSize

        val itemDescriptions = recentItems.take(finalContextSize).joinToString("\n") { item ->
            "- ${item.name} (${item.type})"
        }

        val prompt = """
            Based on the following list of recently watched media, describe the user's current viewing mood in one short sentence (e.g., "You're into sci-fi adventures right now!" or "Looks like a comedy weekend.").

            Media List:
            $itemDescriptions
        """.trimIndent()

        try {
            val response = getPrimaryModel().generateText(prompt)
            val success = response.isNotBlank()
            analytics.logAiEvent("mood_analysis", success, getBackendName(true))
            response.ifBlank { "Enjoying the library!" }
        } catch (e: Exception) {
            analytics.logAiEvent("mood_analysis", false, getBackendName(true))
            "Enjoying the library!"
        }
    }

    /**
     * Generates a concise TL;DR summary of content overview.
     * Uses the primary model (Nano if available, cloud otherwise)
     */
    suspend fun generateSummary(title: String, overview: String): String = withContext(Dispatchers.IO) {
        if (!isAiEnabled()) return@withContext overview
        if (overview.isBlank()) return@withContext "No overview available."
        logModelUsage("generateSummary", usesPrimaryModel = true)

        val template = remoteConfig.getString("ai_summary_prompt_template").takeIf { it.isNotBlank() }
            ?: """
            Rewrite this into a fresh, spoiler-free summary in exactly 2 short sentences (max 55 words total).
            Do not copy phrases directly from the overview.

            Title: %s
            Overview: %s

            Focus on the core premise only. Do not reveal twists or endings.
            """.trimIndent()

        // Handle simple string formatting if the user includes %s placeholders, otherwise append
        val prompt = if (template.contains("%s")) {
            try {
                template.format(title, overview)
            } catch (e: Exception) {
                // Fallback if formatting fails (e.g. mismatch count)
                """
                $template
                
                Title: $title
                Overview: $overview
                """.trimIndent()
            }
        } else {
            """
            $template
            
            Title: $title
            Overview: $overview
            """.trimIndent()
        }

        try {
            val response = getPrimaryModel().generateText(prompt)
            val success = response.isNotBlank()
            analytics.logAiEvent("summary", success, getBackendName(true))
            response.ifBlank { "Unable to generate summary." }
        } catch (e: Exception) {
            analytics.logAiEvent("summary", false, getBackendName(true))
            "Unable to generate summary right now."
        }
    }

    /**
     * Smart Search: Translates a natural language query into potential Jellyfin search terms.
     * Returns a list of keywords to search for.
     * Uses the Pro model for better instruction following.
     */
    suspend fun smartSearchQuery(userQuery: String): List<String> = withContext(Dispatchers.IO) {
        if (!isAiEnabled()) return@withContext listOf(userQuery)
        logModelUsage("smartSearchQuery", usesPrimaryModel = false)

        val keywordLimit = remoteConfig.getLong("ai_search_keyword_limit").toInt()
        val finalLimit = if (keywordLimit <= 0) 5 else keywordLimit

        val prompt = """
            Translate the following user request into a simple list of 3-$finalLimit specific keywords that would work well in a standard media server search engine.
            Ignore filler words. Focus on titles, genres, or key terms.

            User Request: "$userQuery"

            Format: JSON Array of strings. Example: ["Matrix", "Sci-Fi", "Keanu Reeves"]
        """.trimIndent()

        try {
            // Using Pro model for better instruction following on JSON format
            val text = proModel.generateText(prompt)
            val success = text.isNotBlank()
            analytics.logAiEvent("smart_search", success, getBackendName(false))

            if (text.isBlank()) return@withContext listOf(userQuery)

            // Simple cleanup to extract the array if the model adds markdown code blocks
            val jsonString = text.replace("```json", "").replace("```", "").trim()
            val keywords = jsonString.trim('[', ']')
                .split(",")
                .map { it.trim().trim('"') }
                .filter { it.isNotBlank() }
                .distinct()
                .take(finalLimit)

            if (keywords.isEmpty()) listOf(userQuery) else keywords
        } catch (e: Exception) {
            analytics.logAiEvent("smart_search", false, getBackendName(false))
            listOf(userQuery)
        }
    }

    /**
     * Generates personalized movie/show recommendations based on user preferences.
     * This can be called from any screen (Home, TV, Search, etc.)
     */
    suspend fun generateRecommendations(
        recentItems: List<BaseItemDto>,
        userPreferences: String? = null,
    ): String = withContext(Dispatchers.IO) {
        if (!isAiEnabled()) return@withContext "AI recommendations disabled."
        logModelUsage("generateRecommendations", usesPrimaryModel = true)

        val contextSize = remoteConfig.getLong("ai_history_context_size").toInt().coerceAtLeast(1)
        val finalContextSize = if (contextSize == 0) 10 else contextSize

        val recCount = remoteConfig.getLong("ai_recommendation_count").toInt().coerceAtLeast(1)
        val finalRecCount = if (recCount == 0) 5 else recCount

        val itemDescriptions = recentItems.take(finalContextSize).joinToString("\n") { item ->
            "- ${item.name} (${item.type})"
        }

        val prompt = buildString {
            append("Based on the user's viewing history")
            if (userPreferences != null) {
                append(" and preferences: $userPreferences")
            }
            append(", suggest $finalRecCount specific titles they might enjoy. Be concise.\n\n")
            append("Viewing History:\n$itemDescriptions")
        }

        try {
            val response = getPrimaryModel().generateText(prompt)
            val success = response.isNotBlank()
            analytics.logAiEvent("recommendations", success, getBackendName(true))
            response.ifBlank { "No recommendations available." }
        } catch (e: Exception) {
            analytics.logAiEvent("recommendations", false, getBackendName(true))
            "Unable to generate recommendations at this time."
        }
    }

    /**
     * Generates a natural language summary of a media item.
     * Useful for TV screens where reading long descriptions is difficult.
     */
    suspend fun summarizeItem(item: BaseItemDto): String = withContext(Dispatchers.IO) {
        logModelUsage("summarizeItem", usesPrimaryModel = true)
        val prompt = """
            Summarize this ${item.type} in one concise sentence:
            Title: ${item.name}
            ${item.overview?.let { "Description: $it" } ?: ""}
        """.trimIndent()

        try {
            val response = primaryModel.generateText(prompt)
            response.ifBlank { item.overview ?: "No description available." }
        } catch (e: Exception) {
            item.overview ?: "No description available."
        }
    }

    /**
     * Returns whether the current model is using on-device AI (Gemini Nano)
     * This can be used to show a privacy badge in the UI
     */
    fun isUsingOnDeviceAI(): Boolean = backendStateHolder.state.value.isUsingNano

    /**
     * Retry downloading the Gemini Nano model if a previous download failed
     */
    fun retryNanoDownload() {
        Log.d("GenerativeAi", "User initiated Nano download retry")
        AiModule.retryNanoDownload()
    }

    /**
     * Analyzes an image (e.g., movie poster) and provides insights.
     *
     * **Multimodal Support:**
     * - Samsung S25 series: Uses on-device multimodal Gemini Nano (text + images)
     * - Other devices: Uses cloud API with multimodal support
     *
     * **Future Use Cases:**
     * - Analyze movie posters to suggest similar content
     * - Describe scenes from video thumbnails
     * - Visual search: "Find movies with this vibe"
     *
     * @param imageUri URI to the image (can be content://, file://, or http://)
     * @param prompt The question or analysis request about the image
     * @return AI-generated description or analysis of the image
     *
     * **Note**: This method is prepared for future implementation when you want to add
     * image analysis features. Currently commented out to avoid unused code warnings.
     */
    /*
    suspend fun analyzeImage(imageUri: Uri, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            // Load the image as a Bitmap
            val bitmap = loadBitmapFromUri(imageUri)

            // Create content with both text and image
            val content = content {
                text(prompt)
                image(bitmap)
            }

            // Use primary model (Nano on S25, cloud otherwise)
            val response = primaryModel.generateContent(content)
            response.text ?: "Unable to analyze image."
        } catch (e: Exception) {
            "Error analyzing image: ${e.message}"
        }
    }

    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap {
        // Implementation would use ContentResolver or Coil to load bitmap
        // This is a placeholder for when you implement multimodal features
        TODO("Implement bitmap loading from URI")
    }
     */
}
