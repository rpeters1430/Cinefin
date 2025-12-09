package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferences
import com.rpeters.jellyfin.data.preferences.LibraryActionsPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryActionsPreferencesViewModel @Inject constructor(
    private val repository: LibraryActionsPreferencesRepository,
) : ViewModel() {

    private val _preferences = MutableStateFlow(LibraryActionsPreferences.DEFAULT)
    val preferences: StateFlow<LibraryActionsPreferences> = _preferences.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            repository.preferences.collectLatest { prefs ->
                _preferences.value = prefs
            }
        }
    }

    fun setManagementActionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnableManagementActions(enabled)
        }
    }
}
