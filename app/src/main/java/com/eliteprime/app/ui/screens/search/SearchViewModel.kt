package com.eliteprime.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.Property
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        // Guard: if the query hasn't changed, do nothing (prevents re-execution on recomposition)
        if (query == _query.value) return
        _query.value = query
        _uiState.value = _uiState.value.copy(query = query)
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.value = _uiState.value.copy(isLoading = false, channels = emptyList(), movies = emptyList(), series = emptyList())
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Debounce search
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
                series = results.third,
                query = trimmed,
                activeFilter = _uiState.value.activeFilter
            )
        }
    }

    private fun <T> performIntelligentSearch(
        entityClass: Class<T>,
        nameProperty: Property<T>,
        query: String
    ): List<T> {
        val box = boxStore.boxFor(entityClass)
        val queryLower = query.lowercase()

        // 1. Fetch potential matches from DB (broad contains) - Capped to 500 items
        val allMatches = runCatching {
            box.query()
                .contains(nameProperty, query, StringOrder.CASE_INSENSITIVE)
                .build().use { it.find(0, 500) }
        }.getOrDefault(emptyList())

        // 2. Refine, filter and sort in memory using Sequence to avoid duplicate copies
        return allMatches.asSequence().map { entity ->
            val originalName = getEntityName(entity)
            
            // Clean the title: Ignore anything before the (-) symbol
            val searchableName = originalName.getSearchablePart()
            val searchableLower = searchableName.lowercase()
            
            var score = 0
            if (searchableLower.startsWith(queryLower)) {
                score = 100 // Highest priority: Keyword is the first one in the cleaned title
            } else if (searchableLower.contains(queryLower)) {
                score = 50 // High priority: Keyword is in the title but not at start
            } else if (originalName.lowercase().contains(queryLower)) {
                score = 10 // Low priority: Keyword was only in the prefix part
            }
            
            entity to score
        }
        .filter { it.second >= 50 } // Ignore results where keyword is only in the prefix (score 10)
        .sortedWith(compareByDescending<Pair<T, Int>> { it.second }
            .thenBy { getEntityName(it.first).getSearchablePart().length } // Prefer shorter matches
            .thenBy { getEntityName(it.first) })
        .map { it.first }
        .toList()
    }

    private fun String.getSearchablePart(): String {
        return when {
            this.contains(" - ") -> this.substringAfterLast(" - ").trim()
            this.contains("-") -> {
                val parts = this.split("-")
                // If it's something like "fr-4k-title", we take the last part
                // But avoid breaking "Spider-Man" (if there's only one dash and no spaces, it might be a single word title)
                if (parts.size > 2) parts.last().trim() 
                else if (parts.size == 2 && this.length > 20) parts.last().trim() // Likely "Category-Title"
                else this // Likely a dashed title like "Spider-Man"
            }
            else -> this.trim()
        }
    }

    private fun getEntityName(entity: Any?): String {
        return when (entity) {
            is LiveStream -> entity.name
            is Movie -> entity.name
            is Series -> entity.name
            else -> ""
        }
    }

    fun setFilter(filter: SearchResultFilter) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
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
    val series: List<Series> = emptyList(),
    val query: String = "",
    val activeFilter: SearchResultFilter = SearchResultFilter.ALL
)

enum class SearchResultFilter { ALL, MOVIES, SERIES, LIVE_TV }
