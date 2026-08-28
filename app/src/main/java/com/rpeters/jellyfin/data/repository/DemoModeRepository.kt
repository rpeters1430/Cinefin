package com.rpeters.jellyfin.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the app is running in "Demo Mode".
 *
 * Demo Mode lets app store reviewers (e.g. Google Play testers) bypass the real server
 * connection screen and explore the app with bundled sample metadata/media, with no
 * personal Jellyfin server required. It is activated by entering [TRIGGER_KEYWORD] into
 * the server URL field on the server connection screen instead of a real server address.
 *
 * This is a process-wide singleton so every [com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel]
 * instance (there can be more than one, scoped to different navigation back stack entries)
 * observes the same demo mode state.
 */
@Singleton
class DemoModeRepository @Inject constructor() {

    private val _isDemoModeActive = MutableStateFlow(false)
    val isDemoModeActive: StateFlow<Boolean> = _isDemoModeActive.asStateFlow()

    fun activate() {
        _isDemoModeActive.value = true
    }

    fun deactivate() {
        _isDemoModeActive.value = false
    }

    companion object {
        /** Keyword reviewers can type into the server URL field to enter Demo Mode. */
        const val TRIGGER_KEYWORD = "demo.mode"

        fun isTriggerKeyword(serverUrl: String): Boolean =
            serverUrl.trim().equals(TRIGGER_KEYWORD, ignoreCase = true)
    }
}
