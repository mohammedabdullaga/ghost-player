package com.eliteprime.app.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
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

    val series = seriesBox.query().equal(Series_.id, seriesId).build().flow().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val seasons = seasonBox.query().equal(Season_.seriesId, seriesId).build().flow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSeason = MutableStateFlow<Season?>(null)
    val selectedSeason = _selectedSeason.asStateFlow()

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
        _selectedSeason.value = season
    }
}
