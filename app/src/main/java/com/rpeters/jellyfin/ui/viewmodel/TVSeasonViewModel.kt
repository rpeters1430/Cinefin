package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import com.rpeters.jellyfin.utils.isCompletelyWatched
import com.rpeters.jellyfin.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class TVSeasonState(
    val seriesDetails: BaseItemDto? = null,
    val seasons: List<BaseItemDto> = emptyList(),
    val episodesBySeasonId: Map<String, List<BaseItemDto>> = emptyMap(),
    val similarSeries: List<BaseItemDto> = emptyList(),
    val nextEpisode: BaseItemDto? = null,
    val isLoading: Boolean = false,
    val loadingSeasonIds: Set<String> = emptySet(),
    val seasonEpisodeErrors: Map<String, String> = emptyMap(),
    val isSimilarSeriesLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class TVSeasonViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val mediaRepository: JellyfinMediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TVSeasonState())
    val state: StateFlow<TVSeasonState> = _state.asStateFlow()

    // Cache for episodes by season ID to avoid redundant API calls
    private val episodesCache = mutableMapOf<String, List<BaseItemDto>>()

    // Track the current series ID to avoid clearing cache on re-entry
    private var currentSeriesId: String? = null

    fun loadSeriesData(seriesId: String) {
        viewModelScope.launch {
            // Only clear cache and episodes if we're loading a different series
            // This preserves the episode dropdown state when navigating back
            val isNewSeries = currentSeriesId != seriesId
            if (isNewSeries) {
                episodesCache.clear()
                currentSeriesId = seriesId
            }

            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                episodesBySeasonId = if (isNewSeries) emptyMap() else _state.value.episodesBySeasonId,
                loadingSeasonIds = if (isNewSeries) emptySet() else _state.value.loadingSeasonIds,
                seasonEpisodeErrors = if (isNewSeries) emptyMap() else _state.value.seasonEpisodeErrors,
            )

            var seriesDetails = _state.value.seriesDetails
            var seasons = _state.value.seasons
            var similarSeries = _state.value.similarSeries
            var errorMessage: String? = null
            var nextEpisode: BaseItemDto? = null

            // Load series details
            when (val seriesResult = repository.getSeriesDetails(seriesId)) {
                is ApiResult.Success -> {
                    seriesDetails = seriesResult.data
                }
                is ApiResult.Error -> {
                    errorMessage = "Failed to load series details: ${seriesResult.message}"
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            // Load seasons
            when (val seasonsResult = mediaRepository.getSeasonsForSeries(seriesId)) {
                is ApiResult.Success -> {
                    seasons = seasonsResult.data

                    // Validate that the series has content only if no previous errors
                    // If no seasons exist AND the series has no child count (episodes), show error
                    if (errorMessage == null && seasons.isEmpty() && (seriesDetails?.childCount ?: 0) == 0) {
                        errorMessage = "This TV show has no seasons or episodes available"
                    }
                }
                is ApiResult.Error -> {
                    errorMessage = errorMessage ?: "Failed to load seasons: ${seasonsResult.message}"
                }
                is ApiResult.Loading -> {
                    // Already handled
                }
            }

            nextEpisode = seriesDetails?.let { details ->
                findNextUnwatchedEpisode(details, seasons)
            }

            // Load similar series
            _state.value = _state.value.copy(isSimilarSeriesLoading = true)
            when (val similarResult = mediaRepository.getSimilarSeries(seriesId)) {
                is ApiResult.Success -> {
                    similarSeries = similarResult.data
                        .filter { it.type == BaseItemKind.SERIES }
                        .filterNot { it.id.toString() == seriesId }
                }

                is ApiResult.Error -> {
                    // Don't show error for similar series failure - it's a non-critical feature
                    // Just leave the list empty
                }

                is ApiResult.Loading -> {
                    // Already handled
                }
            }
            _state.value = _state.value.copy(isSimilarSeriesLoading = false)

            _state.value = _state.value.copy(
                seriesDetails = seriesDetails,
                seasons = seasons,
                similarSeries = similarSeries,
                nextEpisode = nextEpisode,
                isLoading = false,
                errorMessage = errorMessage,
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun refresh() {
        val seriesId = _state.value.seriesDetails?.id?.toString()
        if (seriesId != null) {
            loadSeriesData(seriesId)
        }
    }

    fun loadSeasonEpisodes(seasonId: String) {
        if (seasonId.isBlank()) {
            return
        }

        val cachedEpisodes = episodesCache[seasonId]
        if (cachedEpisodes != null) {
            _state.value = _state.value.copy(
                episodesBySeasonId = _state.value.episodesBySeasonId + (seasonId to cachedEpisodes),
            )
            return
        }

        if (seasonId in _state.value.loadingSeasonIds) {
            return
        }

        _state.value = _state.value.copy(
            loadingSeasonIds = _state.value.loadingSeasonIds + seasonId,
            seasonEpisodeErrors = _state.value.seasonEpisodeErrors - seasonId,
        )

        viewModelScope.launch {
            when (val episodesResult = mediaRepository.getEpisodesForSeason(seasonId)) {
                is ApiResult.Success -> {
                    episodesCache[seasonId] = episodesResult.data
                    _state.value = _state.value.copy(
                        episodesBySeasonId = _state.value.episodesBySeasonId + (seasonId to episodesResult.data),
                        loadingSeasonIds = _state.value.loadingSeasonIds - seasonId,
                    )
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        loadingSeasonIds = _state.value.loadingSeasonIds - seasonId,
                        seasonEpisodeErrors = _state.value.seasonEpisodeErrors +
                            (seasonId to "Failed to load episodes: ${episodesResult.message}"),
                    )
                }
                is ApiResult.Loading -> {
                    _state.value = _state.value.copy(
                        loadingSeasonIds = _state.value.loadingSeasonIds - seasonId,
                    )
                }
            }
        }
    }

    private suspend fun findNextUnwatchedEpisode(
        series: BaseItemDto,
        seasons: List<BaseItemDto>,
    ): BaseItemDto? {
        // If completely watched, return first episode for "Rewatch Series"
        if (series.isCompletelyWatched()) {
            return findFirstEpisode(seasons)
        }

        val sortedSeasons = seasons.sortedWith(
            compareBy<BaseItemDto> { it.indexNumber ?: Int.MAX_VALUE }
                .thenBy { it.name.orEmpty() },
        )

        for (season in sortedSeasons) {
            val seasonId = season.id.toString()

            // Check cache first to avoid redundant API calls
            val episodes = getEpisodesForSeason(seasonId) ?: continue

            val nextEpisode = episodes
                .sortedWith(compareBy<BaseItemDto> { it.indexNumber ?: Int.MAX_VALUE })
                .firstOrNull { !it.isWatched() }
            if (nextEpisode != null) {
                return nextEpisode
            }
        }

        return null
    }

    private suspend fun findFirstEpisode(seasons: List<BaseItemDto>): BaseItemDto? {
        val sortedSeasons = seasons.sortedWith(
            compareBy<BaseItemDto> { it.indexNumber ?: Int.MAX_VALUE }
                .thenBy { it.name.orEmpty() },
        )

        for (season in sortedSeasons) {
            val seasonId = season.id.toString()
            val episodes = getEpisodesForSeason(seasonId) ?: continue
            val firstEpisode = episodes
                .sortedWith(compareBy<BaseItemDto> { it.indexNumber ?: Int.MAX_VALUE })
                .firstOrNull()
            if (firstEpisode != null) {
                return firstEpisode
            }
        }

        return null
    }

    private suspend fun getEpisodesForSeason(seasonId: String): List<BaseItemDto>? {
        return episodesCache[seasonId] ?: run {
            when (val episodesResult = mediaRepository.getEpisodesForSeason(seasonId)) {
                is ApiResult.Success -> {
                    // Cache the episodes for future lookups
                    episodesCache[seasonId] = episodesResult.data
                    episodesResult.data
                }
                is ApiResult.Error -> {
                    null
                }
                is ApiResult.Loading -> {
                    null
                }
            }
        }
    }
}
