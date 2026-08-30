package com.eliteprime.app.ui.screens.series

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
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
class SeriesViewModel @Inject constructor(
    application: Application,
    private val boxStore: BoxStore,
    private val playbackHistoryRepository: PlaybackHistoryRepository
) : AndroidViewModel(application) {

    companion object {
        const val CAT_ID_RECENTLY_ADDED = -3L
        const val CAT_ID_FAVORITES = -1L
        const val CAT_ID_CONTINUE = -2L
        private const val FAST_LOAD_LIMIT = 500L
        private const val RECENTLY_ADDED_LIMIT = 30L
    }

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _page = MutableStateFlow(0)
    private val PAGE_SIZE = 60

    // Survive navigation: sidebar/grid state in ViewModel, not remember
    private val _isSidebarHidden = MutableStateFlow(false)
    val isSidebarHidden: StateFlow<Boolean> = _isSidebarHidden
    private val _isGridFocused = MutableStateFlow(false)
    val isGridFocused: StateFlow<Boolean> = _isGridFocused

    private var browsingTrace: Trace? = null

    // True lazy: counts are loaded per-category only when item scrolls into view
    private val _categoryCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val loadedCountIds = mutableSetOf<Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoriesFlow = categoryBox.query()
        .equal(Category_.type, "series", StringOrder.CASE_INSENSITIVE)
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
    private val filteredSeriesFlow = combine(
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
            seriesBox.query()
                .contains(Series_.name, query, StringOrder.CASE_INSENSITIVE)
                .order(Series_.order)
                .build()
                .find(0, limit.toLong())
        } else {
            when (currentId) {
                CAT_ID_RECENTLY_ADDED -> {
                    seriesBox.query()
                        .order(Series_.lastModified, QueryBuilder.DESCENDING)
                        .order(Series_.id, QueryBuilder.DESCENDING)
                        .build()
                        .find(0, RECENTLY_ADDED_LIMIT)
                }
                CAT_ID_FAVORITES -> seriesBox.query()
                    .equal(Series_.isFavorite, true)
                    .order(Series_.order)
                    .build().find(0, limit.toLong())
                CAT_ID_CONTINUE -> {
                    val seriesIds = history.filter { it.type == "series" || it.type == "series_episode" }
                        .mapNotNull { it.seriesId }
                    val seriesData = seriesBox.get(seriesIds.toLongArray()).filterNotNull()
                    val seriesMap = seriesData.associateBy { it.id }
                    seriesIds.distinct().mapNotNull { seriesMap[it] }
                }
                else -> {
                    seriesBox.query()
                        .equal(Series_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
                        .order(Series_.order)
                        .build()
                        .find(0, limit.toLong())
                }
            }
        }
    }.onStart { emit(emptyList()) }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SeriesUiState> = combine(
        categoriesFlow,
        _selectedCategoryId,
        filteredSeriesFlow,
        _categoryCounts
    ) { allCategories, selectedId, filteredSeries, counts ->
        
        if (filteredSeries.isNotEmpty() && browsingTrace != null) {
            browsingTrace?.stop()
            browsingTrace = null
        }

        val currentId = selectedId ?: CAT_ID_RECENTLY_ADDED
        val currentSelected = allCategories.find { it.id == currentId } ?: allCategories.firstOrNull()

        SeriesUiState(
            categories = allCategories,
            categoryCounts = counts,
            selectedCategory = currentSelected,
            filteredSeries = filteredSeries,
            isLoading = false
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeriesUiState(isLoading = true))

    /** Lazy: call this from the compose item — runs count() only for this category, one-shot */
    fun loadCategoryCount(category: Category) {
        if (!loadedCountIds.add(category.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val count = when (category.id) {
                CAT_ID_RECENTLY_ADDED -> minOf(
                    seriesBox.query().order(Series_.lastModified, QueryBuilder.DESCENDING).build().count(),
                    RECENTLY_ADDED_LIMIT
                ).toInt()
                CAT_ID_FAVORITES -> seriesBox.query()
                    .equal(Series_.isFavorite, true).build().count().toInt()
                CAT_ID_CONTINUE -> playbackHistoryRepository.getRecentlyWatched(50)
                    .first().count { it.type == "series" || it.type == "series_episode" }
                else -> seriesBox.query()
                    .equal(Series_.remoteCategoryId, category.categoryId, StringOrder.CASE_INSENSITIVE)
                    .build().count().toInt()
            }
            if (count > 0) {
                _categoryCounts.update { it + (category.id to count) }
            }
        }
    }

    fun selectCategory(category: Category) {
        if (_selectedCategoryId.value != category.id) {
            browsingTrace = FirebasePerformance.getInstance().newTrace("series_category_load_time")
            browsingTrace?.start()
            _page.value = 0
            _selectedCategoryId.value = category.id
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) {
            _page.value = 0
            _searchQuery.value = query
        }
    }

    fun loadMore() {
        _page.value++
    }

    fun toggleFavorite(series: Series) {
        series.isFavorite = !series.isFavorite
        seriesBox.put(series)
    }

    fun removeFromHistory(series: Series) {
        viewModelScope.launch {
            val accountBox = boxStore.boxFor(XtreamAccount::class.java)
            val activeAccount = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst()
            activeAccount?.let { account ->
                // For series, we might have multiple history items for different episodes.
                // We need to delete all history items associated with this seriesId.
                // The current deleteHistory only takes streamId (which is episode id for series).
                // Let's check if we can add a method to delete by seriesId.
                
                val history = playbackHistoryRepository.getRecentlyWatched(100).first()
                val seriesHistoryItems = history.filter { it.seriesId == series.id && it.providerId == account.id }
                
                seriesHistoryItems.forEach { item ->
                    playbackHistoryRepository.deleteHistory(
                        streamId = item.streamId,
                        type = item.type,
                        providerId = account.id
                    )
                }
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

data class SeriesUiState(
    val categories: List<Category> = emptyList(),
    val categoryCounts: Map<Long, Int> = emptyMap(),
    val selectedCategory: Category? = null,
    val filteredSeries: List<Series> = emptyList(),
    val isLoading: Boolean = true
)