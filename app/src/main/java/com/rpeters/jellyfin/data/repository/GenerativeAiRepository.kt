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
) {
    private fun logModelUsage(operation: String, usesPrimaryModel: Boolean) {
        val backend = if (usesPrimaryModel) {
            if (backendStateHolder.state.value.isUsingNano) {
                "on-device (Gemini Nano)"
            } else {
                "cloud (Gemini API)"
            }
        } else {
            "cloud (Gemini API)"
        }
        Log.d("GenerativeAi", "AI request [$operation] using $backend")
    }

    /**
     * Simple chat with the model.
     * Uses the primary model (Nano if available, cloud otherwise)
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        logModelUsage("generateResponse", usesPrimaryModel = true)
        try {
            val response = primaryModel.generateText(prompt)
            response.ifBlank { "No response generated." }
        } catch (e: Exception) {
            "Sorry, I encountered an error: ${e.message}"
        }
    }

    /**
     * Stream response for real-time UI updates.
     * Uses the primary model (Nano if available, cloud otherwise)
     */
    fun generateResponseStream(prompt: String): Flow<String> {
        logModelUsage("generateResponseStream", usesPrimaryModel = true)
        return primaryModel.generateTextStream(prompt)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Analyzes a list of user's recent items and suggests a mood or category.
     * Uses the primary model (Nano if available, cloud otherwise)
     */
    suspend fun analyzeViewingHabits(recentItems: List<BaseItemDto>): String = withContext(Dispatchers.IO) {
        if (recentItems.isEmpty()) return@withContext "No viewing history available to analyze."
        logModelUsage("analyzeViewingHabits", usesPrimaryModel = true)

        val itemDescriptions = recentItems.take(10).joinToString("\n") { item ->
            "- ${item.name} (${item.type})"
        }

        val prompt = """
            Based on the following list of recently watched media, describe the user's current viewing mood in one short sentence (e.g., "You're into sci-fi adventures right now!" or "Looks like a comedy weekend.").

            Media List:
            $itemDescriptions
        """.trimIndent()

        try {
            val response = primaryModel.generateText(prompt)
            response.ifBlank { "Enjoying the library!" }
        } catch (e: Exception) {
            "Enjoying the library!"
        }
    }

    /**
     * Smart Search: Translates a natural language query into potential Jellyfin search terms.
     * Returns a list of keywords to search for.
     * Uses the Pro model for better instruction following.
     */
    suspend fun smartSearchQuery(userQuery: String): List<String> = withContext(Dispatchers.IO) {
        logModelUsage("smartSearchQuery", usesPrimaryModel = false)
        val prompt = """
            Translate the following user request into a simple list of 3-5 specific keywords that would work well in a standard media server search engine.
            Ignore filler words. Focus on titles, genres, or key terms.

            User Request: "$userQuery"

            Format: JSON Array of strings. Example: ["Matrix", "Sci-Fi", "Keanu Reeves"]
        """.trimIndent()

        try {
            // Using Pro model for better instruction following on JSON format
            val text = proModel.generateText(prompt).ifBlank { return@withContext listOf(userQuery) }

            // Simple cleanup to extract the array if the model adds markdown code blocks
            val jsonString = text.replace("```json", "").replace("```", "").trim()
            jsonString.trim('[', ']').split(",").map { it.trim().trim('"') }
        } catch (e: Exception) {
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
        logModelUsage("generateRecommendations", usesPrimaryModel = true)
        val itemDescriptions = recentItems.take(10).joinToString("\n") { item ->
            "- ${item.name} (${item.type})"
        }

        val prompt = buildString {
            append("Based on the user's viewing history")
            if (userPreferences != null) {
                append(" and preferences: $userPreferences")
            }
            append(", suggest 3-5 specific titles they might enjoy. Be concise.\n\n")
            append("Viewing History:\n$itemDescriptions")
        }

        try {
            val response = primaryModel.generateText(prompt)
            response.ifBlank { "No recommendations available." }
        } catch (e: Exception) {
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
