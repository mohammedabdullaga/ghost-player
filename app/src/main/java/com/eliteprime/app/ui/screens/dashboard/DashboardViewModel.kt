package com.eliteprime.app.ui.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.TmdbApiService
import com.eliteprime.app.data.repository.PreferenceRepository
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
    private val configRepository: ConfigRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
        
        // Fetch trending data initially and whenever language changes
        preferenceRepository.language
            .onEach { fetchTrending() }
            .launchIn(viewModelScope)
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
            _uiState.update { it.copy(isTrendingLoading = true) }
            
            // Wait for config if not loaded yet
            var apiKey = configRepository.getTmdbApiKey()
            if (apiKey == null) {
                Log.d("DashboardVM", "TMDB Key missing, triggering fetch...")
                configRepository.fetchConfig()
                apiKey = configRepository.getTmdbApiKey()
            }
            
            if (apiKey == null) {
                Log.e("DashboardVM", "TMDB API Key is still null after fetch. Cannot load trending.")
                _uiState.update { it.copy(isTrendingLoading = false) }
                return@launch
            }
            
            Log.d("DashboardVM", "Fetching trending with API Key: ${apiKey.take(4)}...")

            val lang = preferenceRepository.language.value

            try {
                // 1. Fetch Trending Movies
                val movieResp = tmdbApiService.getTrendingMovies(apiKey, lang)
                val trendingMovies = movieResp.results.map { result ->
                    val title = result.title ?: ""
                    val cleanedTitle = title.trim().lowercase()
                    
                    var matched = movieBox.query()
                        .equal(Movie_.name, title, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .findFirst()
                    
                    if (matched == null && cleanedTitle.isNotEmpty()) {
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

                    TrendingItem(
                        tmdbId = result.id,
                        title = title,
                        overview = result.overview ?: "",
                        posterUrl = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = result.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        releaseDate = result.release_date,
                        voteAverage = result.vote_average,
                        genreIds = result.genre_ids,
                        localMatch = matched
                    )
                }.distinctBy { it.tmdbId }.take(20)

                // 2. Fetch Trending Series
                val seriesResp = tmdbApiService.getTrendingTv(apiKey, lang)
                val trendingSeries = seriesResp.results.map { result ->
                    val name = result.name ?: ""
                    val cleanedName = name.trim().lowercase()

                    var matched = seriesBox.query()
                        .equal(Series_.name, name, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .findFirst()

                    if (matched == null && cleanedName.isNotEmpty()) {
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

                    TrendingItem(
                        tmdbId = result.id,
                        title = name,
                        overview = result.overview ?: "",
                        posterUrl = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = result.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        releaseDate = result.first_air_date,
                        voteAverage = result.vote_average,
                        genreIds = result.genre_ids,
                        localMatch = matched
                    )
                }.distinctBy { it.tmdbId }.take(20)

                Log.d("DashboardVM", "Trending results ready: Movies=${trendingMovies.size}, Series=${trendingSeries.size}")

                _uiState.update { it.copy(
                    trendingMovies = trendingMovies,
                    trendingSeries = trendingSeries,
                    isTrendingLoading = false
                ) }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Trending fetch failed", e)
                _uiState.update { it.copy(isTrendingLoading = false) }
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
