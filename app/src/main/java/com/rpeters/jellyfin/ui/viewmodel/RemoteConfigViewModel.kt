package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.rpeters.jellyfin.data.repository.RemoteConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Simple ViewModel to provide access to RemoteConfig in Composables.
 * This allows navigation and screens to check feature flags easily.
 */
@HiltViewModel
class RemoteConfigViewModel @Inject constructor(
    val repository: RemoteConfigRepository,
) : ViewModel() {
    /**
     * Get a boolean feature flag value
     */
    fun getBoolean(key: String): Boolean = repository.getBoolean(key)

    /**
     * Get a string feature flag value
     */
    fun getString(key: String): String = repository.getString(key)

    /**
     * Get a long feature flag value
     */
    fun getLong(key: String): Long = repository.getLong(key)

    /**
     * Get a double feature flag value
     */
    fun getDouble(key: String): Double = repository.getDouble(key)
}
