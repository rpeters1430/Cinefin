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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class PendingMovieRequest(
    val item: SeerrMediaItem,
    val qualityProfiles: List<RadarrQualityProfile>,
    val isUsingRadarrDirect: Boolean,
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

data class RequestHistoryItem(
    val request: com.rpeters.jellyfin.data.model.SeerrRequestItem,
    val title: String? = null,
    val posterPath: String? = null,
    val isLoadingDetails: Boolean = false,
)

private data class SeerrEpisodeMetadata(
    val number: Int,
    val title: String,
    val airDate: String?,
)

data class RequestsUiState(
    val query: String = "",
    val results: List<SeerrMediaItem> = emptyList(),
    val trending: List<SeerrMediaItem> = emptyList(),
    val upcomingMovies: List<SeerrMediaItem> = emptyList(),
    val upcomingTv: List<SeerrMediaItem> = emptyList(),
    val popularMovies: List<SeerrMediaItem> = emptyList(),
    val popularTv: List<SeerrMediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadingAvailabilityIds: Set<Int> = emptySet(),
    val checkedTvItemIds: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
    val isPluginConfigured: Boolean = false,
    val pluginCapabilities: List<String> = emptyList(),
    val isSonarrConfigured: Boolean = false,
    val isRadarrConfigured: Boolean = false,
    val requestingMediaId: Int? = null,
    val requestingSeasonKey: String? = null,
    val tvAvailabilityByMediaId: Map<Int, TvAvailability> = emptyMap(),
    val successMessage: String? = null,
    val recentSearches: List<String> = emptyList(),
    val pendingMovieRequest: PendingMovieRequest? = null,
    val pendingTvRequest: PendingTvRequest? = null,
    val myRequests: List<RequestHistoryItem> = emptyList(),
    val isLoadingMyRequests: Boolean = false,
    val myRequestsLoaded: Boolean = false,
    val cancelingRequestId: Int? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isLoadingMore: Boolean = false,
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

    // Bounds how many season-detail lookups (Seerr + Jellyfin) run concurrently across all
    // checkTvAvailability() calls, so scrolling through a list of many-season shows doesn't
    // fire dozens of simultaneous requests at once.
    private val availabilitySemaphore = Semaphore(4)

    private val _searchQuery = MutableStateFlow("")

    val seerrPreferences: StateFlow<SeerrPreferences> = preferencesRepository.seerrPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SeerrPreferences.DEFAULT,
        )

    init {
        // Keep isConfigured, isSonarrConfigured, and isRadarrConfigured in sync with any of the three services being configured & enabled.
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
                val radarrConfigured = radarr.isValid && radarr.isEnabled
                Triple(configured, sonarrConfigured, radarrConfigured)
            }.collect { (configured, sonarrConfigured, radarrConfigured) ->
                _uiState.update {
                    it.copy(
                        isConfigured = configured,
                        isSonarrConfigured = sonarrConfigured,
                        isRadarrConfigured = radarrConfigured,
                    )
                }
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
            combine(
                _searchQuery,
                preferencesRepository.seerrPreferencesFlow
                    .map { it.isValid && it.isEnabled }
                    .distinctUntilChanged()
            ) { query, seerrEnabled ->
                query to seerrEnabled
            }
            .debounce(500)
            .distinctUntilChanged()
            .collect { (query, seerrEnabled) ->
                _uiState.update { state -> state.copy(isLoading = true, errorMessage = null) }
                if (query.isNotBlank()) {
                    val result = seerrRepository.search(query)
                    handleSearchResult(result)
                } else {
                    if (seerrEnabled) {
                        loadDiscoverSectionsInternal()
                    }
                    handleDiscoverResult()
                }
            }
        }
    }

    private fun handleSearchResult(result: ApiResult<com.rpeters.jellyfin.data.model.SeerrSearchResult>) {
        when (result) {
            is ApiResult.Success -> {
                _uiState.update { state ->
                    val q = state.query.trim()
                    val newRecent = if (q.isNotBlank()) {
                        (listOf(q) + state.recentSearches.filter { it != q }).take(6)
                    } else {
                        state.recentSearches
                    }
                    state.copy(
                        results = result.data.results,
                        isLoading = false,
                        recentSearches = newRecent,
                        tvAvailabilityByMediaId = emptyMap(),
                        loadingAvailabilityIds = emptySet(),
                        checkedTvItemIds = emptySet(),
                        currentPage = result.data.page,
                        totalPages = result.data.totalPages,
                    )
                }
            }
            is ApiResult.Error -> {
                val errorMessage = if (result.message.contains("not configured")) null else result.message
                _uiState.update { it.copy(errorMessage = errorMessage, isLoading = false) }
            }
            is ApiResult.Loading -> {
                _uiState.update { it.copy(isLoading = true) }
            }
        }
    }

    private fun handleDiscoverResult() {
        _uiState.update { state ->
            state.copy(
                results = emptyList(),
                isLoading = false,
                tvAvailabilityByMediaId = emptyMap(),
                loadingAvailabilityIds = emptySet(),
                checkedTvItemIds = emptySet(),
                currentPage = 1,
                totalPages = 1,
            )
        }
    }

    private suspend fun loadDiscoverSectionsInternal() {
        val prefs = seerrPreferences.value
        if (!prefs.isValid || !prefs.isEnabled) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        coroutineScope {
            val trendingDeferred = async { seerrRepository.getTrending() }
            val upcomingMoviesDeferred = async { seerrRepository.getUpcomingMovies() }
            val upcomingTvDeferred = async { seerrRepository.getUpcomingTv() }
            val popularMoviesDeferred = async { seerrRepository.getPopularMovies() }
            val popularTvDeferred = async { seerrRepository.getPopularTv() }

            val trendingResult = trendingDeferred.await()
            val upcomingMoviesResult = upcomingMoviesDeferred.await()
            val upcomingTvResult = upcomingTvDeferred.await()
            val popularMoviesResult = popularMoviesDeferred.await()
            val popularTvResult = popularTvDeferred.await()

            var hasError = false
            var errorMsg: String? = null

            val trendingList = when (trendingResult) {
                is ApiResult.Success -> trendingResult.data.results
                is ApiResult.Error -> {
                    if (trendingResult.message.contains("not configured").not()) {
                        hasError = true
                        errorMsg = trendingResult.message
                    }
                    emptyList()
                }
                else -> emptyList()
            }
            val upcomingMoviesList = when (upcomingMoviesResult) {
                is ApiResult.Success -> upcomingMoviesResult.data.results
                is ApiResult.Error -> {
                    if (upcomingMoviesResult.message.contains("not configured").not()) {
                        hasError = true
                        errorMsg = upcomingMoviesResult.message
                    }
                    emptyList()
                }
                else -> emptyList()
            }
            val upcomingTvList = when (upcomingTvResult) {
                is ApiResult.Success -> upcomingTvResult.data.results
                is ApiResult.Error -> {
                    if (upcomingTvResult.message.contains("not configured").not()) {
                        hasError = true
                        errorMsg = upcomingTvResult.message
                    }
                    emptyList()
                }
                else -> emptyList()
            }
            val popularMoviesList = when (popularMoviesResult) {
                is ApiResult.Success -> popularMoviesResult.data.results
                is ApiResult.Error -> {
                    if (popularMoviesResult.message.contains("not configured").not()) {
                        hasError = true
                        errorMsg = popularMoviesResult.message
                    }
                    emptyList()
                }
                else -> emptyList()
            }
            val popularTvList = when (popularTvResult) {
                is ApiResult.Success -> popularTvResult.data.results
                is ApiResult.Error -> {
                    if (popularTvResult.message.contains("not configured").not()) {
                        hasError = true
                        errorMsg = popularTvResult.message
                    }
                    emptyList()
                }
                else -> emptyList()
            }

            _uiState.update { state ->
                state.copy(
                    trending = trendingList,
                    upcomingMovies = upcomingMoviesList,
                    upcomingTv = upcomingTvList,
                    popularMovies = popularMoviesList,
                    popularTv = popularTvList,
                    isLoading = false,
                    errorMessage = if (hasError) errorMsg else state.errorMessage
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(query = query, errorMessage = null, successMessage = null) }
    }

    fun loadMoreResults() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        if (state.currentPage >= state.totalPages) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val query = state.query.trim()
            val nextPage = state.currentPage + 1
            val result = if (query.isNotBlank()) {
                seerrRepository.search(query, nextPage)
            } else {
                seerrRepository.getTrending(nextPage)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        results = it.results + result.data.results,
                        currentPage = result.data.page,
                        totalPages = result.data.totalPages,
                        isLoadingMore = false,
                    )
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoadingMore = false, errorMessage = result.message) }
                is ApiResult.Loading -> _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun requestMedia(item: SeerrMediaItem) {
        val state = _uiState.value
        if (state.isPluginConfigured) {
            if (item.mediaType == "tv") {
                // Let the user pick seasons even when routing through the plugin.
                initiateTvSeasonRequest(item, useSeerr = true)
            } else {
                requestSeasons(item, null)
            }
            return
        }
        val seerr = seerrPreferences.value
        val useSeerr = seerr.isValid && seerr.isEnabled
        when {
            item.mediaType == "movie" && !useSeerr -> {
                if (state.isRadarrConfigured) {
                    initiateMovieQualityRequest(item)
                } else {
                    _uiState.update {
                        it.copy(errorMessage = "No request service configured for movies. Enable Seerr, Radarr, or the Cinefin plugin in Settings → Media Requests.")
                    }
                }
            }
            item.mediaType == "movie" && useSeerr -> {
                _uiState.update {
                    it.copy(pendingMovieRequest = PendingMovieRequest(item, emptyList(), isUsingRadarrDirect = false))
                }
            }
            item.mediaType == "tv" -> {
                if (useSeerr || state.isSonarrConfigured) {
                    initiateTvSeasonRequest(item, useSeerr)
                } else {
                    _uiState.update {
                        it.copy(errorMessage = "No request service configured for TV shows. Enable Seerr, Sonarr, or the Cinefin plugin in Settings → Media Requests.")
                    }
                }
            }
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
                    pendingMovieRequest = PendingMovieRequest(item, profiles, isUsingRadarrDirect = true),
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
            if (seasons.isEmpty()) {
                // No season metadata available to build a selection dialog — fall back to
                // requesting everything via the configured backend (plugin/Seerr/Sonarr).
                _uiState.update { it.copy(requestingMediaId = null) }
                requestSeasons(item, null)
                return@launch
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

    fun confirmMovieRequest(item: SeerrMediaItem, qualityProfileId: Int?, is4k: Boolean = false) {
        val pending = _uiState.value.pendingMovieRequest
        _uiState.update { it.copy(pendingMovieRequest = null) }
        if (pending?.isUsingRadarrDirect == false) {
            requestSeasons(item, null, is4k)
            return
        }
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

    fun confirmTvRequest(item: SeerrMediaItem, selectedSeasons: List<Int>, qualityProfileId: Int?, is4k: Boolean = false) {
        val pending = _uiState.value.pendingTvRequest
        _uiState.update { it.copy(pendingTvRequest = null) }
        // Trust the routing decided when the dialog was opened, so a settings change while
        // the dialog is open can't send the selected quality profile down the wrong path.
        val useSeerr = pending?.isUsingSonarrDirect == false
        if (useSeerr) {
            requestSeasons(item, selectedSeasons, is4k)
        } else {
            viewModelScope.launch {
                val tvdbId = resolveTvdbId(item)
                if (tvdbId == null) {
                    _uiState.update { it.copy(errorMessage = "TVDB ID not available for ${item.displayTitle}") }
                    return@launch
                }

                // If we have availability data (e.g. Seerr credentials are stored but not
                // enabled for requesting), skip seasons that are already fully downloaded
                // instead of re-triggering a Sonarr search for them.
                val availability = _uiState.value.tvAvailabilityByMediaId[item.id]
                val seasonsToRequest = if (availability != null) {
                    val missingSeasons = availability.seasons.filter { it.hasMissingEpisodes }.map { it.seasonNumber }.toSet()
                    val filtered = selectedSeasons.filter { it in missingSeasons }
                    if (filtered.isEmpty()) {
                        _uiState.update {
                            it.copy(errorMessage = "Selected seasons are already fully available for ${item.displayTitle}")
                        }
                        return@launch
                    }
                    filtered
                } else {
                    selectedSeasons
                }

                _uiState.update { it.copy(requestingMediaId = item.id) }
                val result = sonarrRepository.addSeries(tvdbId, seasonsToRequest, qualityProfileId)
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
        if (!state.isPluginConfigured && !state.isSonarrConfigured) {
            _uiState.update {
                it.copy(errorMessage = "Configure Sonarr or the Cinefin plugin in Settings to request individual episodes")
            }
            return
        }
        if (tvdbId == null) {
            _uiState.update {
                it.copy(errorMessage = "TVDB ID unavailable for ${item.displayTitle} — try requesting the full season instead")
            }
            return
        }

        viewModelScope.launch {
            val episodeKey = "${item.id}:$seasonNumber:$episodeNumber"
            _uiState.update { it.copy(requestingMediaId = item.id, requestingSeasonKey = episodeKey) }

            // Prefer plugin, fall back to direct Sonarr — both are keyed by TVDB ID.
            val result: ApiResult<*> = if (_uiState.value.isPluginConfigured) {
                cinefinPluginRepository.requestEpisode(tvdbId.toString(), seasonNumber, episodeNumber)
            } else {
                sonarrRepository.requestEpisode(tvdbId, seasonNumber, episodeNumber)
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

    private fun requestSeasons(item: SeerrMediaItem, requestedSeasons: List<Int>?, is4k: Boolean = false) {
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
                        is4k = is4k,
                    )
                } else {
                    SeerrRequestRequest(
                        mediaType = item.mediaType,
                        mediaId = mediaId,
                        is4k = is4k,
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

            // 3. Direct Sonarr (TV only) — movies are handled via initiateMovieQualityRequest
            // before requestSeasons is ever reached, so this branch only needs to cover TV.
            val tvdbId = item.tvdbId
            val directResult: ApiResult<Unit> = if (item.mediaType == "tv" && tvdbId != null) {
                sonarrRepository.addSeries(tvdbId, requestedSeasons)
            } else {
                ApiResult.Error("No request service configured. Enable Seerr, Sonarr, or the Cinefin plugin in Settings → Media Requests.")
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
                        availabilitySemaphore.withPermit {
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

        val today = LocalDate.now()
        val seerrEpisodes = seerrSeason.episodes
            .mapNotNull { episode ->
                val number = episode.episodeNumber ?: return@mapNotNull null
                SeerrEpisodeMetadata(
                    number = number,
                    title = episode.name ?: "Episode $number",
                    airDate = episode.airDate,
                )
            }

        val releasedEpisodeNumbers = seerrEpisodes
            .filter { episode -> episode.hasAiredBy(today) || episode.number in localEpisodeByNumber }
            .map { it.number }

        val episodeNumbers = when {
            seerrEpisodes.isNotEmpty() -> releasedEpisodeNumbers
            seerrSeason.episodeCount != null && seerrSeason.episodeCount > 0 -> (1..seerrSeason.episodeCount).toList()
            localEpisodeByNumber.isNotEmpty() -> localEpisodeByNumber.keys.sorted()
            else -> emptyList()
        }

        if (episodeNumbers.isEmpty()) return null

        val titleByNumber = seerrEpisodes.associate { it.number to it.title }
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

    private fun SeerrEpisodeMetadata.hasAiredBy(today: LocalDate): Boolean {
        val releaseDate = airDate?.takeIf { it.length >= 10 }?.take(10)?.let { date ->
            try {
                LocalDate.parse(date)
            } catch (_: DateTimeParseException) {
                null
            }
        }
        return releaseDate == null || !releaseDate.isAfter(today)
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
        val exactTitleMatches = localSeries.filter { it.name?.normalizedTitle() == seerrTitle }

        val exactMatch = if (seerrYear != null) {
            exactTitleMatches.firstOrNull { series ->
                series.productionYear == null || series.productionYear == seerrYear
            }
        } else {
            // Seerr didn't provide a year to disambiguate — only auto-match if the title is
            // unambiguous, otherwise fall through rather than risk picking the wrong series.
            exactTitleMatches.singleOrNull()
        }

        return exactMatch ?: localSeries.firstOrNull { series ->
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

    fun loadMyRequests(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoadingMyRequests) return
        if (_uiState.value.myRequestsLoaded && !forceRefresh) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMyRequests = true) }
            when (val result = seerrRepository.getRequests(filter = "all", page = 1, take = 50)) {
                is ApiResult.Success -> {
                    val items = result.data.results
                        .sortedByDescending { it.createdAt }
                        .map { RequestHistoryItem(request = it) }
                    _uiState.update {
                        it.copy(myRequests = items, isLoadingMyRequests = false, myRequestsLoaded = true)
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoadingMyRequests = false,
                        myRequestsLoaded = true,
                        errorMessage = result.message.ifBlank { "Failed to load requests" },
                    )
                }
                is ApiResult.Loading -> _uiState.update { it.copy(isLoadingMyRequests = false) }
            }
        }
    }

    fun cancelRequest(requestId: Int) {
        if (_uiState.value.cancelingRequestId != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(cancelingRequestId = requestId) }
            when (val result = seerrRepository.deleteRequest(requestId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        myRequests = it.myRequests.filterNot { entry -> entry.request.id == requestId },
                        cancelingRequestId = null,
                        successMessage = "Request canceled",
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        cancelingRequestId = null,
                        errorMessage = result.message.ifBlank { "Failed to cancel request" },
                    )
                }
                is ApiResult.Loading -> _uiState.update { it.copy(cancelingRequestId = null) }
            }
        }
    }

    fun loadRequestMediaDetails(requestId: Int) {
        val entry = _uiState.value.myRequests.firstOrNull { it.request.id == requestId } ?: return
        if (entry.title != null || entry.isLoadingDetails) return
        val media = entry.request.media
        val tmdbId = media.tmdbId ?: return

        _uiState.update { state ->
            state.copy(
                myRequests = state.myRequests.map {
                    if (it.request.id == requestId) it.copy(isLoadingDetails = true) else it
                },
            )
        }

        viewModelScope.launch {
            val (title, posterPath) = if (media.mediaType == "movie") {
                when (val result = seerrRepository.getMovieDetails(tmdbId)) {
                    is ApiResult.Success -> result.data.title to result.data.posterPath
                    else -> null to null
                }
            } else {
                when (val result = seerrRepository.getTvDetails(tmdbId)) {
                    is ApiResult.Success -> result.data.name to result.data.posterPath
                    else -> null to null
                }
            }
            _uiState.update { state ->
                state.copy(
                    myRequests = state.myRequests.map {
                        if (it.request.id == requestId) {
                            it.copy(title = title, posterPath = posterPath, isLoadingDetails = false)
                        } else {
                            it
                        }
                    },
                )
            }
        }
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
