package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.IJellyfinRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
import com.rpeters.jellyfin.data.repository.SonarrRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class MissingEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
)

data class SeasonEpisodesState(
    val episodes: List<BaseItemDto> = emptyList(),
    val missingEpisodes: List<MissingEpisode> = emptyList(),
    val isSonarrConfigured: Boolean = false,
    val isLoadingMissing: Boolean = false,
    val requestingEpisodeKey: String? = null,
    val successMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SeasonEpisodesViewModel @Inject constructor(
    private val repository: IJellyfinRepository,
    private val seerrRepository: SeerrRepository,
    private val sonarrRepository: SonarrRepository,
    private val arrPreferencesRepository: ArrPreferencesRepository,
    private val seerrPreferencesRepository: SeerrPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeasonEpisodesState())
    val state: StateFlow<SeasonEpisodesState> = _state.asStateFlow()

    private var currentSeasonId: String? = null
    private var loadInProgressFor: String? = null

    fun loadEpisodes(seasonId: String) {
        if (loadInProgressFor == seasonId) return
        loadInProgressFor = seasonId

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            currentSeasonId = seasonId

            when (val result = repository.getEpisodesForSeason(seasonId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(episodes = result.data, isLoading = false) }
                    loadMissingEpisodes(seasonId, result.data)
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(isLoading = false, errorMessage = "Failed to load episodes: ${result.message}") }
                }
                is ApiResult.Loading -> Unit
            }
            if (loadInProgressFor == seasonId) loadInProgressFor = null
        }
    }

    private suspend fun loadMissingEpisodes(seasonId: String, localEpisodes: List<BaseItemDto>) {
        val sonarrPrefs = arrPreferencesRepository.sonarrPreferencesFlow.first()
        val seerrPrefs = seerrPreferencesRepository.seerrPreferencesFlow.first()
        val sonarrConfigured = sonarrPrefs.isValid && sonarrPrefs.isEnabled
        val seerrConfigured = seerrPrefs.isValid && seerrPrefs.isEnabled

        _state.update { it.copy(isSonarrConfigured = sonarrConfigured) }

        if (!seerrConfigured || localEpisodes.isEmpty()) return

        val seasonNumber = localEpisodes.first().parentIndexNumber ?: return
        val seriesJellyfinId = localEpisodes.first().seriesId?.toString() ?: return

        _state.update { it.copy(isLoadingMissing = true) }

        val seriesItem = when (val r = repository.getItemDetails(seriesJellyfinId)) {
            is ApiResult.Success -> r.data
            else -> {
                _state.update { it.copy(isLoadingMissing = false) }
                return
            }
        }

        val tmdbId = seriesItem.providerIds?.get("Tmdb")?.toIntOrNull()
        val tvdbId = seriesItem.providerIds?.get("Tvdb")?.toIntOrNull()

        // Store TVDB ID for use during episode requests.
        storedTvdbId = tvdbId

        if (tmdbId == null) {
            _state.update { it.copy(isLoadingMissing = false) }
            return
        }

        val seerrSeason = when (val r = seerrRepository.getTvSeasonDetails(tmdbId, seasonNumber)) {
            is ApiResult.Success -> r.data
            else -> {
                _state.update { it.copy(isLoadingMissing = false) }
                return
            }
        }

        val localEpisodeNumbers = localEpisodes.mapNotNull { it.indexNumber }.toSet()
        val missing = seerrSeason.episodes
            .mapNotNull { ep ->
                val num = ep.episodeNumber ?: return@mapNotNull null
                if (num in localEpisodeNumbers) return@mapNotNull null
                MissingEpisode(
                    seasonNumber = seasonNumber,
                    episodeNumber = num,
                    title = ep.name ?: "Episode $num",
                )
            }
            .sortedBy { it.episodeNumber }

        _state.update { it.copy(missingEpisodes = missing, isLoadingMissing = false) }
    }

    // Cached during loadMissingEpisodes so requestEpisode can use it without re-fetching.
    private var storedTvdbId: Int? = null

    fun requestMissingEpisode(seasonNumber: Int, episodeNumber: Int) {
        val tvdbId = storedTvdbId ?: run {
            _state.update { it.copy(errorMessage = "Series data not loaded yet — wait for the episode list to finish loading, then try again") }
            return
        }
        val key = "$seasonNumber:$episodeNumber"

        viewModelScope.launch {
            _state.update { it.copy(requestingEpisodeKey = key, successMessage = null, errorMessage = null) }

            val result = sonarrRepository.requestEpisode(tvdbId, seasonNumber, episodeNumber)

            _state.update { current ->
                when (result) {
                    is ApiResult.Success -> current.copy(
                        requestingEpisodeKey = null,
                        successMessage = "Request submitted for S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}",
                        missingEpisodes = current.missingEpisodes.filterNot {
                            it.seasonNumber == seasonNumber && it.episodeNumber == episodeNumber
                        },
                    )
                    is ApiResult.Error -> current.copy(
                        requestingEpisodeKey = null,
                        errorMessage = result.message,
                    )
                    else -> current.copy(requestingEpisodeKey = null)
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }

    fun refresh() {
        currentSeasonId?.let { loadEpisodes(it) }
    }
}
