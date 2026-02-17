package com.rpeters.jellyfin.data.repository

import android.util.Log
import com.rpeters.jellyfin.data.ai.AiTextModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    private val remoteConfig: RemoteConfigRepository,
    private val analytics: com.rpeters.jellyfin.utils.AnalyticsHelper,
) {
    data class AiHealthResult(
        val isHealthy: Boolean,
        val message: String,
    )

    // Simple memory cache for AI summaries to avoid redundant API calls
    private val summaryCache = mutableMapOf<String, String>()

    private fun getBackendName(usesPrimaryModel: Boolean): String {
        return if (usesPrimaryModel) "cloud" else "pro_cloud"
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
     * Minimal cloud API health test for home-screen diagnostics.
     * This is intentionally small/cheap and logs enough context for debugging.
     */
    suspend fun checkCloudApiHealth(): AiHealthResult = withContext(Dispatchers.IO) {
        val prompt = "Reply with exactly: OK"
        val backend = getBackendName(usesPrimaryModel = true)
        Log.i(
            "GenerativeAi",
            "[GenerativeAiRepository.kt] checkCloudApiHealth start backend=$backend promptChars=${prompt.length}",
        )
        return@withContext try {
            val response = withTimeout(15_000L) { getPrimaryModel().generateText(prompt).trim() }
            val isHealthy = response.contains("OK", ignoreCase = true)
            val normalized = response.take(120)
            Log.i(
                "GenerativeAi",
                "[GenerativeAiRepository.kt] checkCloudApiHealth success backend=$backend response='$normalized'",
            )
            analytics.logAiEvent("health_check", isHealthy, backend)
            if (isHealthy) {
                AiHealthResult(true, "AI API connected")
            } else {
                AiHealthResult(false, "Unexpected response: $normalized")
            }
        } catch (e: Exception) {
            Log.e("GenerativeAi", "[GenerativeAiRepository.kt] checkCloudApiHealth failed backend=$backend", e)
            analytics.logAiEvent("health_check", false, backend)
            val message = e.message?.take(140) ?: e::class.simpleName.orEmpty()
            AiHealthResult(false, message.ifBlank { "Unknown AI API error" })
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
        logModelUsage("analyzeViewingHabits", usesPrimaryModel = false)

        val contextSize = remoteConfig.getLong("ai_history_context_size").toInt().coerceAtLeast(1)
        // Fallback to 10 if config is 0 (which is default for missing numbers)
        val finalContextSize = if (contextSize == 0) 10 else contextSize

        // Filter to only Movies and Series to avoid problematic content types
        // and sanitize titles to prevent Gemini Nano safety filter issues
        val filteredItems = recentItems.filter { item ->
            item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE ||
            item.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES
        }

        if (filteredItems.isEmpty()) return@withContext "No movies or shows watched recently."

        val itemDescriptions = filteredItems.take(finalContextSize).joinToString("\n") { item ->
            "- ${item.name} (${item.type})"
        }

        val template = remoteConfig.getString("ai_mood_analysis_prompt_template").takeIf { it.isNotBlank() }
            ?: """
            Based on the following list of recently watched media, describe the user's current viewing mood in one short sentence (e.g., "You're into sci-fi adventures right now!" or "Looks like a comedy weekend.").

            Media List:
            %s
            """.trimIndent()

        val prompt = if (template.contains("%s")) {
            try { template.format(itemDescriptions) } catch (e: Exception) { "$template\n\n$itemDescriptions" }
        } else {
            "$template\n\n$itemDescriptions"
        }

        try {
            // Always use cloud model for user content analysis to avoid Nano safety filter issues
            val response = proModel.generateText(prompt)
            val success = response.isNotBlank()
            analytics.logAiEvent("mood_analysis", success, getBackendName(false))
            response.ifBlank { "Enjoying the library!" }
        } catch (e: Exception) {
            analytics.logAiEvent("mood_analysis", false, getBackendName(false))
            "Enjoying the library!"
        }
    }

    /**
     * Generates a concise TL;DR summary of content overview.
     * Uses the primary model (Nano if available, cloud otherwise)
     * Timeout: 15 seconds to prevent indefinite loading
     */
    suspend fun generateSummary(title: String, overview: String): String = withContext(Dispatchers.IO) {
        if (!isAiEnabled() || !remoteConfig.getBoolean("ai_summaries")) return@withContext overview
        if (overview.isBlank()) return@withContext "No overview available."

        // Check cache first
        val cacheKey = "${title}_${overview.hashCode()}"
        summaryCache[cacheKey]?.let {
            Log.v("GenerativeAi", "Returning cached summary for: $title")
            return@withContext it
        }

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
            // Apply 15-second timeout to prevent indefinite loading
            val response = withTimeout(15_000L) {
                getPrimaryModel().generateText(prompt)
            }
            val success = response.isNotBlank()
            analytics.logAiEvent("summary", success, getBackendName(true))
            val finalResponse = if (response.isBlank()) "Unable to generate summary." else response

            // Store in cache if successful
            if (success) {
                summaryCache[cacheKey] = finalResponse
            }

            finalResponse
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("GenerativeAi", "Summary generation timed out after 15s for: $title")
            analytics.logAiEvent("summary", false, getBackendName(true))
            "Summary generation timed out. Please try again."
        } catch (e: Exception) {
            Log.e("GenerativeAi", "Summary generation failed for: $title", e)
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
        logModelUsage("generateRecommendations", usesPrimaryModel = false)

        val contextSize = remoteConfig.getLong("ai_history_context_size").toInt().coerceAtLeast(1)
        val finalContextSize = if (contextSize == 0) 10 else contextSize

        val recCount = remoteConfig.getLong("ai_recommendation_count").toInt().coerceAtLeast(1)
        val finalRecCount = if (recCount == 0) 5 else recCount

        // Filter to Movies and Series only to avoid problematic content types
        val filteredItems = recentItems.filter { item ->
            item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE ||
            item.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES
        }

        val itemDescriptions = filteredItems.take(finalContextSize).joinToString("\n") { item ->
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
            // Use cloud model for user content to avoid Nano safety filter issues
            val response = proModel.generateText(prompt)
            val success = response.isNotBlank()
            analytics.logAiEvent("recommendations", success, getBackendName(false))
            response.ifBlank { "No recommendations available." }
        } catch (e: Exception) {
            analytics.logAiEvent("recommendations", false, getBackendName(false))
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
     * Generates an AI-powered biography for a person (actor, director, etc.)
     * based on their filmography in the user's library.
     *
     * @param personName The name of the person
     * @param filmography List of movies and TV shows they've appeared in
     * @return AI-generated biography highlighting career, known roles, and library presence
     */
    suspend fun generatePersonBio(
        personName: String,
        filmography: List<BaseItemDto>,
    ): String = withContext(Dispatchers.IO) {
        if (!isAiEnabled() || !remoteConfig.getBoolean("ai_person_bio")) return@withContext ""
        if (filmography.isEmpty()) return@withContext ""
        logModelUsage("generatePersonBio", usesPrimaryModel = true)

        // Build filmography context (limit to most notable/recent items)
        val contextSize = remoteConfig.getLong("ai_person_bio_context_size").toInt()
            .let { if (it <= 0) 15 else it }

        val movieTitles = filmography
            .filter { it.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE }
            .take(contextSize)
            .mapNotNull { it.name }
            .joinToString(", ")

        val tvTitles = filmography
            .filter { it.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES }
            .take(contextSize)
            .mapNotNull { it.name }
            .joinToString(", ")

        val movieCount = filmography.count { it.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE }
        val tvCount = filmography.count { it.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES }

        val template = remoteConfig.getString("ai_person_bio_prompt_template").takeIf { it.isNotBlank() }
            ?: """
            Generate a concise 2-3 sentence biography for %s based on their filmography in this library.

            Context:
            - Total in library: %d movies, %d TV shows
            Notable movies: %s
            Notable TV shows: %s

            Focus on:
            1. Career highlights and known roles (based on titles)
            2. Acting style or typical genres
            3. Presence in this library (%d total appearances)

            Keep it engaging and informative. Max 60 words.
            """.trimIndent()

        val prompt = try {
            template.format(personName, movieCount, tvCount, movieTitles, tvTitles, filmography.size)
        } catch (e: Exception) {
            """
            $template
            Person: $personName
            Movies: $movieTitles
            TV Shows: $tvTitles
            """.trimIndent()
        }

        try {
            val response = withTimeout(15_000L) {
                getPrimaryModel().generateText(prompt)
            }
            val success = response.isNotBlank()
            analytics.logAiEvent("person_bio", success, getBackendName(true))
            response.ifBlank {
                // Fallback bio
                buildString {
                    append("Featured in $movieCount ${if (movieCount == 1) "movie" else "movies"}")
                    if (tvCount > 0) {
                        append(" and $tvCount ${if (tvCount == 1) "show" else "shows"}")
                    }
                    append(" in your library.")
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("GenerativeAi", "Person bio generation timed out for: $personName")
            analytics.logAiEvent("person_bio", false, getBackendName(true))
            ""
        } catch (e: Exception) {
            Log.e("GenerativeAi", "Person bio generation failed for: $personName", e)
            analytics.logAiEvent("person_bio", false, getBackendName(true))
            ""
        }
    }

    /**
     * Extracts thematic elements from content overview.
     * Returns a list of themes like "redemption", "found family", "coming of age", etc.
     *
     * @param title The title of the content
     * @param overview The content overview/description
     * @param genres List of genres (helps guide theme extraction)
     * @return List of theme strings (max 5)
     */
    suspend fun extractThemes(
        title: String,
        overview: String,
        genres: List<String> = emptyList(),
    ): List<String> = withContext(Dispatchers.IO) {
        if (!isAiEnabled() || !remoteConfig.getBoolean("ai_thematic_analysis")) return@withContext emptyList()
        if (overview.isBlank()) return@withContext emptyList()
        logModelUsage("extractThemes", usesPrimaryModel = true)

        val maxThemes = remoteConfig.getLong("ai_theme_extraction_limit").toInt()
            .let { if (it <= 0) 5 else it }

        val genreContext = if (genres.isNotEmpty()) {
            genres.joinToString(", ")
        } else "None"

        val template = remoteConfig.getString("ai_theme_extraction_prompt_template").takeIf { it.isNotBlank() }
            ?: """
            Extract %d thematic elements from this content. Focus on deeper themes beyond just genre.

            Title: %s
            Genres: %s
            Overview: %s

            Examples of themes: "redemption", "found family", "coming of age", "moral ambiguity", "survival", "betrayal", "identity crisis", "power corruption", "revenge", "sacrifice", "forbidden love", "class struggle"

            Return ONLY a JSON array of theme strings, lowercase, 1-3 words each.
            Example: ["redemption", "found family", "survival"]
            """.trimIndent()

        val prompt = try {
            template.format(maxThemes, title, genreContext, overview)
        } catch (e: Exception) {
            """
            $template
            Title: $title
            Genres: $genreContext
            Overview: $overview
            """.trimIndent()
        }

        try {
            val response = withTimeout(10_000L) {
                getPrimaryModel().generateText(prompt)
            }
            val success = response.isNotBlank()
            analytics.logAiEvent("theme_extraction", success, getBackendName(true))

            if (response.isBlank()) return@withContext emptyList()

            // Parse JSON array
            val jsonString = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
                .trim('[', ']')

            val themes = jsonString
                .split(",")
                .map { it.trim().trim('"').lowercase() }
                .filter { it.isNotBlank() && it.length >= 3 }
                .distinct()
                .take(maxThemes)

            themes
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("GenerativeAi", "Theme extraction timed out for: $title")
            analytics.logAiEvent("theme_extraction", false, getBackendName(true))
            emptyList()
        } catch (e: Exception) {
            Log.e("GenerativeAi", "Theme extraction failed for: $title", e)
            analytics.logAiEvent("theme_extraction", false, getBackendName(true))
            emptyList()
        }
    }

    /**
     * Generates a personalized "Why You'll Love This" pitch for a movie/show.
     * Analyzes user's viewing history to find connections and similarities.
     *
     * @param item The movie/show to generate a pitch for
     * @param viewingHistory User's recent viewing history (max 20 items)
     * @return Personalized pitch explaining why the user would enjoy this content
     */
    suspend fun generateWhyYoullLoveThis(
        item: BaseItemDto,
        viewingHistory: List<BaseItemDto>,
    ): String = withContext(Dispatchers.IO) {
        if (!isAiEnabled() || !remoteConfig.getBoolean("ai_why_youll_love_this")) return@withContext ""
        if (viewingHistory.isEmpty()) return@withContext ""
        logModelUsage("whyYoullLoveThis", usesPrimaryModel = false)

        val historySize = remoteConfig.getLong("ai_history_context_size").toInt()
            .let { if (it <= 0) 10 else it }

        // Filter to Movies and Series only
        val filteredHistory = viewingHistory.filter {
            it.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE ||
            it.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES
        }

        // Build viewing history context
        val recentTitles = filteredHistory.take(historySize)
            .mapNotNull { it.name }
            .joinToString(", ")

        val itemTitle = item.name ?: "this content"
        val itemGenres = item.genres?.joinToString(", ") ?: ""
        val itemOverview = item.overview?.take(300) ?: ""

        val template = remoteConfig.getString("ai_why_love_this_prompt_template").takeIf { it.isNotBlank() }
            ?: """
            Based on the user's viewing history, explain why they would enjoy "%s" in ONE compelling sentence (max 40 words).

            Their recent watches: %s

            About "%s":
            - Genres: %s
            - Overview: %s

            Find connections to their history (similar themes, genres, actors, tone, storytelling style).

            Format: "You loved [Title] and [Title] - this has the same [specific quality]."

            Be specific and engaging. Focus on WHY, not just WHAT.
            """.trimIndent()

        val prompt = try {
            template.format(itemTitle, recentTitles, itemTitle, itemGenres, itemOverview)
        } catch (e: Exception) {
            """
            $template
            Title: $itemTitle
            History: $recentTitles
            """.trimIndent()
        }

        try {
            // Use cloud model for user content to avoid Nano safety filter issues
            // Increased timeout to 30s for Firebase AI API (can be slow on first calls)
            val response = withTimeout(30_000L) {
                proModel.generateText(prompt)
            }
            val success = response.isNotBlank()
            analytics.logAiEvent("why_youll_love_this", success, getBackendName(false))
            response.trim().takeIf { it.isNotBlank() } ?: ""
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("GenerativeAi", "Why you'll love this timed out for: $itemTitle")
            analytics.logAiEvent("why_youll_love_this", false, getBackendName(false))
            ""
        } catch (e: Exception) {
            Log.e("GenerativeAi", "Why you'll love this failed for: $itemTitle", e)
            analytics.logAiEvent("why_youll_love_this", false, getBackendName(false))
            ""
        }
    }

    /**
     * Generates mood-based collections from the user's library.
     * Creates dynamic collections like "Feel-Good Comedies", "Mind-Bending Thrillers", etc.
     *
     * @param library The user's complete library
     * @param currentHour Hour of day (0-23) for time-aware collections
     * @return Map of collection name to list of items
     */
    suspend fun generateMoodCollections(
        library: List<BaseItemDto>,
        currentHour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
    ): Map<String, List<BaseItemDto>> = withContext(Dispatchers.IO) {
        if (!isAiEnabled() || !remoteConfig.getBoolean("ai_mood_collections")) return@withContext emptyMap()
        if (library.isEmpty()) return@withContext emptyMap()
        logModelUsage("moodCollections", usesPrimaryModel = false)

        val maxCollections = remoteConfig.getLong("ai_mood_collections_count").toInt()
            .let { if (it <= 0) 3 else it }

        val itemsPerCollection = remoteConfig.getLong("ai_mood_collection_size").toInt()
            .let { if (it <= 0) 10 else it }

        // Build library context (limit to avoid huge prompts)
        val libraryContext = library.take(100).joinToString("\n") { item ->
            "${item.name} (${item.type}, ${item.genres?.joinToString(", ").orEmpty()})"
        }

        // Time-aware context
        val timeContext = when (currentHour) {
            in 6..11 -> "morning (suggest energizing, light content)"
            in 12..17 -> "afternoon (suggest engaging, varied content)"
            in 18..21 -> "evening (suggest entertaining, relaxing content)"
            else -> "late night (suggest mysteries, thrillers, or comfort content)"
        }

        val template = remoteConfig.getString("ai_mood_collections_prompt_template").takeIf { it.isNotBlank() }
            ?: """
            Based on this library and the current time (%s), create %d mood-based collections.

            Library sample (first 100 items):
            %s

            Create collections with:
            1. Engaging collection names (e.g., "Feel-Good Comedies", "Mind-Bending Thrillers", "Cozy Comfort Shows")
            2. %d items per collection
            3. Time-appropriate suggestions

            Return as JSON:
            {
              "Collection Name 1": ["Title 1", "Title 2", ...],
              "Collection Name 2": ["Title 1", "Title 2", ...],
              ...
            }

            Only include titles that exist in the library above.
            """.trimIndent()

        val prompt = try {
            template.format(timeContext, maxCollections, libraryContext, itemsPerCollection)
        } catch (e: Exception) {
            """
            $template
            Time: $timeContext
            Collections: $maxCollections
            """.trimIndent()
        }

        try {
            // Use cloud model for user content to avoid Nano safety filter issues
            val response = withTimeout(15_000L) {
                proModel.generateText(prompt)
            }
            val success = response.isNotBlank()
            analytics.logAiEvent("mood_collections", success, getBackendName(false))

            if (response.isBlank()) return@withContext emptyMap()

            // Parse JSON response
            val jsonString = response
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // Simple JSON parsing (production would use kotlinx.serialization)
            val collections = mutableMapOf<String, List<BaseItemDto>>()

            // Create a title-to-item lookup for fast matching
            val titleMap = library.associateBy { it.name?.lowercase()?.trim() }

            // Extract collection name and titles (simplified parsing)
            val collectionPattern = """"([^"]+)":\s*\[([^\]]+)\]""".toRegex()
            collectionPattern.findAll(jsonString).forEach { match ->
                val collectionName = match.groupValues[1]
                val titlesString = match.groupValues[2]

                // Extract individual titles
                val titles = """"([^"]+)"""".toRegex()
                    .findAll(titlesString)
                    .map { it.groupValues[1] }
                    .toList()

                // Match titles to actual items
                val items = titles.mapNotNull { title ->
                    titleMap[title.lowercase().trim()]
                }.take(itemsPerCollection)

                if (items.isNotEmpty()) {
                    collections[collectionName] = items
                }
            }

            collections.entries
                .take(maxCollections)
                .associate { (name, items) -> name to items }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("GenerativeAi", "Mood collections generation timed out")
            analytics.logAiEvent("mood_collections", false, getBackendName(false))
            emptyMap()
        } catch (e: Exception) {
            Log.e("GenerativeAi", "Mood collections generation failed", e)
            analytics.logAiEvent("mood_collections", false, getBackendName(false))
            emptyMap()
        }
    }

    /**
     * Generates smart AI-powered recommendations for a movie/show.
     * Analyzes viewing history and library to find thematic matches beyond metadata.
     *
     * This is the HYBRID approach - use alongside Jellyfin's built-in similar items.
     * Jellyfin provides metadata-based matches, AI provides thematic/mood matches.
     *
     * @param currentItem The movie/show to find recommendations for
     * @param viewingHistory User's recent viewing history
     * @param library Full library to recommend from
     * @return List of recommended items (AI-selected from library)
     */
    suspend fun generateSmartRecommendations(
        currentItem: BaseItemDto,
        viewingHistory: List<BaseItemDto>,
        library: List<BaseItemDto>,
    ): List<BaseItemDto> = withContext(Dispatchers.IO) {
        if (!isAiEnabled() || !remoteConfig.getBoolean("ai_smart_recommendations")) return@withContext emptyList()
        if (library.isEmpty()) return@withContext emptyList()
        logModelUsage("smartRecommendations", usesPrimaryModel = false)

        val maxRecommendations = remoteConfig.getLong("ai_smart_recommendations_limit").toInt()
            .let { if (it <= 0) 10 else it }

        val historySize = remoteConfig.getLong("ai_history_context_size").toInt()
            .let { if (it <= 0) 10 else it }

        // Build context
        val currentTitle = currentItem.name ?: "this content"
        val currentGenres = currentItem.genres?.joinToString(", ") ?: ""
        val currentOverview = currentItem.overview?.take(200) ?: ""

        val historyTitles = viewingHistory.take(historySize)
            .mapNotNull { it.name }
            .joinToString(", ")

        // Build library context (sample to avoid huge prompts)
        val libraryContext = library.take(100).joinToString("\n") { item ->
            "${item.name} (${item.type}, ${item.genres?.joinToString(", ").orEmpty()})"
        }

        val template = remoteConfig.getString("ai_smart_recommendations_prompt_template").takeIf { it.isNotBlank() }
            ?: """
            You are a smart content recommendation engine. Find %d titles from the library that the user would enjoy based on thematic similarities, mood, and tone.

            Current item: %s
            Genres: %s
            Overview: %s

            User's recent viewing history: %s

            Available library (sample):
            %s

            Find items that match:
            1. Thematic elements (e.g., similar character arcs, moral dilemmas, storytelling style)
            2. Mood and tone (not just genre)
            3. User's demonstrated preferences from history

            DO NOT recommend the current item itself.

            Return ONLY a JSON array of title strings, in priority order:
            ["Title 1", "Title 2", "Title 3", ...]

            Include ONLY titles that exist in the library above.
            """.trimIndent()

        val prompt = try {
            template.format(maxRecommendations, currentTitle, currentGenres, currentOverview, historyTitles, libraryContext)
        } catch (e: Exception) {
            """
            $template
            Item: $currentTitle
            History: $historyTitles
            """.trimIndent()
        }

        try {
            // Use cloud model for user content to avoid Nano safety filter issues
            // Increased timeout to 30s for Firebase AI API (can be slow on first calls)
            val response = withTimeout(30_000L) {
                proModel.generateText(prompt)
            }
            val success = response.isNotBlank()
            analytics.logAiEvent("smart_recommendations", success, getBackendName(false))

            if (response.isBlank()) return@withContext emptyList()

            // Parse JSON array
            val jsonString = response
                .replace("```json", "")
                .replace("```", "")
                .trim()
                .trim('[', ']')

            // Create title lookup map
            val titleMap = library.associateBy { it.name?.lowercase()?.trim() }

            // Extract and match titles
            val recommendedTitles = """"([^"]+)"""".toRegex()
                .findAll(jsonString)
                .map { it.groupValues[1] }
                .toList()

            val recommendations = recommendedTitles
                .mapNotNull { title -> titleMap[title.lowercase().trim()] }
                .filter { it.id != currentItem.id } // Don't recommend the current item
                .distinct()
                .take(maxRecommendations)

            recommendations
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("GenerativeAi", "Smart recommendations timed out for: $currentTitle")
            analytics.logAiEvent("smart_recommendations", false, getBackendName(false))
            emptyList()
        } catch (e: Exception) {
            Log.e("GenerativeAi", "Smart recommendations failed for: $currentTitle", e)
            analytics.logAiEvent("smart_recommendations", false, getBackendName(false))
            emptyList()
        }
    }

    fun isUsingOnDeviceAI(): Boolean = false
    fun retryNanoDownload() = Unit

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
