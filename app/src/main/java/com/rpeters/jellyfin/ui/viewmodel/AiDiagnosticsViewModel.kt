package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.rpeters.jellyfin.data.ai.AiBackendStateHolder
import com.rpeters.jellyfin.di.AiModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for AI diagnostics screen.
 * Exposes the current AI backend state and provides retry functionality.
 */
@HiltViewModel
class AiDiagnosticsViewModel @Inject constructor(
    private val aiBackendStateHolder: AiBackendStateHolder,
) : ViewModel() {

    val aiBackendState: StateFlow<com.rpeters.jellyfin.data.ai.AiBackendState> = aiBackendStateHolder.state

    /**
     * Retry downloading the Gemini Nano model if previous attempt failed.
     */
    fun retryNanoDownload() {
        AiModule.retryNanoDownload()
    }
}
