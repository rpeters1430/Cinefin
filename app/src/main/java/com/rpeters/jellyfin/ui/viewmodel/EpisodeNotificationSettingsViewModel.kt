package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.EpisodeNotificationPreferences
import com.rpeters.jellyfin.data.preferences.EpisodeNotificationPreferencesRepository
import com.rpeters.jellyfin.data.worker.EpisodeNotificationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeNotificationSettingsViewModel @Inject constructor(
    private val repository: EpisodeNotificationPreferencesRepository,
    private val workerScheduler: EpisodeNotificationWorker.Scheduler,
) : ViewModel() {

    val preferences: StateFlow<EpisodeNotificationPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EpisodeNotificationPreferences.DEFAULT,
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(enabled)
            if (enabled && repository.getPreferences().followedSeries.isNotEmpty()) {
                workerScheduler.schedule()
            } else if (!enabled) {
                workerScheduler.cancel()
            }
        }
    }

    fun removeSeries(seriesId: String) {
        viewModelScope.launch {
            repository.unfollowSeries(seriesId)
            if (repository.getPreferences().followedSeries.isEmpty()) {
                workerScheduler.cancel()
            }
        }
    }

    fun checkNow() {
        workerScheduler.runOnce()
    }
}
