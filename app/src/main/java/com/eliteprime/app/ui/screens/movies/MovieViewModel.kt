package com.eliteprime.app.ui.screens.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
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
        private const val FAST_LOAD_LIMIT = 500L
    }

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val historyBox = boxStore.boxFor(PlaybackHistory::class.java)

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")

    private val categoriesFlow = categoryBox.query()
        .equal(Category_.type, "movie", StringOrder.CASE_INSENSITIVE)
        .build()
        .flow()
        .map { providerCats ->
            val virtualCats = listOf(
                Category(id = CAT_ID_FAVORITES, categoryName = "★ Favorites", isVirtual = true),
                Category(id = CAT_ID_CONTINUE, categoryName = "🕒 Continue Watching", isVirtual = true),
                Category(id = CAT_ID_ALL, categoryName = "All Movies", isVirtual = true)
            )
            (virtualCats + providerCats.filter { !it.isHidden }).sortedBy { it.order }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<MovieUiState> = combine(
        categoriesFlow,
        _selectedCategoryId,
        _searchQuery.debounce(300)
    ) { allCategories, selectedId, query ->
        
        val currentId = selectedId ?: CAT_ID_ALL
        val currentSelected = allCategories.find { it.id == currentId } ?: allCategories.find { it.id == CAT_ID_ALL } ?: allCategories.first()

        val movies = if (query.isNotEmpty()) {
            val qb = movieBox.query().contains(Movie_.name, query, StringOrder.CASE_INSENSITIVE)
            if (currentId != CAT_ID_ALL && currentId > 0) {
                qb.equal(Movie_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
            }
            qb.build().find(0, FAST_LOAD_LIMIT)
        } else {
            when (currentId) {
                CAT_ID_ALL -> movieBox.query().build().find(0, FAST_LOAD_LIMIT)
                CAT_ID_FAVORITES -> movieBox.query().equal(Movie_.isFavorite, true).build().find()
                CAT_ID_CONTINUE -> {
                    val histories = historyBox.query()
                        .equal(PlaybackHistory_.type, "movie", StringOrder.CASE_INSENSITIVE)
                        .order(PlaybackHistory_.lastPlayedAt, QueryBuilder.DESCENDING)
                        .build()
                        .find()
                    
                    val movieIds = histories.map { it.streamId }
                    val moviesList = movieBox.get(movieIds.toLongArray()).filterNotNull()
                    val movieMap = moviesList.associateBy { it.id }
                    movieIds.mapNotNull { movieMap[it] }
                }
                else -> {
                    movieBox.query()
                        .equal(Movie_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .find()
                }
            }
        }

        MovieUiState(
            categories = allCategories,
            selectedCategory = currentSelected,
            filteredMovies = movies,
            isLoading = false
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MovieUiState(isLoading = true))

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
