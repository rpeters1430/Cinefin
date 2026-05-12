package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.model.SeerrMediaInfo
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.data.model.SeerrRequestRequest
import com.rpeters.jellyfin.data.model.SeerrSeason
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class TvEpisodeAvailability(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val isAvailable: Boolean
)

data class TvSeasonAvailability(
    val seasonNumber: Int,
    val title: String,
    val availableCount: Int,
    val totalCount: Int,
    val isRequestableInSeerr: Boolean,
    val isPendingRequest: Boolean = false,
    val episodes: List<TvEpisodeAvailability>
) {
    val missingCount: Int get() = totalCount - availableCount
    val hasMissingEpisodes: Boolean get() = missingCount > 0
    val canRequestMissingEpisodes: Boolean get() = hasMissingEpisodes && isRequestableInSeerr
}

data class TvAvailability(
    val localSeriesTitle: String,
    val seasons: List<TvSeasonAvailability>
) {
    val totalAvailableEpisodes: Int get() = seasons.sumOf { it.availableCount }
    val totalEpisodes: Int get() = seasons.sumOf { it.totalCount }
    val missingSeasons: List<Int> get() = seasons.filter { it.canRequestMissingEpisodes }.map { it.seasonNumber }
}

data class RequestsUiState(
    val query: String = "",
    val results: List<SeerrMediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadingAvailabilityIds: Set<Int> = emptySet(),
    val checkedTvItemIds: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
    val requestingMediaId: Int? = null,
    val requestingSeasonKey: String? = null,
    val tvAvailabilityByMediaId: Map<Int, TvAvailability> = emptyMap(),
    val successMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val jellyfinSearchRepository: JellyfinSearchRepository,
    private val jellyfinMediaRepository: JellyfinMediaRepository,
    private val preferencesRepository: SeerrPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val seerrPreferences: StateFlow<SeerrPreferences> = preferencesRepository.seerrPreferencesFlow
        .onEach { prefs ->
            _uiState.update { it.copy(isConfigured = prefs.isValid && prefs.isEnabled) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SeerrPreferences.DEFAULT
        )

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .distinctUntilChanged()
                .onEach { _uiState.update { state -> state.copy(isLoading = true, errorMessage = null) } }
                .flatMapLatest { query ->
                    val result = if (query.isNotBlank()) {
                        seerrRepository.search(query)
                    } else {
                        seerrRepository.getTrending()
                    }
                    kotlinx.coroutines.flow.flowOf(result)
                }
                .collect { result ->
                    when (result) {
                        is ApiResult.Success<*> -> {
                            val searchResult = result.data as com.rpeters.jellyfin.data.model.SeerrSearchResult
                            _uiState.update {
                                it.copy(
                                    results = searchResult.results,
                                    isLoading = false,
                                    tvAvailabilityByMediaId = emptyMap(),
                                    loadingAvailabilityIds = emptySet(),
                                    checkedTvItemIds = emptySet(),
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            // Don't show an error if it's just because Seerr isn't configured yet
                            val errorMessage = if (result.message.contains("not configured")) null else result.message
                            _uiState.update { it.copy(errorMessage = errorMessage, isLoading = false) }
                        }
                        is ApiResult.Loading -> {
                            _uiState.update { it.copy(isLoading = true) }
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(query = query, errorMessage = null, successMessage = null) }
    }

    fun requestMedia(item: SeerrMediaItem) {
        requestSeasons(item, null)
    }

    fun requestSeason(item: SeerrMediaItem, seasonNumber: Int) {
        requestSeasons(item, listOf(seasonNumber))
    }

    fun requestMissingSeasons(item: SeerrMediaItem) {
        val seasons = _uiState.value.tvAvailabilityByMediaId[item.id]?.missingSeasons.orEmpty()
        if (seasons.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No missing seasons found for ${item.displayTitle}") }
            return
        }
        requestSeasons(item, seasons)
    }

    private fun requestSeasons(item: SeerrMediaItem, requestedSeasons: List<Int>?) {
        viewModelScope.launch {
            val seasonKey = requestedSeasons?.joinToString(prefix = "${item.id}:", separator = ",")
            _uiState.update { it.copy(requestingMediaId = item.id, requestingSeasonKey = seasonKey) }
            val mediaId = item.tmdbId ?: item.id
            val request = if (item.mediaType == "tv") {
                val seasons = loadRequestableSeasons(
                    mediaId = mediaId,
                    title = item.displayTitle,
                    requestedSeasons = requestedSeasons
                ) ?: return@launch

                if (seasons.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            requestingMediaId = null,
                            errorMessage = "No requestable seasons found for ${item.displayTitle}. Jellyseerr may already mark them available or requested."
                        )
                    }
                    return@launch
                }

                SeerrRequestRequest(
                    mediaType = item.mediaType,
                    mediaId = mediaId,
                    seasons = seasons
                )
            } else {
                SeerrRequestRequest(
                    mediaType = item.mediaType,
                    mediaId = mediaId
                )
            }

            val result = seerrRepository.request(request)
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> {
                        val updatedResults = state.results.map { r ->
                            if (r.id == item.id) r.copy(mediaInfo = updatedMediaInfo(r, result.data.media)) else r
                        }
                        state.copy(
                            requestingMediaId = null,
                            requestingSeasonKey = null,
                            successMessage = "Request submitted for ${item.displayTitle}",
                            results = updatedResults
                        )
                    }
                    is ApiResult.Error -> {
                        state.copy(
                            requestingMediaId = null,
                            requestingSeasonKey = null,
                            errorMessage = result.message
                        )
                    }
                    else -> state.copy(requestingMediaId = null, requestingSeasonKey = null)
                }
            }
        }
    }

    private suspend fun loadRequestableSeasons(
        mediaId: Int,
        title: String,
        requestedSeasons: List<Int>?
    ): List<Int>? {
        return when (val detailsResult = seerrRepository.getTvDetails(mediaId)) {
            is ApiResult.Success -> detailsResult.data.requestableSeasons(
                requestedSeasons ?: detailsResult.data.seasons.map { it.seasonNumber }
            )
                .distinct()
                .sorted()
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        requestingMediaId = null,
                        requestingSeasonKey = null,
                        errorMessage = detailsResult.message.ifBlank { "Unable to load seasons for $title" }
                    )
                }
                null
            }
            is ApiResult.Loading -> emptyList()
        }
    }

    private suspend fun buildTvAvailability(item: SeerrMediaItem, localSeries: BaseItemDto): TvAvailability? {
        val mediaId = item.tmdbId ?: item.id
        val seerrDetails = when (val result = seerrRepository.getTvDetails(mediaId)) {
            is ApiResult.Success -> result.data
            else -> return null
        }
        val localSeasons = when (val result = jellyfinMediaRepository.getSeasonsForSeries(localSeries.id.toString())) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }

        val localSeasonByNumber = localSeasons
            .mapNotNull { season -> season.indexNumber?.let { it to season } }
            .toMap()

        val requestableSeasonNumbers = seerrDetails.requestableSeasons(
            seerrDetails.seasons.map { it.seasonNumber }
        ).toSet()

        val activeRequestedSeasonNumbers = seerrDetails.mediaInfo?.requests.orEmpty()
            .filter { request -> request.status in ACTIVE_REQUEST_STATUSES && !request.is4k }
            .flatMap { request -> request.seasons.map { it.seasonNumber } }
            .toSet()

        val seasons = seerrDetails.seasons
            .filter { it.seasonNumber > 0 }
            .sortedBy { it.seasonNumber }
            .mapNotNull { seerrSeason ->
                val localSeason = localSeasonByNumber[seerrSeason.seasonNumber]
                val localEpisodes = localSeason?.let { season ->
                    when (val result = jellyfinMediaRepository.getEpisodesForSeason(season.id.toString())) {
                        is ApiResult.Success -> result.data
                        else -> emptyList()
                    }
                }.orEmpty()
                val seerrSeasonWithEpisodes = when (val result = seerrRepository.getTvSeasonDetails(mediaId, seerrSeason.seasonNumber)) {
                    is ApiResult.Success -> result.data
                    else -> seerrSeason
                }
                buildSeasonAvailability(
                    seerrSeason = seerrSeasonWithEpisodes,
                    localEpisodes = localEpisodes,
                    isRequestableInSeerr = seerrSeason.seasonNumber in requestableSeasonNumbers,
                    isPendingRequest = seerrSeason.seasonNumber in activeRequestedSeasonNumbers
                )
            }

        return TvAvailability(
            localSeriesTitle = localSeries.name ?: item.displayTitle,
            seasons = seasons
        )
    }

    private fun buildSeasonAvailability(
        seerrSeason: SeerrSeason,
        localEpisodes: List<BaseItemDto>,
        isRequestableInSeerr: Boolean,
        isPendingRequest: Boolean = false
    ): TvSeasonAvailability? {
        val localEpisodeByNumber = localEpisodes
            .mapNotNull { episode -> episode.indexNumber?.let { it to episode } }
            .toMap()

        val seerrEpisodes = seerrSeason.episodes
            .mapNotNull { episode ->
                val number = episode.episodeNumber ?: return@mapNotNull null
                number to (episode.name ?: "Episode $number")
            }

        val episodeNumbers = when {
            seerrEpisodes.isNotEmpty() -> seerrEpisodes.map { it.first }
            seerrSeason.episodeCount != null && seerrSeason.episodeCount > 0 -> (1..seerrSeason.episodeCount).toList()
            localEpisodeByNumber.isNotEmpty() -> localEpisodeByNumber.keys.sorted()
            else -> emptyList()
        }

        if (episodeNumbers.isEmpty()) return null

        val titleByNumber = seerrEpisodes.toMap()
        val episodes = episodeNumbers.distinct().sorted().map { episodeNumber ->
            val localEpisode = localEpisodeByNumber[episodeNumber]
            TvEpisodeAvailability(
                seasonNumber = seerrSeason.seasonNumber,
                episodeNumber = episodeNumber,
                title = titleByNumber[episodeNumber] ?: localEpisode?.name ?: "Episode $episodeNumber",
                isAvailable = localEpisode != null
            )
        }

        return TvSeasonAvailability(
            seasonNumber = seerrSeason.seasonNumber,
            title = seerrSeason.name ?: "Season ${seerrSeason.seasonNumber}",
            availableCount = episodes.count { it.isAvailable },
            totalCount = episodes.size,
            isRequestableInSeerr = isRequestableInSeerr,
            isPendingRequest = isPendingRequest,
            episodes = episodes
        )
    }

    private fun com.rpeters.jellyfin.data.model.SeerrTvDetails.requestableSeasons(
        requestedSeasonNumbers: List<Int>
    ): List<Int> {
        val activeRequestedSeasons = mediaInfo?.requests.orEmpty()
            .filter { request -> request.status in ACTIVE_REQUEST_STATUSES && !request.is4k }
            .flatMap { request -> request.seasons.map { it.seasonNumber } }
            .toSet()

        val seerrSeasonByNumber = seasons.associateBy { it.seasonNumber }
        val mediaInfoSeasonStatusByNumber = mediaInfo?.seasons.orEmpty()
            .associate { it.seasonNumber to it.status }
        return requestedSeasonNumbers
            .filter { it > 0 }
            .filter { seasonNumber -> seasonNumber !in activeRequestedSeasons }
            .filter { seasonNumber ->
                val status = mediaInfoSeasonStatusByNumber[seasonNumber]
                    ?: seerrSeasonByNumber[seasonNumber]?.status
                status == null || status in REQUESTABLE_STATUSES
            }
    }

    private fun findLocalSeriesMatch(item: SeerrMediaItem, localSeries: List<BaseItemDto>): BaseItemDto? {
        val seerrTitle = item.displayTitle.normalizedTitle()
        val seerrYear = item.displayDate.take(4).toIntOrNull()
        return localSeries.firstOrNull { series ->
            val titleMatches = series.name?.normalizedTitle() == seerrTitle
            val yearMatches = seerrYear == null || series.productionYear == null || series.productionYear == seerrYear
            titleMatches && yearMatches
        } ?: localSeries.firstOrNull { series ->
            val title = series.name?.normalizedTitle().orEmpty()
            title.isNotBlank() && (title.contains(seerrTitle) || seerrTitle.contains(title))
        }
    }

    private fun String.normalizedTitle(): String =
        lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun checkTvAvailability(item: SeerrMediaItem) {
        if (item.mediaType != "tv") return
        val itemId = item.id
        if (itemId in _uiState.value.loadingAvailabilityIds || itemId in _uiState.value.checkedTvItemIds) return

        viewModelScope.launch {
            _uiState.update { it.copy(loadingAvailabilityIds = it.loadingAvailabilityIds + itemId) }

            val localSeries = when (val result = jellyfinSearchRepository.searchTVShows(item.displayTitle, limit = 10)) {
                is ApiResult.Success -> result.data
                else -> emptyList()
            }
            val localMatch = findLocalSeriesMatch(item, localSeries)
            val availability = localMatch?.let { buildTvAvailability(item, it) }

            _uiState.update { state ->
                state.copy(
                    loadingAvailabilityIds = state.loadingAvailabilityIds - itemId,
                    checkedTvItemIds = state.checkedTvItemIds + itemId,
                    tvAvailabilityByMediaId = if (availability != null) {
                        state.tvAvailabilityByMediaId + (itemId to availability)
                    } else {
                        state.tvAvailabilityByMediaId
                    },
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    /**
     * Returns the best available media info to reflect that a request has been submitted.
     * Precedence: API response media > optimistic pending update of existing media > new pending media.
     */
    private fun updatedMediaInfo(item: SeerrMediaItem, responseMedia: SeerrMediaInfo?): SeerrMediaInfo {
        return responseMedia
            ?: item.mediaInfo?.let { existing ->
                if (existing.status != SEERR_STATUS_AVAILABLE) existing.copy(status = SEERR_STATUS_PENDING) else existing
            }
            ?: SeerrMediaInfo(status = SEERR_STATUS_PENDING, requests = null, seasons = emptyList())
    }

    companion object {
        private const val SEERR_STATUS_UNKNOWN = 1
        private const val SEERR_STATUS_PENDING = 2
        private const val SEERR_STATUS_PARTIALLY_AVAILABLE = 4
        private const val SEERR_STATUS_AVAILABLE = 5
        private const val SEERR_STATUS_DELETED = 6
        // Statuses that allow a season to be requested (not yet fully in-progress or available)
        private val REQUESTABLE_STATUSES = setOf(SEERR_STATUS_UNKNOWN, SEERR_STATUS_PARTIALLY_AVAILABLE, SEERR_STATUS_DELETED)
        private val ACTIVE_REQUEST_STATUSES = setOf(1, 2)
    }
}
