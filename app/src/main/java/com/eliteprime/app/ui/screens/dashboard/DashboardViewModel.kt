package com.eliteprime.app.ui.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.TmdbApiService
import com.eliteprime.app.data.repository.PreferenceRepository
import com.eliteprime.app.data.repository.XtreamRepository
import com.eliteprime.app.domain.model.DownloadStatus
import com.eliteprime.app.domain.repository.DownloadManager
import com.eliteprime.app.domain.repository.PlaybackHistoryRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.flow
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val boxStore: BoxStore,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val downloadManager: DownloadManager,
    private val tmdbApiService: TmdbApiService,
    private val configRepository: ConfigRepository,
    private val preferenceRepository: PreferenceRepository,
    private val xtreamRepository: XtreamRepository
) : ViewModel() {

    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)
    private val trendingBox = boxStore.boxFor(TrendingCache::class.java)
    
    private val gson = Gson()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeData()
        
        // Initial load from cache to show instantly
        loadTrendingFromCache()
        
        // Background Silent Sync check (every 12 hours)
        viewModelScope.launch(Dispatchers.IO) {
            val lastSync = preferenceRepository.getLastSyncTimestamp()
            val now = System.currentTimeMillis()
            if (now - lastSync > 12 * 60 * 60 * 1000L) {
                Log.d("DashboardVM", "Starting background silent sync...")
                try {
                    xtreamRepository.syncCategories()
                    xtreamRepository.syncAllStreams({}, {}, {})
                    preferenceRepository.setLastSyncTimestamp(now)
                    Log.d("DashboardVM", "Silent sync completed successfully.")
                } catch (e: Exception) {
                    Log.e("DashboardVM", "Silent sync failed", e)
                }
            }
        }
        
        // Refresh from TMDB only once every 24 hours
        viewModelScope.launch {
            fetchTrending(forceRefresh = false)
        }
        
        // Re-fetch on language change
        preferenceRepository.language
            .drop(1)
            .onEach { fetchTrending(forceRefresh = true) }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        viewModelScope.launch {
            combine(
                accountBox.query().equal(XtreamAccount_.isActive, true).build().flow(),
                playbackHistoryRepository.getRecentlyWatched(50).map { history ->
                    history.filter { it.type != "live" }
                },
                downloadManager.observeAllDownloads()
            ) { accounts, history, downloads ->
                val activeAccount = accounts.firstOrNull()
                val latestDownload = downloads.filter { it.status == DownloadStatus.COMPLETED }
                    .maxByOrNull { it.completedAt ?: 0L }
                
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        liveCount = liveBox.count().toInt(),
                        movieCount = movieBox.count().toInt(),
                        seriesCount = seriesBox.count().toInt(),
                        downloadsCount = downloads.size,
                        completedDownloadsCount = downloads.count { it.status == DownloadStatus.COMPLETED },
                        latestDownloadPoster = latestDownload?.posterUrl,
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

    private fun loadTrendingFromCache() {
        val allCached = trendingBox.all
        val cachedMovies = allCached.filter { it.type == "movie" }
        val cachedSeries = allCached.filter { it.type == "tv" }
        
        if (cachedMovies.isNotEmpty() || cachedSeries.isNotEmpty()) {
            Log.d("DashboardVM", "Instant load: Using Trending Cache (${cachedMovies.size} M, ${cachedSeries.size} S)")
            
            val tMovies: List<TrendingItem<Movie>> = cachedMovies.map { it.toTrendingMovieItem(movieBox) }
            val tSeries: List<TrendingItem<Series>> = cachedSeries.map { it.toTrendingSeriesItem(seriesBox) }
            
            _uiState.update { it.copy(
                trendingMovies = tMovies,
                trendingSeries = tSeries,
                isTrendingLoading = false
            ) }
        }
    }

    private fun fetchTrending(forceRefresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val allCached = trendingBox.all
            val lastUpdate = allCached.firstOrNull()?.lastUpdated ?: 0L
            val now = System.currentTimeMillis()
            val twentyFourHours = 24 * 60 * 60 * 1000L
            
            if (!forceRefresh && (now - lastUpdate < twentyFourHours) && lastUpdate > 0) {
                Log.d("DashboardVM", "Trending cache is fresh. Skipping TMDB call.")
                _uiState.update { it.copy(isTrendingLoading = false) }
                return@launch
            }

            _uiState.update { it.copy(isTrendingLoading = true) }
            
            var apiKey = configRepository.getTmdbApiKey()
            if (apiKey == null) {
                configRepository.fetchConfig()
                apiKey = configRepository.getTmdbApiKey()
            }
            
            if (apiKey == null) {
                _uiState.update { it.copy(isTrendingLoading = false) }
                return@launch
            }
            
            val lang = "ar"

            try {
                val movieGenres = runCatching { tmdbApiService.getMovieGenres(apiKey, lang).genres }.getOrDefault(emptyList()).associate { it.id to it.name }
                val tvGenres = runCatching { tmdbApiService.getTvGenres(apiKey, lang).genres }.getOrDefault(emptyList()).associate { it.id to it.name }

                val movieResp = tmdbApiService.getTrendingMovies(apiKey, lang)
                val trendingMovies = movieResp.results.map { result ->
                    val displayTitle = result.title ?: ""
                    val originalTitle = result.original_title ?: ""
                    
                    var matched = movieBox.query()
                        .equal(Movie_.name, displayTitle, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .findFirst()
                    
                    if (matched == null && originalTitle.isNotBlank()) {
                        matched = movieBox.query()
                            .contains(Movie_.name, originalTitle.lowercase(), StringOrder.CASE_INSENSITIVE)
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
                        title = displayTitle,
                        overview = result.overview?.takeIf { it.isNotBlank() } ?: matched?.plot ?: "",
                        posterUrl = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = result.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        releaseDate = result.release_date,
                        voteAverage = result.vote_average,
                        genreIds = result.genre_ids,
                        genres = result.genre_ids.mapNotNull { movieGenres[it] },
                        localMatch = matched
                    )
                }.distinctBy { it.tmdbId }.take(20)

                val seriesResp = tmdbApiService.getTrendingTv(apiKey, lang)
                val trendingSeries = seriesResp.results.map { result ->
                    val displayName = result.name ?: ""
                    val originalName = result.original_name ?: ""

                    var matched = seriesBox.query()
                        .equal(Series_.name, displayName, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .findFirst()

                    if (matched == null && originalName.isNotBlank()) {
                        matched = seriesBox.query()
                            .contains(Series_.name, originalName.lowercase(), StringOrder.CASE_INSENSITIVE)
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
                        title = displayName,
                        overview = result.overview?.takeIf { it.isNotBlank() } ?: matched?.plot ?: "",
                        posterUrl = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                        backdropUrl = result.backdrop_path?.let { "https://image.tmdb.org/t/p/w1280$it" },
                        releaseDate = result.first_air_date,
                        voteAverage = result.vote_average,
                        genreIds = result.genre_ids,
                        genres = result.genre_ids.mapNotNull { tvGenres[it] },
                        localMatch = matched
                    )
                }.distinctBy { it.tmdbId }.take(20)

                boxStore.runInTx {
                    trendingBox.removeAll()
                    val toCache = mutableListOf<TrendingCache>()
                    trendingMovies.forEach { toCache.add(it.toCacheEntity("movie", now)) }
                    trendingSeries.forEach { toCache.add(it.toCacheEntity("tv", now)) }
                    trendingBox.put(toCache)
                }

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

    private fun TrendingCache.toTrendingMovieItem(box: Box<Movie>): TrendingItem<Movie> {
        val typeInt = object : TypeToken<List<Int>>() {}.type
        val typeStr = object : TypeToken<List<String>>() {}.type
        val gIds: List<Int> = gson.fromJson(genreIdsJson, typeInt) ?: emptyList()
        val gNames: List<String> = gson.fromJson(genreNamesJson, typeStr) ?: emptyList()
        val matched = box.query().equal(Movie_.name, title, StringOrder.CASE_INSENSITIVE).build().findFirst()

        return TrendingItem(tmdbId, title, overview, posterUrl, backdropUrl, releaseDate, voteAverage, gIds, gNames, matched)
    }

    private fun TrendingCache.toTrendingSeriesItem(box: Box<Series>): TrendingItem<Series> {
        val typeInt = object : TypeToken<List<Int>>() {}.type
        val typeStr = object : TypeToken<List<String>>() {}.type
        val gIds: List<Int> = gson.fromJson(genreIdsJson, typeInt) ?: emptyList()
        val gNames: List<String> = gson.fromJson(genreNamesJson, typeStr) ?: emptyList()
        val matched = box.query().equal(Series_.name, title, StringOrder.CASE_INSENSITIVE).build().findFirst()

        return TrendingItem(tmdbId, title, overview, posterUrl, backdropUrl, releaseDate, voteAverage, gIds, gNames, matched)
    }

    private fun TrendingItem<*>.toCacheEntity(type: String, timestamp: Long): TrendingCache {
        return TrendingCache(
            type = type,
            tmdbId = tmdbId,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            releaseDate = releaseDate,
            voteAverage = voteAverage,
            genreIdsJson = gson.toJson(genreIds),
            genreNamesJson = gson.toJson(genres),
            lastUpdated = timestamp
        )
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
