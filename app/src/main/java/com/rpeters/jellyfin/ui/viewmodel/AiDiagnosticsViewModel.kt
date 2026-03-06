package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.ai.AiDownloadState
import com.rpeters.jellyfin.data.repository.GenerativeAiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiBackendUiState(
    val isUsingNano: Boolean = false,
    val nanoStatus: String = "Cloud API",
    val isDownloading: Boolean = false,
    val downloadBytesProgress: String? = null,
    val canRetryDownload: Boolean = false,
    val errorCode: Int? = null,
    val detailedStatus: String = "Checking..."
)

@HiltViewModel
class AiDiagnosticsViewModel @Inject constructor(
    private val generativeAiRepository: GenerativeAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiBackendUiState())
    val aiBackendState: StateFlow<AiBackendUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                generativeAiRepository.downloadState,
                generativeAiRepository.isNanoActive
            ) { state, active ->
                state to active
            }.collect { (state, active) ->
                val detailed = generativeAiRepository.getDetailedAiStatus()
                _uiState.value = AiBackendUiState(
                    isUsingNano = active,
                    nanoStatus = state.name,
                    isDownloading = state == AiDownloadState.DOWNLOADING,
                    canRetryDownload = state == AiDownloadState.FAILED || state == AiDownloadState.SUPPORTED_NOT_DOWNLOADED,
                    detailedStatus = detailed
                )
            }
        }
    }

    fun retryNanoDownload() {
        viewModelScope.launch {
            generativeAiRepository.retryNanoDownload()
        }
    }
}
