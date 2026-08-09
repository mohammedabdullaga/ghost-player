package com.eliteprime.app.ui.screens.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MovieViewModel @Inject constructor(
    private val boxStore: BoxStore
) : ViewModel() {

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<MovieUiState> = combine(
        categoryBox.query().equal(Category_.type, "movie", StringOrder.CASE_INSENSITIVE).build().flow(),
        _selectedCategory,
        _searchQuery.debounce(300)
    ) { categories, selectedCat, query ->
        val currentSelected = selectedCat ?: categories.firstOrNull()

        val movies = if (currentSelected != null) {
            val qb = movieBox.query()
            qb.equal(Movie_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
            if (query.isNotEmpty()) {
                qb.contains(Movie_.name, query, StringOrder.CASE_INSENSITIVE)
            }
            qb.build().find()
        } else {
            emptyList()
        }

        MovieUiState(
            categories = categories,
            selectedCategory = currentSelected,
            filteredMovies = movies,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MovieUiState())

    fun selectCategory(category: Category) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}

data class MovieUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val filteredMovies: List<Movie> = emptyList(),
    val isLoading: Boolean = true
)
