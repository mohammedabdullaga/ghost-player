package com.ghost.tv.ui.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tv.data.config.ConfigRepository
import com.ghost.tv.data.local.entity.*
import com.ghost.tv.data.remote.TmdbApiService
import com.ghost.tv.data.remote.TmdbGenreResponse
import com.ghost.tv.data.repository.PreferenceRepository
import com.ghost.tv.data.repository.XtreamRepository
import com.ghost.tv.domain.model.DownloadStatus
import com.ghost.tv.domain.repository.DownloadManager
import com.ghost.tv.domain.repository.PlaybackHistoryRepository
import com.ghost.tv.util.DeviceRenderProfile
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
import kotlinx.coroutines.withContext
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
        // Always observe core data (account, stats, history) regardless of mode
        observeData()

        // Always load trending — TMDB API call is lightweight and
        // Lite mode flag may leak from TV SharedPreferences to mobile.
        loadTrendingFromCache()

        // Background Silent Sync check (every 12 hours) - Delayed to prevent launch frame drops
        viewModelScope.launch(Dispatchers.IO) {
            delay(15000) // Delay by 15s to let the initial UI and animations settle
            val lastSync = preferenceRepository.getLastSyncTimestamp()
            val now = System.currentTimeMillis()
            if (now - lastSync > 12 * 60 * 60 * 1000L && lastSync > 0L) {
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

        // Refresh from TMDB
        viewModelScope.launch {
            // Use TTL logic - only refresh if stale (>24h)
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                combine(
                    runCatching { accountBox.query().equal(XtreamAccount_.isActive, true).build().flow() }
                        .getOrDefault(flowOf(emptyList())),
                    playbackHistoryRepository.getRecentlyWatched(50).map { history ->
                        history.filter { it.type != "live" }
                    },
                    downloadManager.observeAllDownloads()
                ) { accounts, history, downloads ->
                    val activeAccount = accounts.firstOrNull()
                    val latestDownload = downloads.filter { it.status == DownloadStatus.COMPLETED }
                        .maxByOrNull { it.completedAt ?: 0L }
                    
                    DashboardUiState(
                        isLoading = false,
                        liveCount = runCatching { liveBox.count().toInt() }.getOrDefault(0),
                        movieCount = runCatching { movieBox.count().toInt() }.getOrDefault(0),
                        seriesCount = runCatching { seriesBox.count().toInt() }.getOrDefault(0),
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
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    Log.e("DashboardVM", "Error in observeData flow", e)
                }
                .collect { newState ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            liveCount = newState.liveCount,
                            movieCount = newState.movieCount,
                            seriesCount = newState.seriesCount,
                            downloadsCount = newState.downloadsCount,
                            completedDownloadsCount = newState.completedDownloadsCount,
                            latestDownloadPoster = newState.latestDownloadPoster,
                            providerHealth = newState.providerHealth,
                            continueWatching = newState.continueWatching
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error in observeData coroutine", e)
            }
        }
    }

    private fun loadTrendingFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val allCached = trendingBox.all
            val cachedMovies = allCached.filter { it.type == "movie" }
            val cachedSeries = allCached.filter { it.type == "tv" }
            
            if (cachedMovies.isNotEmpty() || cachedSeries.isNotEmpty()) {
                Log.d("DashboardVM", "Instant load: Using Trending Cache (${cachedMovies.size} M, ${cachedSeries.size} S)")
                
                // Batch fetch local matches to avoid 40 sequential queries
                val movieTitles = cachedMovies.map { it.title }
                val seriesTitles = cachedSeries.map { it.title }
                
                val localMovies = if (movieTitles.isNotEmpty()) {
                    runCatching {
                        val qb = movieBox.query()
                        movieTitles.forEachIndexed { index, title ->
                            qb.equal(Movie_.name, title, StringOrder.CASE_INSENSITIVE)
                            if (index < movieTitles.size - 1) qb.or()
                        }
                        qb.build().use { it.find() }
                    }.getOrDefault(emptyList())
                } else emptyList<Movie>()

                val localSeries = if (seriesTitles.isNotEmpty()) {
                    runCatching {
                        val qb = seriesBox.query()
                        seriesTitles.forEachIndexed { index, title ->
                            qb.equal(Series_.name, title, StringOrder.CASE_INSENSITIVE)
                            if (index < seriesTitles.size - 1) qb.or()
                        }
                        qb.build().use { it.find() }
                    }.getOrDefault(emptyList())
                } else emptyList<Series>()
                
                val localMovieMap = localMovies.associateBy { it.name.lowercase() }
                val localSeriesMap = localSeries.associateBy { it.name.lowercase() }

                // Move CPU intensive Gson parsing to Default dispatcher
                val (tMovies, tSeries) = withContext(Dispatchers.Default) {
                    val typeInt = object : TypeToken<List<Int>>() {}.type
                    val typeStr = object : TypeToken<List<String>>() {}.type

                    val moviesList = cachedMovies.map { cache ->
                        val gIds: List<Int> = gson.fromJson(cache.genreIdsJson, typeInt) ?: emptyList()
                        val gNames: List<String> = gson.fromJson(cache.genreNamesJson, typeStr) ?: emptyList()
                        val matched: Movie? = localMovieMap[cache.title.lowercase()]
                        TrendingItem<Movie>(cache.tmdbId, cache.title, cache.overview, cache.posterUrl, cache.backdropUrl, cache.releaseDate, cache.voteAverage, gIds, gNames, matched)
                    }
                    val seriesList = cachedSeries.map { cache ->
                        val gIds: List<Int> = gson.fromJson(cache.genreIdsJson, typeInt) ?: emptyList()
                        val gNames: List<String> = gson.fromJson(cache.genreNamesJson, typeStr) ?: emptyList()
                        val matched: Series? = localSeriesMap[cache.title.lowercase()]
                        TrendingItem<Series>(cache.tmdbId, cache.title, cache.overview, cache.posterUrl, cache.backdropUrl, cache.releaseDate, cache.voteAverage, gIds, gNames, matched)
                    }
                    moviesList to seriesList
                }
                
                _uiState.update { it.copy(
                    trendingMovies = tMovies,
                    trendingSeries = tSeries,
                    isTrendingLoading = false
                ) }
            } else {
                // No trending cache yet — clear loading flag so UI shows fallback,
                // then fetch from TMDB will populate it.
                _uiState.update { it.copy(isTrendingLoading = false) }
            }
        }
    }

    private fun fetchTrending(forceRefresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            // Staggered Dashboard Init: Allow first frame to render from cache first
            delay(1500)
            
            // Fallback hardcoded key in case config endpoint is slow or unreachable
            val HARDCODED_KEY = "77db3c693088e47469e0df1feebb4a4c"
            
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
            
            // Fallback hardcoded key if config endpoint doesn't provide one.
            // This ensures trending always works even on a fresh install with no config.
            if (apiKey == null) {
                apiKey = HARDCODED_KEY
                Log.d("DashboardVM", "Using hardcoded TMDB key as fallback")
            }
            
            val langAr = "ar"
            val langEn = "en"

            try {
                // Cached Genre Mapping logic
                val movieGenres = getOrFetchGenres("movie", apiKey, langEn)
                val tvGenres = getOrFetchGenres("tv", apiKey, langEn)

                val movieRespAr = tmdbApiService.getTrendingMovies(apiKey, langAr)
                val movieRespEn = tmdbApiService.getTrendingMovies(apiKey, langEn)
                
                val movieEnMap = movieRespEn.results.associateBy { it.id }

                // Parallel Parsing Optimization: Move heavy mapping to Default dispatcher
                val trendingMovies = withContext(Dispatchers.Default) {
                    movieRespAr.results.map { resultAr ->
                        val resultEn = movieEnMap[resultAr.id]
                        val displayTitle = resultEn?.title ?: resultAr.title ?: ""
                        val originalTitle = resultAr.original_title ?: ""
                        
                        var matched = runCatching {
                            movieBox.query()
                                .equal(Movie_.name, displayTitle, StringOrder.CASE_INSENSITIVE)
                                .build()
                                .use { it.findFirst() }
                        }.getOrNull()
                        
                        if (matched == null && resultAr.title != null) {
                            matched = runCatching {
                                movieBox.query()
                                    .equal(Movie_.name, resultAr.title!!, StringOrder.CASE_INSENSITIVE)
                                    .build()
                                    .use { it.findFirst() }
                            }.getOrNull()
                        }

                        if (matched == null && originalTitle.isNotBlank()) {
                            matched = runCatching {
                                movieBox.query()
                                    .contains(Movie_.name, originalTitle.lowercase(), StringOrder.CASE_INSENSITIVE)
                                    .build()
                                    .use { it.findFirst() }
                            }.getOrNull()
                        }

                        matched?.apply { 
                            if (resultEn?.backdrop_path != null) {
                                val newBackdrop = "https://image.tmdb.org/t/p/w1280${resultEn.backdrop_path}"
                                if (backdropUrl != newBackdrop) {
                                    backdropUrl = newBackdrop
                                    movieBox.put(this)
                                }
                            }
                        }

                        TrendingItem<Movie>(
                            tmdbId = resultAr.id,
                            title = displayTitle,
                            overview = resultAr.overview?.takeIf { it.isNotBlank() } ?: matched?.plot ?: "",
                            posterUrl = (resultEn?.poster_path ?: resultAr.poster_path)?.let { "https://image.tmdb.org/t/p/w500$it" },
                            backdropUrl = (resultEn?.backdrop_path ?: resultAr.backdrop_path)?.let { "https://image.tmdb.org/t/p/w1280$it" },
                            releaseDate = resultAr.release_date,
                            voteAverage = resultAr.vote_average,
                            genreIds = resultAr.genre_ids,
                            genres = resultAr.genre_ids.mapNotNull { movieGenres[it] },
                            localMatch = matched
                        )
                    }.distinctBy { it.tmdbId }.take(20)
                }

                val seriesRespAr = tmdbApiService.getTrendingTv(apiKey, langAr)
                val seriesRespEn = tmdbApiService.getTrendingTv(apiKey, langEn)
                val seriesEnMap = seriesRespEn.results.associateBy { it.id }

                val trendingSeries = withContext(Dispatchers.Default) {
                    seriesRespAr.results.map { resultAr ->
                        val resultEn = seriesEnMap[resultAr.id]
                        val displayName = resultEn?.name ?: resultAr.name ?: ""
                        val originalName = resultAr.original_name ?: ""

                        var matched = runCatching {
                            seriesBox.query()
                                .equal(Series_.name, displayName, StringOrder.CASE_INSENSITIVE)
                                .build()
                                .use { it.findFirst() }
                        }.getOrNull()

                        if (matched == null && resultAr.name != null) {
                            matched = runCatching {
                                seriesBox.query()
                                    .equal(Series_.name, resultAr.name!!, StringOrder.CASE_INSENSITIVE)
                                    .build()
                                    .use { it.findFirst() }
                            }.getOrNull()
                        }

                        if (matched == null && originalName.isNotBlank()) {
                            matched = runCatching {
                                seriesBox.query()
                                    .contains(Series_.name, originalName.lowercase(), StringOrder.CASE_INSENSITIVE)
                                    .build()
                                    .use { it.findFirst() }
                            }.getOrNull()
                        }
                        
                        matched?.apply {
                            if (resultEn?.backdrop_path != null) {
                                val newBackdrop = "https://image.tmdb.org/t/p/w1280${resultEn.backdrop_path}"
                                if (backdropUrl != newBackdrop) {
                                    backdropUrl = newBackdrop
                                    seriesBox.put(this)
                                }
                            }
                        }

                        TrendingItem<Series>(
                            tmdbId = resultAr.id,
                            title = displayName,
                            overview = resultAr.overview?.takeIf { it.isNotBlank() } ?: matched?.plot ?: "",
                            posterUrl = (resultEn?.poster_path ?: resultAr.poster_path)?.let { "https://image.tmdb.org/t/p/w500$it" },
                            backdropUrl = (resultEn?.backdrop_path ?: resultAr.backdrop_path)?.let { "https://image.tmdb.org/t/p/w1280$it" },
                            releaseDate = resultAr.first_air_date,
                            voteAverage = resultAr.vote_average,
                            genreIds = resultAr.genre_ids,
                            genres = resultAr.genre_ids.mapNotNull { tvGenres[it] },
                            localMatch = matched
                        )
                    }.distinctBy { it.tmdbId }.take(20)
                }

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

    private suspend fun getOrFetchGenres(type: String, apiKey: String, lang: String): Map<Int, String> {
        val cached = preferenceRepository.getCachedGenres(type)
        if (cached != null) {
            try {
                val genreList = gson.fromJson(cached, TmdbGenreResponse::class.java)
                return genreList.genres.associate { it.id to it.name }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Failed to parse cached genres", e)
            }
        }

        val remoteGenres = if (type == "movie") {
            tmdbApiService.getMovieGenres(apiKey, lang).genres
        } else {
            tmdbApiService.getTvGenres(apiKey, lang).genres
        }
        
        if (remoteGenres.isNotEmpty()) {
            val wrapper = TmdbGenreResponse(remoteGenres)
            preferenceRepository.setCachedGenres(type, gson.toJson(wrapper))
        }
        
        return remoteGenres.associate { it.id to it.name }
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
