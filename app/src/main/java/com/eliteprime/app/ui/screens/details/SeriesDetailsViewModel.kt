package com.eliteprime.app.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesDetailsViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val seriesId: Long = savedStateHandle.get<Long>("id") ?: 0
    private val seriesBox = boxStore.boxFor(Series::class.java)
    private val seasonBox = boxStore.boxFor(Season::class.java)
    private val episodeBox = boxStore.boxFor(Episode::class.java)

    val series = seriesBox.query().equal(Series_.id, seriesId).build().flow().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedSeasonNumber = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val seasons = combine(
        seasonBox.query().equal(Season_.seriesId, seriesId).build().flow(),
        episodeBox.query().equal(Episode_.seriesId, seriesId).build().flow()
    ) { seasonList, _ ->
        // Important: Force reload relation to get latest episodes
        seasonList.sortedBy { it.seasonNumber }.onEach { season ->
            season.episodes.reset() 
            season.episodes.sortBy { it.episodeNumber }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedSeason = combine(seasons, _selectedSeasonNumber) { list, number ->
        if (list.isEmpty()) null
        else if (number == null) list.firstOrNull()
        else list.find { it.seasonNumber == number } ?: list.firstOrNull()
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
                        // Fallback: if same category is empty, just show any other series
                        seriesBox.query().notEqual(Series_.id, s.id).build().find(0, 12)
                    } else {
                        list.take(12)
                    }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            val s = seriesBox.get(seriesId)
            if (s != null) {
                xtreamRepository.fetchSeriesDetails(s)
            }
        }
    }

    fun selectSeason(season: Season) {
        _selectedSeasonNumber.value = season.seasonNumber
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = series.value ?: return@launch
            current.isFavorite = !current.isFavorite
            seriesBox.put(current)
        }
    }
}
