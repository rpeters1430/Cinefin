package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.rpeters.jellyfin.data.ai.AiBackendState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AiDiagnosticsViewModel @Inject constructor() : ViewModel() {

    private val _aiBackendState = MutableStateFlow(
        AiBackendState(
            isUsingNano = false,
            nanoStatus = "Cloud API only",
            isDownloading = false,
            downloadBytesProgress = null,
            canRetryDownload = false,
            errorCode = null,
        ),
    )
    val aiBackendState: StateFlow<AiBackendState> = _aiBackendState.asStateFlow()

    fun retryNanoDownload() = Unit
}
