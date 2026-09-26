package com.eliteprime.app.ui.screens.movies

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
import com.eliteprime.app.player.GlobalPlayerState
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieViewModel @Inject constructor(
    application: Application,
    private val boxStore: BoxStore,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    val globalPlayerState: GlobalPlayerState,
    private val savedHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        const val CAT_ID_RECENTLY_ADDED = -3L
        const val CAT_ID_FAVORITES = -1L
        const val CAT_ID_CONTINUE = -2L
        private const val FAST_LOAD_LIMIT = 500L
        private const val RECENTLY_ADDED_LIMIT = 100L
    }

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)

    private val _selectedCategoryId = MutableStateFlow<Long?>(savedHandle["selectedCat"])
    private val _searchQuery = MutableStateFlow(savedHandle["search"] ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _page = MutableStateFlow(0)
    private val PAGE_SIZE = 120

    // Survive navigation: sidebar/grid state in ViewModel, not remember
    private val _isSidebarHidden = MutableStateFlow(false)
    val isSidebarHidden: StateFlow<Boolean> = _isSidebarHidden
    private val _isGridFocused = MutableStateFlow(false)
    val isGridFocused: StateFlow<Boolean> = _isGridFocused

    // Focus position state — backed by SavedStateHandle for Lite mode survival
    private val _lastFocusedCategoryId = MutableStateFlow<Long?>(savedHandle["focusCat"])
    val lastFocusedCategoryId: StateFlow<Long?> = _lastFocusedCategoryId.asStateFlow()
    private val _lastFocusedItemId = MutableStateFlow<Long?>(savedHandle["focusItem"])
    val lastFocusedItemId: StateFlow<Long?> = _lastFocusedItemId.asStateFlow()

    private var browsingTrace: Trace? = null

    // True lazy: counts are loaded per-category only when item scrolls into view
    private val _categoryCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val loadedCountIds = mutableSetOf<Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoriesFlow = categoryBox.query()
        .equal(Category_.type, "movie", StringOrder.CASE_INSENSITIVE)
        .build()
        .flow()
        .map { providerCats ->
            val virtualCats = listOf(
                Category(id = CAT_ID_RECENTLY_ADDED, categoryName = getApplication<Application>().getString(R.string.nav_recently_added), isVirtual = true, order = -100),
                Category(id = CAT_ID_CONTINUE, categoryName = getApplication<Application>().getString(R.string.home_continue_watching), isVirtual = true, order = -99),
                Category(id = CAT_ID_FAVORITES, categoryName = getApplication<Application>().getString(R.string.nav_favorites), isVirtual = true, order = -98)
            )
            val filteredProvider = providerCats.filter { 
                !it.isHidden && it.id > 0 && it.categoryName.lowercase() != "favorites" && it.categoryName.lowercase() != "recently added"
            }
            (virtualCats + filteredProvider).sortedBy { it.order }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery
        .flatMapLatest { query ->
            if (query.isEmpty()) flowOf(query)
            else flowOf(query).debounce(300)
        }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredMoviesFlow = combine(
        _selectedCategoryId,
        debouncedSearchQuery,
        categoriesFlow,
        _page,
        playbackHistoryRepository.getRecentlyWatched(50)
    ) { selectedId, query, allCategories, page, history ->
        val currentId = selectedId ?: CAT_ID_RECENTLY_ADDED
        val currentSelected = allCategories.find { it.id == currentId } ?: allCategories.firstOrNull() ?: return@combine emptyList()
        val limit = (page + 1) * PAGE_SIZE

        if (query.isNotEmpty()) {
            runCatching {
                movieBox.query()
                    .contains(Movie_.name, query, StringOrder.CASE_INSENSITIVE)
                    .order(Movie_.order)
                    .build()
                    .use { it.find(0, limit.toLong()) }
            }.getOrDefault(emptyList())
        } else {
            runCatching {
                when (currentId) {
                    CAT_ID_RECENTLY_ADDED -> {
                        movieBox.query()
                            .order(Movie_.added, QueryBuilder.DESCENDING)
                            .order(Movie_.id, QueryBuilder.DESCENDING)
                            .build()
                            .use { it.find(0, RECENTLY_ADDED_LIMIT) }
                    }
                    CAT_ID_FAVORITES -> movieBox.query()
                        .equal(Movie_.isFavorite, true)
                        .order(Movie_.order)
                        .build().use { it.find(0, limit.toLong()) }
                    CAT_ID_CONTINUE -> {
                        val movieIds = history.filter { it.type == "movie" }
                            .map { it.streamId }
                        val moviesList = movieBox.get(movieIds.toLongArray()).filterNotNull()
                        val movieMap = moviesList.associateBy { it.id }
                        movieIds.distinct().mapNotNull { movieMap[it] }
                    }
                    else -> {
                        movieBox.query()
                            .equal(Movie_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
                            .order(Movie_.order)
                            .build()
                            .use { it.find(0, limit.toLong()) }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }.onStart { emit(emptyList()) }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MovieUiState> = combine(
        categoriesFlow,
        _selectedCategoryId,
        filteredMoviesFlow,
        _categoryCounts
    ) { allCategories, selectedId, filteredMovies, counts ->
        
        if (filteredMovies.isNotEmpty() && browsingTrace != null) {
            browsingTrace?.stop()
            browsingTrace = null
        }

        val currentId = selectedId ?: CAT_ID_RECENTLY_ADDED
        val currentSelected = allCategories.find { it.id == currentId } ?: allCategories.firstOrNull()

        MovieUiState(
            categories = allCategories,
            categoryCounts = counts,
            selectedCategory = currentSelected,
            filteredMovies = filteredMovies,
            isLoading = false
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MovieUiState(isLoading = true))

    /** Lazy: call this from the compose item — runs count() only for this category, one-shot */
    fun loadCategoryCount(category: Category) {
        if (!loadedCountIds.add(category.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val count = runCatching {
                when (category.id) {
                    CAT_ID_RECENTLY_ADDED -> movieBox.query()
                        .order(Movie_.added, QueryBuilder.DESCENDING)
                        .build().use { minOf(it.count(), RECENTLY_ADDED_LIMIT).toInt() }
                    CAT_ID_FAVORITES -> movieBox.query()
                        .equal(Movie_.isFavorite, true)
                        .build().use { it.count().toInt() }
                    CAT_ID_CONTINUE -> playbackHistoryRepository.getRecentlyWatched(50)
                        .first().count { it.type == "movie" }
                    else -> movieBox.query()
                        .equal(Movie_.remoteCategoryId, category.categoryId, StringOrder.CASE_INSENSITIVE)
                        .build().use { it.count().toInt() }
                }
            }.getOrElse { e ->
                Log.e("MovieViewModel", "Error loading count for category ${category.categoryName}", e)
                loadedCountIds.remove(category.id)
                0
            }
            if (count > 0) {
                _categoryCounts.update { it + (category.id to count) }
            }
        }
    }

    fun selectCategory(category: Category) {
        if (_selectedCategoryId.value == category.id && _lastFocusedItemId.value != null) {
            return
        }
        browsingTrace = FirebasePerformance.getInstance().newTrace("movie_category_load_time")
        browsingTrace?.start()
        _page.value = 0
        _selectedCategoryId.value = category.id
        savedHandle["selectedCat"] = category.id
        if (_searchQuery.value.isNotEmpty()) {
            _searchQuery.value = ""
            savedHandle["search"] = ""
        }
        _lastFocusedItemId.value = null
    }

    fun selectCategoryForRestore(category: Category) {
        browsingTrace = FirebasePerformance.getInstance().newTrace("movie_category_load_time")
        browsingTrace?.start()
        _page.value = 0
        _selectedCategoryId.value = category.id
        savedHandle["selectedCat"] = category.id
        if (_searchQuery.value.isNotEmpty()) {
            _searchQuery.value = ""
            savedHandle["search"] = ""
        }
    }
    fun recordFocusedCategory(categoryId: Long) {
        _lastFocusedCategoryId.value = categoryId
        savedHandle["focusCat"] = categoryId
    }

    fun recordFocusedItem(itemId: Long) {
        _lastFocusedItemId.value = itemId
        savedHandle["focusItem"] = itemId
    }

    fun clearFocusedItem() {
        _lastFocusedItemId.value = null
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) {
            _page.value = 0
            _searchQuery.value = query
            savedHandle["search"] = query
        }
    }

    fun loadMore() {
        Log.i("MovieVM", "Loading more items, current page: ${_page.value}")
        _page.value++
    }

    fun toggleFavorite(movie: Movie) {
        movie.isFavorite = !movie.isFavorite
        movieBox.put(movie)
    }

    fun removeFromHistory(movie: Movie) {
        viewModelScope.launch {
            val accountBox = boxStore.boxFor(XtreamAccount::class.java)
            val activeAccount = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst()
            activeAccount?.let { account ->
                playbackHistoryRepository.deleteHistory(
                    streamId = movie.id,
                    type = "movie",
                    providerId = account.id
                )
            }
        }
    }

    fun enterFullScreen() {
        _isSidebarHidden.value = true
        _isGridFocused.value = true
    }

    fun showSidebar() {
        _isSidebarHidden.value = false
        _isGridFocused.value = false
    }
}

data class MovieUiState(
    val categories: List<Category> = emptyList(),
    val categoryCounts: Map<Long, Int> = emptyMap(),
    val selectedCategory: Category? = null,
    val filteredMovies: List<Movie> = emptyList(),
    val isLoading: Boolean = true
)