package com.ghost.tv.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tv.data.local.entity.*
import com.ghost.tv.data.repository.XtreamRepository
import com.ghost.tv.domain.model.DownloadContentType
import com.ghost.tv.domain.model.DownloadRequest
import com.ghost.tv.domain.model.DownloadStatus
import com.ghost.tv.domain.model.Result
import com.ghost.tv.domain.repository.DownloadManager
import com.ghost.tv.domain.repository.PlaybackHistoryRepository
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

    val series = seriesBox.query().equal(Series_.id, seriesId).build().flow().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
                        runCatching {
                            seriesBox.query().notEqual(Series_.id, s.id).build().use { it.find(0, 12) }
                        }.getOrDefault(emptyList())
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
                // Fetch from repository which updates the object and saves it to DB
                xtreamRepository.fetchSeriesDetails(s)
            }
        }
    }

    fun selectSeason(swE: SeasonWithEpisodes) {
        _selectedSeasonNumber.value = swE.season.seasonNumber
    }

    fun toggleFavorite(addedMsg: String = "Added to favorites", removedMsg: String = "Removed from favorites") {
        viewModelScope.launch {
            val current = series.value ?: return@launch
            current.isFavorite = !current.isFavorite
            seriesBox.put(current)
            _userMessage.emit(if (current.isFavorite) addedMsg else removedMsg)
        }
    }

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    fun downloadEpisode(episode: Episode) {
        viewModelScope.launch {
            val s = series.value ?: return@launch
            val account = runCatching {
                accountBox.query().equal(XtreamAccount_.isActive, true).build().use { it.findFirst() }
            }.getOrNull() ?: return@launch
            
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
