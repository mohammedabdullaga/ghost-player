package com.eliteprime.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val boxStore: BoxStore
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        viewModelScope.launch {
            // Optimization: Use count() instead of fetching full lists for the dashboard summary
            combine(
                accountBox.query().equal(XtreamAccount_.isActive, true).build().flow(),
                liveBox.query().build().flow(),
                movieBox.query().build().flow(),
                seriesBox.query().build().flow()
            ) { accounts, liveQuery, moviesQuery, seriesQuery ->
                val activeAccount = accounts.firstOrNull()
                
                // Perform counts in background
                DashboardUiState(
                    isLoading = false,
                    liveCount = liveBox.count().toInt(),
                    movieCount = movieBox.count().toInt(),
                    seriesCount = seriesBox.count().toInt(),
                    providerHealth = DashboardProviderHealth(
                        expirationDate = activeAccount?.expiryDate,
                        status = activeAccount?.status ?: "",
                        maxConnections = activeAccount?.maxConnections ?: 0
                    )
                )
            }
            .flowOn(Dispatchers.Default)
            .collect {
                _uiState.value = it
            }
        }
    }
}
