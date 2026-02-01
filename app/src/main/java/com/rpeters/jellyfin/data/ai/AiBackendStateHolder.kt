package com.rpeters.jellyfin.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AiBackendState(
    val isUsingNano: Boolean = false,
    val nanoStatus: String = "Checking availability...",
    val isDownloading: Boolean = false,
    val downloadBytesProgress: String? = null,
    val canRetryDownload: Boolean = false,
    val errorCode: Int? = null, // Stores GenAI error code for specific handling
)

@Singleton
class AiBackendStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(AiBackendState())
    val state: StateFlow<AiBackendState> = _state

    fun update(
        isUsingNano: Boolean,
        nanoStatus: String,
        isDownloading: Boolean = false,
        downloadBytesProgress: String? = null,
        canRetryDownload: Boolean = false,
        errorCode: Int? = null,
    ) {
        _state.value = AiBackendState(
            isUsingNano = isUsingNano,
            nanoStatus = nanoStatus,
            isDownloading = isDownloading,
            downloadBytesProgress = downloadBytesProgress,
            canRetryDownload = canRetryDownload,
            errorCode = errorCode,
        )
    }
}
