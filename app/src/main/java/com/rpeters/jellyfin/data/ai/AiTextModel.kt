package com.rpeters.jellyfin.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Minimal abstraction for text-only generation across on-device and cloud backends.
 */
interface AiTextModel {
    suspend fun generateText(prompt: String): String
    fun generateTextStream(prompt: String): Flow<String>
}
