package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.GenerativeAiRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * ViewModel for Person Detail Screen showing actor/director filmography
 */
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val aiRepository: GenerativeAiRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle.get<String>("personId"))
    private val personName: String = checkNotNull(savedStateHandle.get<String>("personName"))

    private val _uiState = MutableStateFlow<PersonDetailUiState>(PersonDetailUiState.Loading)
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    init {
        loadPersonFilmography()
    }

    fun loadPersonFilmography() {
        viewModelScope.launch {
            _uiState.value = PersonDetailUiState.Loading

            when (val result = repository.getItemsByPerson(personId)) {
                is ApiResult.Success -> {
                    val items = result.data
                    val movies = items.filter { it.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE }
                    val shows = items.filter { it.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES }

                    // Set initial success state without bio
                    _uiState.value = PersonDetailUiState.Success(
                        personId = personId,
                        personName = personName,
                        movies = movies,
                        tvShows = shows,
                        allItems = items,
                        aiBio = null,
                        isBioLoading = true
                    )

                    // Generate AI bio asynchronously
                    generateBio(items)
                }
                is ApiResult.Error -> {
                    _uiState.value = PersonDetailUiState.Error(result.message)
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    private fun generateBio(filmography: List<BaseItemDto>) {
        viewModelScope.launch {
            try {
                val bio = aiRepository.generatePersonBio(personName, filmography)

                // Update state with bio
                val currentState = _uiState.value
                if (currentState is PersonDetailUiState.Success) {
                    _uiState.value = currentState.copy(
                        aiBio = bio.takeIf { it.isNotBlank() },
                        isBioLoading = false
                    )
                }
            } catch (e: Exception) {
                // Bio generation failed, just mark as not loading
                val currentState = _uiState.value
                if (currentState is PersonDetailUiState.Success) {
                    _uiState.value = currentState.copy(
                        aiBio = null,
                        isBioLoading = false
                    )
                }
            }
        }
    }

    fun refresh() {
        loadPersonFilmography()
    }
}

/**
 * UI State for Person Detail Screen
 */
sealed class PersonDetailUiState {
    object Loading : PersonDetailUiState()

    data class Success(
        val personId: String,
        val personName: String,
        val movies: List<BaseItemDto>,
        val tvShows: List<BaseItemDto>,
        val allItems: List<BaseItemDto>,
        val aiBio: String? = null,
        val isBioLoading: Boolean = false,
    ) : PersonDetailUiState() {
        val movieCount: Int get() = movies.size
        val tvShowCount: Int get() = tvShows.size
        val totalCount: Int get() = allItems.size
    }

    data class Error(val message: String) : PersonDetailUiState()
}
