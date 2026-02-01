package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.ai.AiBackendStateHolder
import com.rpeters.jellyfin.data.repository.GenerativeAiRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class AiMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val recommendedItems: List<BaseItemDto> = emptyList()
)

data class AiAssistantState(
    val messages: List<AiMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isOnDeviceAI: Boolean = false,
    val nanoStatus: String = "Checking...",
    val isDownloadingNano: Boolean = false,
    val downloadProgress: String? = null,
    val canRetryDownload: Boolean = false,
    val errorCode: Int? = null  // GenAI error code for specific handling
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val generativeAiRepository: GenerativeAiRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val backendStateHolder: AiBackendStateHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantState())
    val uiState: StateFlow<AiAssistantState> = _uiState.asStateFlow()

    init {
        // Add welcome message
        val welcomeMessage = AiMessage(
            content = "Hello! I'm your Jellyfin AI Assistant. Ask me to find movies, recommend something based on your mood, or just chat about your library!",
            isUser = false
        )
        _uiState.value = _uiState.value.copy(
            messages = listOf(welcomeMessage),
            isOnDeviceAI = generativeAiRepository.isUsingOnDeviceAI()
        )

        viewModelScope.launch {
            backendStateHolder.state.collectLatest { state ->
                _uiState.update {
                    it.copy(
                        isOnDeviceAI = state.isUsingNano,
                        nanoStatus = state.nanoStatus,
                        isDownloadingNano = state.isDownloading,
                        downloadProgress = state.downloadBytesProgress,
                        canRetryDownload = state.canRetryDownload,
                        errorCode = state.errorCode
                    )
                }
            }
        }
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return

        val userMessage = AiMessage(content = query, isUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true
        )

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

                // Perform search if keywords are valid and different from original query (or just search anyway)
                val searchResults = if (searchKeywords.isNotEmpty()) {
                    // Search for the first/best keyword or combine them
                    val searchTerm = searchKeywords.joinToString(" ")
                    when (val result = jellyfinRepository.searchItems(searchTerm)) {
                        is ApiResult.Success -> result.data
                        else -> emptyList()
                    }
                } else {
                    emptyList()
                }

                val aiMessage = AiMessage(
                    content = chatResponse,
                    isUser = false,
                    recommendedItems = searchResults.take(10)
                )

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isLoading = false
                )

            } catch (e: Exception) {
                val errorMessage = AiMessage(
                    content = "Sorry, I encountered an error: ${e.message}",
                    isUser = false
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errorMessage,
                    isLoading = false
                )
            }
        }
    }
    
    fun getImageUrl(item: BaseItemDto): String? = jellyfinRepository.getImageUrl(item.id.toString())

    fun retryNanoDownload() {
        generativeAiRepository.retryNanoDownload()
    }
}
