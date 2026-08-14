package com.eliteprime.app.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.domain.model.DownloadContentType
import com.eliteprime.app.domain.model.DownloadRequest
import com.eliteprime.app.domain.model.DownloadStatus
import com.eliteprime.app.domain.model.Result
import com.eliteprime.app.domain.repository.DownloadManager
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeasonWithEpisodes(
    val season: Season,
    val episodes: List<Episode>
)

@HiltViewModel
class SeriesDetailsViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository,
    private val downloadManager: DownloadManager,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seriesId: Long = savedStateHandle.get<Long>("id") ?: 0
    private val seriesBox = boxStore.boxFor(Series::class.java)
    private val seasonBox = boxStore.boxFor(Season::class.java)
    private val episodeBox = boxStore.boxFor(Episode::class.java)
    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)

    private val _dynamicMetadata = MutableStateFlow<Series?>(null)

    val series = combine(
        seriesBox.query().equal(Series_.id, seriesId).build().flow().map { it.firstOrNull() },
        _dynamicMetadata
    ) { local, dynamic ->
        if (local == null) return@combine null
        if (dynamic != null && dynamic.id == local.id) {
            local.apply {
                plot = dynamic.plot
                cast = dynamic.cast
                director = dynamic.director
                genre = dynamic.genre
                releaseDate = dynamic.releaseDate
                rating = dynamic.rating
            }
        }
        local
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val seasons: StateFlow<List<SeasonWithEpisodes>> = combine(
        seasonBox.query().equal(Season_.seriesId, seriesId).build().flow(),
        episodeBox.query().equal(Episode_.seriesId, seriesId).build().flow()
    ) { seasonList, _ ->
        seasonList.sortedBy { it.seasonNumber }.map { season ->
            SeasonWithEpisodes(
                season = season,
                episodes = season.episodes.toList().sortedBy { it.episodeNumber }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val resumeEpisode = seasons.map { seasonList ->
        var lastWatched: Episode? = null
        for (swE in seasonList) {
            for (episode in swE.episodes) {
                if (episode.watchProgress > 0) {
                    if (lastWatched == null || episode.lastWatchedAt > lastWatched.lastWatchedAt) {
                        lastWatched = episode
                    }
                }
            }
        }
        
        // Auto-select season based on resume episode if no season is selected yet
        if (lastWatched != null && _selectedSeasonNumber.value == null) {
            _selectedSeasonNumber.value = lastWatched.seasonNumber
        }
        
        lastWatched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedSeason = combine(seasons, _selectedSeasonNumber) { list, number ->
        if (list.isEmpty()) null
        else if (number == null) list.firstOrNull()
        else list.find { it.season.seasonNumber == number } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val relatedSeries = series.flatMapLatest { s ->
        if (s == null) flowOf(emptyList())
        else {
            val catId = s.remoteCategoryId ?: ""
            seriesBox.query()
                .apply {
                    if (catId.isNotEmpty()) {
                        equal(Series_.remoteCategoryId, catId, StringOrder.CASE_INSENSITIVE)
                    }
                }
                .notEqual(Series_.id, s.id)
                .build()
                .flow()
                .map { list ->
                    if (list.isEmpty()) {
                        seriesBox.query().notEqual(Series_.id, s.id).build().find(0, 12)
                    } else {
                        list.take(12)
                    }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val episodeDownloads = downloadManager.observeAllDownloads()
        .map { downloads ->
            downloads.filter { it.seriesId == seriesId }
                .associateBy { it.contentId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            val s = seriesBox.get(seriesId)
            if (s != null) {
                // Fetch from repository which updates the object with transient fields
                xtreamRepository.fetchSeriesDetails(s)
                // Store in dynamic state
                _dynamicMetadata.value = s
            }
        }
    }

    fun selectSeason(swE: SeasonWithEpisodes) {
        _selectedSeasonNumber.value = swE.season.seasonNumber
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = series.value ?: return@launch
            current.isFavorite = !current.isFavorite
            seriesBox.put(current)
        }
    }

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    fun downloadEpisode(episode: Episode) {
        viewModelScope.launch {
            val s = series.value ?: return@launch
            val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@launch
            
            val request = DownloadRequest(
                providerId = account.id,
                contentType = DownloadContentType.SERIES_EPISODE,
                contentId = episode.id,
                contentName = "${s.name} - ${episode.title}",
                streamUrl = "", 
                containerExtension = episode.containerExtension,
                posterUrl = episode.coverUrl ?: s.cover,
                seriesId = s.id,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber
            )
            val result = downloadManager.enqueueDownload(request)
            if (result is Result.Success) {
                _userMessage.emit("Episode added to download queue")
            } else if (result is Result.Error) {
                _userMessage.emit(result.message)
            }
        }
    }
}
