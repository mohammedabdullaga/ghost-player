package com.eliteprime.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.Property
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val boxStore: BoxStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.value = SearchUiState(isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Perform search in background
            val results = withContext(Dispatchers.Default) {
                val live = performIntelligentSearch(LiveStream::class.java, LiveStream_.name, trimmed)
                val movies = performIntelligentSearch(Movie::class.java, Movie_.name, trimmed)
                val series = performIntelligentSearch(Series::class.java, Series_.name, trimmed)
                Triple(live, movies, series)
            }

            _uiState.value = SearchUiState(
                isLoading = false,
                channels = results.first,
                movies = results.second,
                series = results.third
            )
        }
    }

    private fun <T> performIntelligentSearch(
        entityClass: Class<T>,
        nameProperty: Property<T>,
        query: String
    ): List<T> {
        val box = boxStore.boxFor(entityClass)
        
        // 1. Priority 1: Exact matches or Starts With (Best for "FROM" series)
        val startMatches = box.query()
            .startsWith(nameProperty, query, StringOrder.CASE_INSENSITIVE)
            .build().find()
            
        // 2. Priority 2: Contains but NOT starts with
        val containsMatches = box.query()
            .contains(nameProperty, query, StringOrder.CASE_INSENSITIVE)
            .build().find()
            
        // Merge results: Starts With first, then the rest
        val startIds = startMatches.map { getEntityId(it) }.toSet()
        val uniqueContains = containsMatches.filter { getEntityId(it) !in startIds }
        
        return startMatches + uniqueContains
    }

    private fun getEntityId(entity: Any?): Long {
        return when (entity) {
            is LiveStream -> entity.id
            is Movie -> entity.id
            is Series -> entity.id
            else -> 0L
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
