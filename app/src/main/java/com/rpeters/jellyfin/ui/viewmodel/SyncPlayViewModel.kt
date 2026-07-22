package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.SyncPlayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncPlayState(
    val isInGroup: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val members: List<String> = emptyList(),
    val availableGroups: List<SyncPlayGroupSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class SyncPlayGroupSummary(
    val id: String,
    val name: String,
    val members: List<String>,
)

@HiltViewModel
class SyncPlayViewModel @Inject constructor(
    private val repository: SyncPlayRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SyncPlayState())
    val state: StateFlow<SyncPlayState> = _state.asStateFlow()

    fun loadGroups() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val groups = repository.getGroups().map { group ->
                    SyncPlayGroupSummary(
                        id = group.groupId.toString(),
                        name = group.groupName,
                        members = group.participants,
                    )
                }
                _state.value = _state.value.copy(isLoading = false, availableGroups = groups)
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(isLoading = false)
                throw e
            }
        }
    }

    fun joinGroup(groupId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val group = repository.joinGroup(groupId)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isInGroup = true,
                    groupId = group.groupId.toString(),
                    groupName = group.groupName,
                    members = group.participants,
                    availableGroups = emptyList(),
                )
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(isLoading = false)
                throw e
            }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val group = repository.createGroup(name)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isInGroup = true,
                    groupId = group.groupId.toString(),
                    groupName = group.groupName,
                    members = group.participants,
                    availableGroups = emptyList(),
                )
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(isLoading = false)
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Unable to create group")
            }
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                repository.leaveGroup()
                _state.value = SyncPlayState()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = e.message ?: "Unable to leave group")
            }
        }
    }
}
