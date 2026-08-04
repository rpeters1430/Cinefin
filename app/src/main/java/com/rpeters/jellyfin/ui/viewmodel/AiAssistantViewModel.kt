package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.ai.AiDownloadState
import com.rpeters.jellyfin.data.repository.GenerativeAiRepository
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class AiMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val recommendedItems: List<BaseItemDto> = emptyList(),
)

data class AiAssistantState(
    val messages: List<AiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isOnDeviceAI: Boolean = false,
    val nanoStatus: String = "Checking...",
    val isDownloadingNano: Boolean = false,
    val downloadProgress: String? = null,
    val canRetryDownload: Boolean = false,
    val errorCode: Int? = null, // GenAI error code for specific handling
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val generativeAiRepository: GenerativeAiRepository,
    private val jellyfinRepository: IJellyfinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantState())
    val uiState: StateFlow<AiAssistantState> = _uiState.asStateFlow()

    init {
        // Add welcome message
        val welcomeMessage = AiMessage(
            content = "Hello! I'm your Cinefin AI Assistant. Ask me to find movies, recommend something based on your mood, or just chat about your library!",
            isUser = false,
        )
        _uiState.update { it.copy(messages = listOf(welcomeMessage)) }

        viewModelScope.launch {
            combine(
                generativeAiRepository.downloadState,
                generativeAiRepository.isNanoActive,
            ) { state, active -> state to active }
                .collect { (state, active) ->
                    // Use update {} (atomic read-modify-write) rather than a plain value=
                    // read/copy/write: this collector fires independently of sendMessage()'s
                    // own writes to _uiState, and a plain set here could race with it and
                    // silently drop a concurrent chat-state update (or vice versa).
                    _uiState.update { current ->
                        current.copy(
                            isOnDeviceAI = active,
                            nanoStatus = when {
                                active -> "On-Device (Nano)"
                                state == AiDownloadState.DOWNLOADING -> "Downloading AI Model..."
                                state == AiDownloadState.SUPPORTED_NOT_DOWNLOADED -> "AI Model Needs Download"
                                state == AiDownloadState.FAILED -> "AI Download Failed"
                                else -> "Cloud API"
                            },
                            isDownloadingNano = state == AiDownloadState.DOWNLOADING,
                            canRetryDownload = state == AiDownloadState.FAILED ||
                                state == AiDownloadState.SUPPORTED_NOT_DOWNLOADED,
                        )
                    }
                }
        }
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        val userMessage = AiMessage(content = query, isUser = true)
        _uiState.update { it.copy(messages = it.messages + userMessage, isLoading = true) }

        viewModelScope.launch {
            try {
                // Parallel execution: 1. Get conversational reply, 2. Get search terms
                val chatResponseDeferred = async {
                    generativeAiRepository.generateResponse(query)
                }

                val searchKeywordsDeferred = async {
                    generativeAiRepository.smartSearchQuery(query)
                }

                val chatResponse = chatResponseDeferred.await()
                val searchKeywords = searchKeywordsDeferred.await()

                // Search original query plus AI-generated terms, then merge unique results.
                val searchTerms = buildList {
                    add(query)
                    addAll(searchKeywords)
                }.map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(5)

                val mergedResults = mutableListOf<BaseItemDto>()
                val seenIds = mutableSetOf<String>()
                searchTerms.forEach { term ->
                    when (val result = jellyfinRepository.searchItems(term)) {
                        is ApiResult.Success -> {
                            result.data.forEach { item ->
                                val id = item.id.toString()
                                if (seenIds.add(id)) {
                                    mergedResults += item
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                val aiMessage = AiMessage(
                    content = chatResponse,
                    isUser = false,
                    recommendedItems = mergedResults.take(10),
                )

                _uiState.update { it.copy(messages = it.messages + aiMessage, isLoading = false) }
            } catch (e: Exception) {
                val errorMessage = AiMessage(
                    content = "Sorry, I encountered an error: ${e.message}",
                    isUser = false,
                )
                _uiState.update { it.copy(messages = it.messages + errorMessage, isLoading = false) }
            }
        }
    }

    fun getImageUrl(item: BaseItemDto): String? = jellyfinRepository.getImageUrl(item.id.toString())

    fun retryNanoDownload() {
        viewModelScope.launch {
            generativeAiRepository.retryNanoDownload()
        }
    }
}
