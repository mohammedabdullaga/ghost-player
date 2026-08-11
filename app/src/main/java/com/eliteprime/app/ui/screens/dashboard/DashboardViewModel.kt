package com.eliteprime.app.ui.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.TmdbApiService
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val tmdbApiService: TmdbApiService,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
        fetchTrending()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        viewModelScope.launch {
            combine(
                accountBox.query().equal(XtreamAccount_.isActive, true).build().flow(),
                playbackHistoryRepository.getRecentlyWatched(50).map { history ->
                    history.filter { it.type != "live" }
                }
            ) { accounts, history ->
                val activeAccount = accounts.firstOrNull()
                
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        liveCount = liveBox.count().toInt(),
                        movieCount = movieBox.count().toInt(),
                        seriesCount = seriesBox.count().toInt(),
                        providerHealth = DashboardProviderHealth(
                            expirationDate = activeAccount?.expiryDate,
                            status = activeAccount?.status ?: "",
                            maxConnections = activeAccount?.maxConnections ?: 0
                        ),
                        continueWatching = history
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .collect()
        }
    }

    private fun fetchTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            // Wait for config if not loaded yet
            var apiKey = configRepository.getTmdbApiKey()
            if (apiKey == null) {
                Log.d("DashboardVM", "TMDB Key missing, triggering fetch...")
                configRepository.fetchConfig()
                apiKey = configRepository.getTmdbApiKey()
            }
            
            if (apiKey == null) {
                Log.e("DashboardVM", "TMDB API Key is still null after fetch. Cannot load trending.")
                return@launch
            }
            
            Log.d("DashboardVM", "Fetching trending with API Key: ${apiKey.take(4)}...")

            try {
                // 1. Fetch Trending Movies
                val movieResp = tmdbApiService.getTrendingMovies(apiKey)
                val trendingMovies = movieResp.results.mapNotNull { result ->
                    val title = result.title ?: return@mapNotNull null
                    
                    // Match strategy:
                    // 1. Cleaned Exact Match
                    // 2. Contains Match (if library title is "Deadpool & Wolverine (2024) [4K]" it contains "Deadpool & Wolverine")
                    
                    val cleanedTitle = title.trim().lowercase()
                    
                    var matched = movieBox.query()
                        .equal(Movie_.name, title, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .findFirst()
                    
                    if (matched == null) {
                        // More aggressive: find in library where library name contains TMDB title
                        matched = movieBox.query()
                            .contains(Movie_.name, cleanedTitle, StringOrder.CASE_INSENSITIVE)
                            .build()
                            .findFirst()
                    }

                    matched?.apply { 
                        if (result.backdrop_path != null) {
                            val newBackdrop = "https://image.tmdb.org/t/p/w1280${result.backdrop_path}"
                            if (backdropUrl != newBackdrop) {
                                backdropUrl = newBackdrop
                                movieBox.put(this)
                            }
                        }
                    }
                }.distinctBy { it.id }.take(15)

                // 2. Fetch Trending Series
                val seriesResp = tmdbApiService.getTrendingTv(apiKey)
                val trendingSeries = seriesResp.results.mapNotNull { result ->
                    val name = result.name ?: return@mapNotNull null
                    val cleanedName = name.trim().lowercase()

                    var matched = seriesBox.query()
                        .equal(Series_.name, name, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .findFirst()

                    if (matched == null) {
                        matched = seriesBox.query()
                            .contains(Series_.name, cleanedName, StringOrder.CASE_INSENSITIVE)
                            .build()
                            .findFirst()
                    }
                    
                    matched?.apply {
                        if (result.backdrop_path != null) {
                            val newBackdrop = "https://image.tmdb.org/t/p/w1280${result.backdrop_path}"
                            if (backdropUrl != newBackdrop) {
                                backdropUrl = newBackdrop
                                seriesBox.put(this)
                            }
                        }
                    }
                }.distinctBy { it.id }.take(15)

                Log.d("DashboardVM", "Trending match results: Movies=${trendingMovies.size}, Series=${trendingSeries.size}")

                _uiState.update { it.copy(
                    trendingMovies = trendingMovies,
                    trendingSeries = trendingSeries
                ) }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Trending fetch failed", e)
            }
        }
    }

    fun removeFromHistory(history: PlaybackHistory) {
        viewModelScope.launch {
            playbackHistoryRepository.deleteHistory(
                streamId = history.streamId,
                type = history.type,
                providerId = history.providerId
            )
        }
    }
}
