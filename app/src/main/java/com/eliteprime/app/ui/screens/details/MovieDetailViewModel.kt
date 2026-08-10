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
class MovieDetailViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val xtreamRepository: XtreamRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: Long = savedStateHandle.get<Long>("id") ?: 0
    private val movieBox = boxStore.boxFor(Movie::class.java)

    val movie = movieBox.query().equal(Movie_.id, movieId).build().flow().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val relatedMovies = movie.flatMapLatest { m ->
        if (m == null) flowOf(emptyList())
        else {
            // Find other movies in the same category
            movieBox.query()
                .equal(Movie_.remoteCategoryId, m.remoteCategoryId ?: "", StringOrder.CASE_INSENSITIVE)
                .notEqual(Movie_.id, m.id)
                .build()
                .flow()
                .map { it.take(12) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            val m = movieBox.get(movieId)
            if (m != null) {
                xtreamRepository.fetchMovieDetails(m)
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = movie.value ?: return@launch
            current.isFavorite = !current.isFavorite
            movieBox.put(current)
        }
    }
}
