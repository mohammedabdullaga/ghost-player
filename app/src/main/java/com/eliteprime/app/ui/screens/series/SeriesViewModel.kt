package com.eliteprime.app.ui.screens.series

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
class SeriesViewModel @Inject constructor(
    private val boxStore: BoxStore
) : ViewModel() {

    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<SeriesUiState> = combine(
        categoryBox.query().equal(Category_.type, "series", StringOrder.CASE_INSENSITIVE).build().flow(),
        _selectedCategory,
        _searchQuery.debounce(300)
    ) { categories, selectedCat, query ->
        val currentSelected = selectedCat ?: categories.firstOrNull()

        val seriesList = if (currentSelected != null) {
            val qb = seriesBox.query()
            qb.equal(Series_.remoteCategoryId, currentSelected.categoryId, StringOrder.CASE_INSENSITIVE)
            if (query.isNotEmpty()) {
                qb.contains(Series_.name, query, StringOrder.CASE_INSENSITIVE)
            }
            qb.build().find()
        } else {
            emptyList()
        }

        SeriesUiState(
            categories = categories,
            selectedCategory = currentSelected,
            filteredSeries = seriesList,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeriesUiState())

    fun selectCategory(category: Category) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}

data class SeriesUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val filteredSeries: List<Series> = emptyList(),
    val isLoading: Boolean = true
)
