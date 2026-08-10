package com.eliteprime.app.ui.screens.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MovieViewModel @Inject constructor(
    private val boxStore: BoxStore
) : ViewModel() {

    companion object {
        const val CAT_ID_FAVORITES = -1L
        const val CAT_ID_CONTINUE = -2L
        const val CAT_ID_ALL = -3L
    }

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val historyBox = boxStore.boxFor(PlaybackHistory::class.java)

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<MovieUiState> = combine(
        categoryBox.query().equal(Category_.type, "movie", StringOrder.CASE_INSENSITIVE).build().flow(),
        _selectedCategoryId,
        _searchQuery.debounce(300)
    ) { providerCats: List<Category>, selectedId: Long?, query: String ->
        
        val virtualCats = listOf(
            Category(id = CAT_ID_FAVORITES, categoryName = "★ Favorites", isVirtual = true),
            Category(id = CAT_ID_CONTINUE, categoryName = "🕒 Continue Watching", isVirtual = true),
            Category(id = CAT_ID_ALL, categoryName = "All Movies", isVirtual = true)
        )

        val allCategories = virtualCats + providerCats.filter { !it.isHidden }.sortedBy { it.order }
        
        val currentId = selectedId ?: CAT_ID_ALL
        val currentSelected = allCategories.find { it.id == currentId } ?: virtualCats.last()

        val baseMovies = when (currentId) {
            CAT_ID_ALL -> movieBox.query().build().find()
            CAT_ID_FAVORITES -> movieBox.query().equal(Movie_.isFavorite, true).build().find()
            CAT_ID_CONTINUE -> {
                val histories = historyBox.query()
                    .equal(PlaybackHistory_.type, "movie", StringOrder.CASE_INSENSITIVE)
                    .order(PlaybackHistory_.lastPlayedAt, QueryBuilder.DESCENDING)
                    .build()
                    .find()
                
                val movieIds = histories.map { it.streamId }
                // Fetch movies
                val moviesList = movieBox.get(movieIds.toLongArray()).filterNotNull()
                val movieMap = moviesList.associateBy { it.id }
                movieIds.mapNotNull { movieMap[it] }
            }
            else -> {
                movieBox.query().equal(Movie_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE).build().find()
            }
        }

        val filteredMovies = if (query.isEmpty()) baseMovies else {
            baseMovies.filter { it.name.contains(query, ignoreCase = true) }
        }

        MovieUiState(
            categories = allCategories,
            selectedCategory = currentSelected,
            filteredMovies = filteredMovies,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MovieUiState())

    fun selectCategory(category: Category) {
        _selectedCategoryId.value = category.id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(movie: Movie) {
        movie.isFavorite = !movie.isFavorite
        movieBox.put(movie)
    }
}

data class MovieUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val filteredMovies: List<Movie> = emptyList(),
    val isLoading: Boolean = true
)
