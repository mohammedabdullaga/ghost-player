package com.eliteprime.app.ui.screens.series

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.R
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val categoriesFlow = categoryBox.query()
        .equal(Category_.type, "series", StringOrder.CASE_INSENSITIVE)
        .build()
        .flow()
        .map { providerCats ->
            val virtualCats = listOf(
                Category(id = CAT_ID_RECENTLY_ADDED, categoryName = getApplication<Application>().getString(R.string.nav_recently_added), isVirtual = true),
                Category(id = CAT_ID_FAVORITES, categoryName = getApplication<Application>().getString(R.string.nav_favorites), isVirtual = true),
                Category(id = CAT_ID_CONTINUE, categoryName = "🕒 Continue Watching", isVirtual = true)
            )
            (virtualCats + providerCats.filter { !it.isHidden }).sortedBy { it.order }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    // Debounce only non-empty queries; empty (initial/default) emits immediately
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
            val qb = seriesBox.query().contains(Series_.name, query, StringOrder.CASE_INSENSITIVE)
            if (currentId > 0) {
                qb.equal(Series_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
            }
            qb.order(Series_.order).build().find(0, limit.toLong())
        } else {
            when (currentId) {
                CAT_ID_RECENTLY_ADDED -> {
                    // Find latest added series, capped to 30 items
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
        filteredSeriesFlow
    ) { allCategories, selectedId, seriesList ->
        
        val currentId = selectedId ?: CAT_ID_RECENTLY_ADDED
        val currentSelected = allCategories.find { it.id == currentId } ?: allCategories.firstOrNull()

        SeriesUiState(
            categories = allCategories,
            selectedCategory = currentSelected,
            filteredSeries = seriesList,
            isLoading = false
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeriesUiState(isLoading = true))

    fun selectCategory(category: Category) {
        if (_selectedCategoryId.value != category.id) {
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
}

data class SeriesUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val filteredSeries: List<Series> = emptyList(),
    val isLoading: Boolean = true
)
