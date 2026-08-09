package com.eliteprime.app.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val boxStore: BoxStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val movieId: Long = savedStateHandle.get<Long>("id") ?: 0
    private val movieBox = boxStore.boxFor(Movie::class.java)

    val movie = movieBox.query().equal(Movie_.id, movieId).build().flow().map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
