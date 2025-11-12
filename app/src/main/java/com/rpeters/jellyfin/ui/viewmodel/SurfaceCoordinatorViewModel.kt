package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.ui.surface.ModernSurfaceCoordinator
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Lightweight view model that funnels UI surface data into the
 * [ModernSurfaceCoordinator]. This keeps composables free of direct dependency
 * on the coordinator while still enabling hilt-based injection.
 */
@HiltViewModel
class SurfaceCoordinatorViewModel @Inject constructor(
    private val modernSurfaceCoordinator: ModernSurfaceCoordinator,
) : ViewModel() {

    private val continueWatchingItems = MutableStateFlow<List<BaseItemDto>>(emptyList())

    init {
        viewModelScope.launch {
            continueWatchingItems
                .distinctUntilChangedBy { items ->
                    items.mapNotNull { item ->
                        val id = item.id?.toString() ?: return@mapNotNull null
                        val percentage = item.userData?.playedPercentage ?: 0.0
                        val lastPlayed = item.userData?.lastPlayedDate ?: ""
                        "$id|$percentage|$lastPlayed"
                    }
                }
                .collectLatest { items ->
                    modernSurfaceCoordinator.updateContinueWatching(items)
                }
        }
    }

    fun updateContinueWatching(items: List<BaseItemDto>) {
        continueWatchingItems.value = items
    }

    fun refreshSurfaces() {
        modernSurfaceCoordinator.refreshAll()
    }
}
