package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.security.CertificatePinningManager
import com.rpeters.jellyfin.data.security.PinnedCertificateRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinningSettingsViewModel @Inject constructor(
    private val certificatePinningManager: CertificatePinningManager,
) : ViewModel() {

    private val _pins = MutableStateFlow<List<PinnedHostState>>(emptyList())
    val pins: StateFlow<List<PinnedHostState>> = _pins.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshPins()
    }

    fun refreshPins() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _pins.value = try {
                certificatePinningManager.getPinnedCertificates().map { it.toUiModel() }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun revokePin(hostname: String) {
        viewModelScope.launch {
            try {
                certificatePinningManager.removePin(hostname)
                refreshPins()
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun PinnedCertificateRecord.toUiModel(): PinnedHostState {
        val now = System.currentTimeMillis()
        return PinnedHostState(
            hostname = hostname,
            primaryPin = primaryPin,
            backupCount = backupPins.size,
            firstSeenEpochMillis = firstSeenEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            isExpired = isExpired(now),
        )
    }
}

data class PinnedHostState(
    val hostname: String,
    val primaryPin: String,
    val backupCount: Int,
    val firstSeenEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val isExpired: Boolean,
)
