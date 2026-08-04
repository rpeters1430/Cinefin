package com.rpeters.jellyfin.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Minimal abstraction for text-only generation across on-device and cloud backends.
 */
interface AiTextModel {
    /**
     * @param forceCloud When true, implementations that can route to an on-device model
     * (e.g. [HybridAiTextModel]) must skip it and use the cloud backend directly.
     * Cloud-only and on-device-only implementations ignore this flag.
     */
    suspend fun generateText(prompt: String, forceCloud: Boolean = false): String
    fun generateTextStream(prompt: String, forceCloud: Boolean = false): Flow<String>
}
