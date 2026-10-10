package com.ghost.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tv.data.local.entity.Movie
import com.ghost.tv.data.local.entity.Movie_
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val boxStore: BoxStore
) : ViewModel() {

    private val _trendingMovies = MutableStateFlow<List<Movie>>(emptyList())
    val trendingMovies: StateFlow<List<Movie>> = _trendingMovies.asStateFlow()

    init {
        loadTrending()
    }

    private fun loadTrending() {
        viewModelScope.launch {
            val movieBox = boxStore.boxFor(Movie::class.java)
            // Query for trending movies ordered by rank
            val trending = movieBox.query()
                .equal(Movie_.isTrendingThisWeek, true)
                .order(Movie_.trendingRank)
                .build()
                .find()
            _trendingMovies.value = trending
        }
    }
}
