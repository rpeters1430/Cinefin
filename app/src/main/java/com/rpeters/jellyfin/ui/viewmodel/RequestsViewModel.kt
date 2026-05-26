package com.rpeters.jellyfin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.jellyfin.data.model.RadarrQualityProfile
import com.rpeters.jellyfin.data.model.SeerrMediaInfo
import com.rpeters.jellyfin.data.model.SeerrMediaItem
import com.rpeters.jellyfin.data.model.SeerrRequestRequest
import com.rpeters.jellyfin.data.model.SeerrSeason
import com.rpeters.jellyfin.data.model.SonarrQualityProfile
import com.rpeters.jellyfin.data.preferences.ArrPreferencesRepository
import com.rpeters.jellyfin.data.preferences.SeerrPreferences
import com.rpeters.jellyfin.data.preferences.SeerrPreferencesRepository
import com.rpeters.jellyfin.data.repository.JellyfinMediaRepository
import com.rpeters.jellyfin.data.repository.JellyfinSearchRepository
import com.rpeters.jellyfin.data.repository.RadarrRepository
import com.rpeters.jellyfin.data.repository.SeerrRepository
import com.rpeters.jellyfin.data.repository.SonarrRepository
import com.rpeters.jellyfin.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

data class PendingMovieRequest(
    val item: SeerrMediaItem,
    val qualityProfiles: List<RadarrQualityProfile>,
)

data class PendingTvRequest(
    val item: SeerrMediaItem,
    val seasons: List<Int>,
    val qualityProfiles: List<SonarrQualityProfile>,
    val isUsingSonarrDirect: Boolean,
)

data class TvEpisodeAvailability(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val isAvailable: Boolean,
)

data class TvSeasonAvailability(
    val seasonNumber: Int,
    val title: String,
    val availableCount: Int,
    val totalCount: Int,
    val isRequestableInSeerr: Boolean,
    val isPendingRequest: Boolean = false,
    val episodes: List<TvEpisodeAvailability>,
) {
    val missingCount: Int get() = totalCount - availableCount
    val hasMissingEpisodes: Boolean get() = missingCount > 0
    val canRequestMissingEpisodes: Boolean get() = hasMissingEpisodes && isRequestableInSeerr
}

data class TvAvailability(
    val localSeriesTitle: String,
    val seasons: List<TvSeasonAvailability>,
    val tvdbId: Int? = null,
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
    val isPluginConfigured: Boolean = false,
    val pluginCapabilities: List<String> = emptyList(),
    val isSonarrConfigured: Boolean = false,
    val requestingMediaId: Int? = null,
    val requestingSeasonKey: String? = null,
    val tvAvailabilityByMediaId: Map<Int, TvAvailability> = emptyMap(),
    val successMessage: String? = null,
    val recentSearches: List<String> = emptyList(),
    val pendingMovieRequest: PendingMovieRequest? = null,
    val pendingTvRequest: PendingTvRequest? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val seerrRepository: SeerrRepository,
    private val sonarrRepository: SonarrRepository,
    private val radarrRepository: RadarrRepository,
    private val jellyfinSearchRepository: JellyfinSearchRepository,
    private val jellyfinMediaRepository: JellyfinMediaRepository,
    private val preferencesRepository: SeerrPreferencesRepository,
    private val arrPreferencesRepository: ArrPreferencesRepository,
    private val cinefinPluginRepository: com.rpeters.jellyfin.data.repository.CinefinPluginRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val seerrPreferences: StateFlow<SeerrPreferences> = preferencesRepository.seerrPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SeerrPreferences.DEFAULT,
        )

    init {
        // Keep isConfigured and isSonarrConfigured in sync with any of the three services being configured & enabled.
        viewModelScope.launch {
            combine(
                preferencesRepository.seerrPreferencesFlow,
                arrPreferencesRepository.sonarrPreferencesFlow,
                arrPreferencesRepository.radarrPreferencesFlow,
            ) { seerr, sonarr, radarr ->
                val configured = (seerr.isValid && seerr.isEnabled) ||
                    (sonarr.isValid && sonarr.isEnabled) ||
                    (radarr.isValid && radarr.isEnabled) ||
                    _uiState.value.isPluginConfigured
                val sonarrConfigured = sonarr.isValid && sonarr.isEnabled
                Pair(configured, sonarrConfigured)
            }.collect { (configured, sonarrConfigured) ->
                _uiState.update { it.copy(isConfigured = configured, isSonarrConfigured = sonarrConfigured) }
            }
        }
    }

    init {
        // Check plugin availability (optional — plugin is no longer required)
        viewModelScope.launch {
            when (val infoResult = cinefinPluginRepository.getPluginInfo()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isPluginConfigured = infoResult.data.isConfigured,
                        pluginCapabilities = infoResult.data.capabilities,
                    )
                }
                else -> _uiState.update { it.copy(isPluginConfigured = false) }
            }
        }
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
                            _uiState.update { state ->
                                val q = state.query.trim()
                                val newRecent = if (q.isNotBlank()) {
                                    (listOf(q) + state.recentSearches.filter { it != q }).take(6)
                                } else {
                                    state.recentSearches
                                }
                                state.copy(
                                    results = searchResult.results,
                                    isLoading = false,
                                    recentSearches = newRecent,
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
        val state = _uiState.value
        if (state.isPluginConfigured) {
            requestSeasons(item, null)
            return
        }
        val seerr = seerrPreferences.value
        val useSeerr = seerr.isValid && seerr.isEnabled
        when {
            item.mediaType == "movie" && !useSeerr -> initiateMovieQualityRequest(item)
            item.mediaType == "tv" -> initiateTvSeasonRequest(item, useSeerr)
            else -> requestSeasons(item, null)
        }
    }

    private fun initiateMovieQualityRequest(item: SeerrMediaItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(requestingMediaId = item.id) }
            val profiles = when (val result = radarrRepository.getQualityProfiles()) {
                is ApiResult.Success -> result.data
                else -> emptyList()
            }
            _uiState.update {
                it.copy(
                    requestingMediaId = null,
                    pendingMovieRequest = PendingMovieRequest(item, profiles),
                )
            }
        }
    }

    private fun initiateTvSeasonRequest(item: SeerrMediaItem, useSeerr: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(requestingMediaId = item.id) }
            val mediaId = item.tmdbId ?: item.id
            val seasons = when (val result = seerrRepository.getTvDetails(mediaId)) {
                is ApiResult.Success -> result.data.seasons
                    .filter { it.seasonNumber > 0 }
                    .map { it.seasonNumber }
                    .sorted()
                else -> emptyList()
            }
            val qualityProfiles = if (!useSeerr && _uiState.value.isSonarrConfigured) {
                when (val result = sonarrRepository.getQualityProfiles()) {
                    is ApiResult.Success -> result.data
                    else -> emptyList()
                }
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    requestingMediaId = null,
                    pendingTvRequest = PendingTvRequest(
                        item = item,
                        seasons = seasons,
                        qualityProfiles = qualityProfiles,
                        isUsingSonarrDirect = !useSeerr,
                    ),
                )
            }
        }
    }

    fun confirmMovieRequest(item: SeerrMediaItem, qualityProfileId: Int) {
        _uiState.update { it.copy(pendingMovieRequest = null) }
        viewModelScope.launch {
            val mediaId = item.tmdbId ?: item.id
            _uiState.update { it.copy(requestingMediaId = item.id) }
            val result = radarrRepository.addMovie(mediaId, qualityProfileId)
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(
                        requestingMediaId = null,
                        successMessage = "Request submitted for ${item.displayTitle}",
                    )
                    is ApiResult.Error -> state.copy(requestingMediaId = null, errorMessage = result.message)
                    else -> state.copy(requestingMediaId = null)
                }
            }
        }
    }

    fun confirmTvRequest(item: SeerrMediaItem, selectedSeasons: List<Int>, qualityProfileId: Int?) {
        _uiState.update { it.copy(pendingTvRequest = null) }
        val state = _uiState.value
        val seerr = seerrPreferences.value
        val useSeerr = !state.isPluginConfigured && seerr.isValid && seerr.isEnabled
        if (useSeerr) {
            requestSeasons(item, selectedSeasons)
        } else {
            viewModelScope.launch {
                val tvdbId = resolveTvdbId(item)
                if (tvdbId == null) {
                    _uiState.update { it.copy(errorMessage = "TVDB ID not available for ${item.displayTitle}") }
                    return@launch
                }
                _uiState.update { it.copy(requestingMediaId = item.id) }
                val result = sonarrRepository.addSeries(tvdbId, selectedSeasons, qualityProfileId)
                _uiState.update { st ->
                    when (result) {
                        is ApiResult.Success -> st.copy(
                            requestingMediaId = null,
                            successMessage = "Request submitted for ${item.displayTitle}",
                        )
                        is ApiResult.Error -> st.copy(requestingMediaId = null, errorMessage = result.message)
                        else -> st.copy(requestingMediaId = null)
                    }
                }
            }
        }
    }

    private suspend fun resolveTvdbId(item: SeerrMediaItem): Int? {
        item.tvdbId?.let { return it }
        _uiState.value.tvAvailabilityByMediaId[item.id]?.tvdbId?.let { return it }
        val mediaId = item.tmdbId ?: item.id
        return when (val result = seerrRepository.getTvDetails(mediaId)) {
            is ApiResult.Success -> result.data.externalIds?.tvdbId
            else -> null
        }
    }

    fun dismissPendingRequest() {
        _uiState.update { it.copy(pendingMovieRequest = null, pendingTvRequest = null) }
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

    fun requestMissingEpisode(item: SeerrMediaItem, seasonNumber: Int, episodeNumber: Int) {
        val tvdbId = item.tvdbId
            ?: _uiState.value.tvAvailabilityByMediaId[item.id]?.tvdbId
        val state = _uiState.value
        val canRequest = state.isPluginConfigured || (tvdbId != null && state.isSonarrConfigured)
        if (!canRequest) {
            val reason = when {
                !state.isSonarrConfigured && !state.isPluginConfigured ->
                    "Configure Sonarr or the Cinefin plugin in Settings to request individual episodes"
                tvdbId == null ->
                    "TVDB ID unavailable for ${item.displayTitle} — try requesting the full season instead"
                else -> "Individual episode requests require Sonarr or the Cinefin plugin"
            }
            _uiState.update { it.copy(errorMessage = reason) }
            return
        }

        viewModelScope.launch {
            val episodeKey = "${item.id}:$seasonNumber:$episodeNumber"
            _uiState.update { it.copy(requestingMediaId = item.id, requestingSeasonKey = episodeKey) }

            // Prefer plugin, fall back to direct Sonarr
            val result: ApiResult<*> = if (_uiState.value.isPluginConfigured) {
                val seriesId = item.tvdbId?.toString() ?: item.tmdbId?.toString() ?: item.id.toString()
                cinefinPluginRepository.requestEpisode(seriesId, seasonNumber, episodeNumber)
            } else if (tvdbId != null) {
                sonarrRepository.requestEpisode(tvdbId, seasonNumber, episodeNumber)
            } else {
                ApiResult.Error("TVDB ID missing — cannot request individual episode")
            }

            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(
                        requestingMediaId = null,
                        requestingSeasonKey = null,
                        successMessage = "Request submitted for S${seasonNumber}E$episodeNumber of ${item.displayTitle}",
                    )
                    is ApiResult.Error -> state.copy(
                        requestingMediaId = null,
                        requestingSeasonKey = null,
                        errorMessage = result.message,
                    )
                    else -> state.copy(requestingMediaId = null, requestingSeasonKey = null)
                }
            }
        }
    }

    private fun requestSeasons(item: SeerrMediaItem, requestedSeasons: List<Int>?) {
        viewModelScope.launch {
            val seasonKey = requestedSeasons?.joinToString(prefix = "${item.id}:", separator = ",")
            _uiState.update { it.copy(requestingMediaId = item.id, requestingSeasonKey = seasonKey) }
            val mediaId = item.tmdbId ?: item.id
            val seerr = seerrPreferences.value

            // 1. Plugin route (if installed and configured on the server)
            if (_uiState.value.isPluginConfigured) {
                val result = cinefinPluginRepository.requestMedia(mediaId.toString(), item.mediaType, requestedSeasons)
                _uiState.update { state ->
                    when (result) {
                        is ApiResult.Success -> if (result.data.success) {
                            state.copy(
                                requestingMediaId = null,
                                requestingSeasonKey = null,
                                successMessage = "Request submitted for ${item.displayTitle}",
                                results = state.results.map { r ->
                                    if (r.id == item.id) r.copy(mediaInfo = updatedMediaInfo(r, null)) else r
                                },
                            )
                        } else {
                            state.copy(
                                requestingMediaId = null,
                                requestingSeasonKey = null,
                                errorMessage = result.data.message.ifBlank { "Failed to request ${item.displayTitle}" },
                            )
                        }
                        is ApiResult.Error -> state.copy(requestingMediaId = null, requestingSeasonKey = null, errorMessage = result.message)
                        else -> state.copy(requestingMediaId = null, requestingSeasonKey = null)
                    }
                }
                return@launch
            }

            // 2. Seerr/Overseerr direct (preferred when no plugin)
            if (seerr.isValid && seerr.isEnabled) {
                val request = if (item.mediaType == "tv") {
                    val seasons = loadRequestableSeasons(
                        mediaId = mediaId,
                        title = item.displayTitle,
                        requestedSeasons = requestedSeasons,
                    ) ?: return@launch

                    if (seasons.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                requestingMediaId = null,
                                errorMessage = "No requestable seasons found for ${item.displayTitle}. Jellyseerr may already mark them available or requested.",
                            )
                        }
                        return@launch
                    }

                    SeerrRequestRequest(
                        mediaType = item.mediaType,
                        mediaId = mediaId,
                        seasons = seasons,
                    )
                } else {
                    SeerrRequestRequest(
                        mediaType = item.mediaType,
                        mediaId = mediaId,
                    )
                }

                val result = seerrRepository.request(request)
                _uiState.update { state ->
                    when (result) {
                        is ApiResult.Success -> state.copy(
                            requestingMediaId = null,
                            requestingSeasonKey = null,
                            successMessage = "Request submitted for ${item.displayTitle}",
                            results = state.results.map { r ->
                                if (r.id == item.id) r.copy(mediaInfo = updatedMediaInfo(r, result.data.media)) else r
                            },
                        )
                        is ApiResult.Error -> state.copy(requestingMediaId = null, requestingSeasonKey = null, errorMessage = result.message)
                        else -> state.copy(requestingMediaId = null, requestingSeasonKey = null)
                    }
                }
                return@launch
            }

            // 3. Direct Radarr (movies) or Sonarr (TV) — no Seerr required
            val tvdbId = item.tvdbId
            val directResult: ApiResult<Unit> = when {
                item.mediaType == "movie" -> radarrRepository.addMovie(mediaId)
                item.mediaType == "tv" && tvdbId != null -> sonarrRepository.addSeries(tvdbId, requestedSeasons)
                else -> ApiResult.Error("No request service configured. Enable Seerr, Sonarr, or Radarr in Settings → Media Requests.")
            }

            _uiState.update { state ->
                when (directResult) {
                    is ApiResult.Success -> state.copy(
                        requestingMediaId = null,
                        requestingSeasonKey = null,
                        successMessage = "Request submitted for ${item.displayTitle}",
                    )
                    is ApiResult.Error -> state.copy(
                        requestingMediaId = null,
                        requestingSeasonKey = null,
                        errorMessage = directResult.message,
                    )
                    else -> state.copy(requestingMediaId = null, requestingSeasonKey = null)
                }
            }
        }
    }

    private suspend fun loadRequestableSeasons(
        mediaId: Int,
        title: String,
        requestedSeasons: List<Int>?,
    ): List<Int>? {
        return when (val detailsResult = seerrRepository.getTvDetails(mediaId)) {
            is ApiResult.Success -> detailsResult.data.requestableSeasons(
                requestedSeasons ?: detailsResult.data.seasons.map { it.seasonNumber },
            )
                .distinct()
                .sorted()
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        requestingMediaId = null,
                        requestingSeasonKey = null,
                        errorMessage = detailsResult.message.ifBlank { "Unable to load seasons for $title" },
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
            seerrDetails.seasons.map { it.seasonNumber },
        ).toSet()

        val activeRequestedSeasonNumbers = seerrDetails.mediaInfo?.requests.orEmpty()
            .filter { request -> request.status in ACTIVE_REQUEST_STATUSES && !request.is4k }
            .flatMap { request -> request.seasons.map { it.seasonNumber } }
            .toSet()

        val seasons = coroutineScope {
            seerrDetails.seasons
                .filter { it.seasonNumber > 0 }
                .sortedBy { it.seasonNumber }
                .map { seerrSeason ->
                    async {
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
                            isPendingRequest = seerrSeason.seasonNumber in activeRequestedSeasonNumbers,
                        )
                    }
                }
                .map { it.await() }
                .filterNotNull()
        }

        return TvAvailability(
            localSeriesTitle = localSeries.name ?: item.displayTitle,
            seasons = seasons,
            tvdbId = seerrDetails.externalIds?.tvdbId ?: item.tvdbId,
        )
    }

    private fun buildSeasonAvailability(
        seerrSeason: SeerrSeason,
        localEpisodes: List<BaseItemDto>,
        isRequestableInSeerr: Boolean,
        isPendingRequest: Boolean = false,
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
                isAvailable = localEpisode != null,
            )
        }

        return TvSeasonAvailability(
            seasonNumber = seerrSeason.seasonNumber,
            title = seerrSeason.name ?: "Season ${seerrSeason.seasonNumber}",
            availableCount = episodes.count { it.isAvailable },
            totalCount = episodes.size,
            isRequestableInSeerr = isRequestableInSeerr,
            isPendingRequest = isPendingRequest,
            episodes = episodes,
        )
    }

    private fun com.rpeters.jellyfin.data.model.SeerrTvDetails.requestableSeasons(
        requestedSeasonNumbers: List<Int>,
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

    fun dismissRecentSearch(query: String) {
        _uiState.update { it.copy(recentSearches = it.recentSearches.filter { q -> q != query }) }
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
        // Seerr *media* status codes (mediaInfo.status / season.status)
        private const val SEERR_STATUS_UNKNOWN = 1
        private const val SEERR_STATUS_PENDING = 2
        private const val SEERR_STATUS_PARTIALLY_AVAILABLE = 4
        private const val SEERR_STATUS_AVAILABLE = 5
        private const val SEERR_STATUS_DELETED = 6

        // Seerr *request* status codes (request.status) — distinct from media status codes above
        private const val SEERR_REQUEST_STATUS_PENDING = 1 // awaiting approval
        private const val SEERR_REQUEST_STATUS_APPROVED = 2 // approved, download queued

        // Media statuses that allow a new request to be submitted for the season
        private val REQUESTABLE_STATUSES = setOf(SEERR_STATUS_UNKNOWN, SEERR_STATUS_DELETED)

        // Request statuses that mean a season is already actively being processed
        private val ACTIVE_REQUEST_STATUSES = setOf(SEERR_REQUEST_STATUS_PENDING, SEERR_REQUEST_STATUS_APPROVED)
    }
}
