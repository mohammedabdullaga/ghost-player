package com.eliteprime.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val boxStore: BoxStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        if (query.length < 2) {
            _uiState.value = SearchUiState(isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val liveResults = boxStore.boxFor(LiveStream::class.java).query()
                .contains(LiveStream_.name, query, StringOrder.CASE_INSENSITIVE)
                .build().find()
                
            val movieResults = boxStore.boxFor(Movie::class.java).query()
                .contains(Movie_.name, query, StringOrder.CASE_INSENSITIVE)
                .build().find()
                
            val seriesResults = boxStore.boxFor(Series::class.java).query()
                .contains(Series_.name, query, StringOrder.CASE_INSENSITIVE)
                .build().find()

            _uiState.value = SearchUiState(
                isLoading = false,
                channels = liveResults,
                movies = movieResults,
                series = seriesResults
            )
        }
    }

    fun onSearchSubmitted() {
        // Handle search submission if needed
    }

    fun clearRecentQueries() {
        // Handle clearing recent queries
    }
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val channels: List<LiveStream> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList()
)
