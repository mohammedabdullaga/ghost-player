package com.ghost.tv.data.repository

import android.util.Log
import com.ghost.tv.data.config.ConfigRepository
import com.ghost.tv.data.local.entity.*
import com.ghost.tv.data.remote.TmdbApiService
import com.ghost.tv.data.remote.XtreamApi
import com.ghost.tv.data.remote.XtreamSeasonResponse
import com.ghost.tv.data.remote.XtreamStreamParser
import com.ghost.tv.data.remote.XtreamUrlFactory
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRepository @Inject constructor(
    private val xtreamApi: XtreamApi,
    private val boxStore: BoxStore,
    private val okHttpClient: OkHttpClient,
    private val configRepository: ConfigRepository,
    private val parser: XtreamStreamParser,
    private val tmdbApi: TmdbApiService
) {
    private val TAG = "XtreamRepository"
    private val TMDB_API_KEY = "77db3c693088e47469e0df1feebb4a4c"
    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)
    private val epgBox = boxStore.boxFor(EPGProgram::class.java)

    suspend fun login(user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        // ALWAYS fetch fresh config for login to ensure 100% reliance on latest remote config
        Log.d(TAG, "Forcing fresh config fetch for login...")
        configRepository.fetchConfig()
        
        val activeHosts = configRepository.getActiveHosts()
        Log.d(TAG, "Attempting login with ${activeHosts.size} hosts from remote config")
        
        for (host in activeHosts) {
            try {
                val baseUrl = if (host.url.endsWith("/")) host.url else "${host.url}/"
                val fullUrl = "${baseUrl}player_api.php"
                Log.d(TAG, "Authenticating against: $fullUrl")
                
                val response = xtreamApi.authenticate(fullUrl, user, pass)
                Log.d(TAG, "Response status: ${response.userInfo?.status}")

                if (response.userInfo?.status == "Active") {
                    boxStore.runInTx {
                        accountBox.removeAll()
                        accountBox.put(XtreamAccount(
                            name = "GHOST TV",
                            serverUrl = baseUrl,
                            username = user,
                            password = pass,
                            isActive = true,
                            expiryDate = response.userInfo.exp_date?.toLongOrNull()?.let { it * 1000 },
                            maxConnections = response.userInfo.max_connections?.toIntOrNull() ?: 0,
                            status = response.userInfo.status
                        ))
                    }
                    return@withContext true
                } else if (response.userInfo?.status != null) {
                    Log.w(TAG, "Account status not active: ${response.userInfo.status}")
                    FirebaseCrashlytics.getInstance().log("Login: Account status is ${response.userInfo.status} for host ${host.url}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login failed for host ${host.url}: ${e.message}")
                FirebaseCrashlytics.getInstance().setCustomKey("last_failed_host", host.url)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
        false
    }

    suspend fun getAccount(): XtreamAccount? {
        val account = runCatching {
            accountBox.query().equal(XtreamAccount_.isActive, true).build().use { it.findFirst() }
        }.getOrNull() ?: return null
        
        // Ensure config is loaded; resolveWorkingAccount() handles failover.
        val activeHosts = configRepository.getActiveHosts()
        if (activeHosts.isEmpty()) {
            Log.d(TAG, "No session hosts available, forcing fresh config fetch...")
            configRepository.fetchConfig()
        }
        
        return account
    }

    private suspend fun getActiveAccountOrRefresh(): XtreamAccount? = getAccount()

    /**
     * Tries the stored account first. If the primary host fails, iterates through
     * all active hosts from remote config to find a working one, and updates
     * the stored account's serverUrl on success.
     */
    private suspend fun resolveWorkingAccount(): XtreamAccount? {
        val account = getAccount() ?: return null

        // Quick probe of the current host
        if (isUrlWorking("${account.serverUrl}player_api.php")) {
            return account
        }

        Log.w(TAG, "Primary host ${account.serverUrl} is unreachable. Attempting failover...")
        val allHosts = configRepository.getActiveHosts()
        for (host in allHosts) {
            val baseUrl = if (host.url.endsWith("/")) host.url else "${host.url}/"
            if (baseUrl == account.serverUrl) continue // already tried

            val probeUrl = "${baseUrl}player_api.php?username=${account.username}&password=${account.password}&action=get_live_categories"
            if (isUrlWorking(probeUrl)) {
                Log.i(TAG, "Failover successful! Switching from ${account.serverUrl} to $baseUrl")
                account.serverUrl = baseUrl
                accountBox.put(account)
                return account
            }
        }

        Log.e(TAG, "All hosts exhausted. No working server found.")
        return account // return original anyway, let callers handle the error
    }

    /**
     * Returns ALL possible playback URLs for a stream across every active host
     * in the remote config, with the current account's host listed first.
     * The caller should iterate through these on playback failure.
     */
    /** Returns all active host URLs from remote config (not suspend, cached). */
    fun getActiveHostUrls(): List<String> {
        return configRepository.getActiveHosts().map { h ->
            if (h.url.endsWith("/")) h.url else "${h.url}/"
        }
    }

    suspend fun getPlaybackUrlCandidates(
        kind: String,
        streamId: Long,
        extension: String? = null
    ): List<String> {
        val account = getAccount() ?: return emptyList()
        val allHosts = configRepository.getActiveHosts()
        val urls = mutableListOf<String>()

        // Current host first
        urls.add(
            XtreamUrlFactory.buildPlaybackUrl(account.serverUrl, account.username, account.password, kind, streamId, extension)
        )

        // Then all other active hosts
        for (host in allHosts) {
            val baseUrl = if (host.url.endsWith("/")) host.url else "${host.url}/"
            if (baseUrl == account.serverUrl) continue
            urls.add(
                XtreamUrlFactory.buildPlaybackUrl(baseUrl, account.username, account.password, kind, streamId, extension)
            )
        }

        Log.i(TAG, "Playback URL candidates for $kind/$streamId: ${urls.size} URLs from ${allHosts.size} other hosts. Account host=${account.serverUrl}. Hosts=${allHosts.map { it.url }}")
        return urls
    }

    private suspend fun isUrlWorking(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).head().build()
                val response = okHttpClient.newCall(request).execute()
                val result = response.isSuccessful
                Log.d(TAG, "isUrlWorking HEAD $url -> ${response.code} ($result)")
                response.close()
                result
            } catch (e: Exception) {
                Log.w(TAG, "isUrlWorking HEAD $url -> EXCEPTION: ${e.message}")
                false
            }
        }
    }

    suspend fun syncCategories() = withContext(Dispatchers.IO) {
        val account = resolveWorkingAccount() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        val trace = FirebasePerformance.getInstance().newTrace("sync_categories")
        trace.start()
        try {
            coroutineScope {
                val liveCatsDeferred = async { xtreamApi.getCategories(fullUrl, account.username, account.password, "get_live_categories") }
                val movieCatsDeferred = async { xtreamApi.getCategories(fullUrl, account.username, account.password, "get_vod_categories") }
                val seriesCatsDeferred = async { xtreamApi.getCategories(fullUrl, account.username, account.password, "get_series_categories") }

                val liveCats = liveCatsDeferred.await()
                val movieCats = movieCatsDeferred.await()
                val seriesCats = seriesCatsDeferred.await()

                boxStore.runInTx {
                    categoryBox.removeAll()
                    categoryBox.put(liveCats.mapIndexed { index, it -> Category(categoryId = it.categoryId, categoryName = it.categoryName, parentId = it.parentId, type = "live", order = index) })
                    categoryBox.put(movieCats.mapIndexed { index, it -> Category(categoryId = it.categoryId, categoryName = it.categoryName, parentId = it.parentId, type = "movie", order = index) })
                    categoryBox.put(seriesCats.mapIndexed { index, it -> Category(categoryId = it.categoryId, categoryName = it.categoryName, parentId = it.parentId, type = "series", order = index) })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncCategories failed: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            trace.stop()
        }
    }

    suspend fun syncAllStreams(
        onLiveProgress: (Int) -> Unit,
        onMovieProgress: (Int) -> Unit,
        onSeriesProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val syncTrace = FirebasePerformance.getInstance().newTrace("full_catalog_sync")
        syncTrace.start()
        try {
            val account = resolveWorkingAccount() ?: return@withContext
            
            Log.i(TAG, "Starting full catalog sync. Initial counts: Live=${liveBox.count()}, Movie=${movieBox.count()}, Series=${seriesBox.count()}")
            
            // Proactively clear stale proxy connections before starting a massive sync
            okHttpClient.connectionPool.evictAll()
            
            syncStreamType(account, "get_live_streams") { inputStream -> 
                parser.parseLiveStreams(inputStream, null, onLiveProgress) 
            }
            Log.i(TAG, "Live streams sync complete. New Live count: ${liveBox.count()}")
            
            syncStreamType(account, "get_vod_streams") { inputStream -> 
                parser.parseMovies(inputStream, null, onMovieProgress) 
            }
            Log.i(TAG, "VOD sync complete. New Movie count: ${movieBox.count()}")
            
            syncStreamType(account, "get_series") { inputStream -> 
                parser.parseSeries(inputStream, null, onSeriesProgress) 
            }
            Log.i(TAG, "Series sync complete. New Series count: ${seriesBox.count()}")

            // Cleanup: Remove orphaned data for channels that no longer exist
            cleanupOrphanedData()
            Log.i(TAG, "Catalog sync finished. Final Live count: ${liveBox.count()}")
        } finally {
            syncTrace.stop()
        }
    }

    /**
     * Lightweight VOD-only sync — movies + series, no Live channels.
     * Designed for silent background refresh on TV devices.
     */
    suspend fun syncVodOnly(
        onMovieProgress: (Int) -> Unit = {},
        onSeriesProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val syncTrace = FirebasePerformance.getInstance().newTrace("vod_silent_sync")
        syncTrace.start()
        try {
            val account = resolveWorkingAccount() ?: return@withContext

            Log.i(TAG, "Starting silent VOD sync. Current counts: Movie=${movieBox.count()}, Series=${seriesBox.count()}")

            // Evict stale connections before sync
            okHttpClient.connectionPool.evictAll()

            // Only sync Movies + Series (skip Live channels for speed)
            syncStreamType(account, "get_vod_streams") { inputStream ->
                parser.parseMovies(inputStream, null, onMovieProgress)
            }
            Log.i(TAG, "Silent VOD sync: movies done. New count: ${movieBox.count()}")

            syncStreamType(account, "get_series") { inputStream ->
                parser.parseSeries(inputStream, null, onSeriesProgress)
            }
            Log.i(TAG, "Silent VOD sync: series done. New count: ${seriesBox.count()}")

            // Lightweight cleanup — only series-related orphans (skip EPG since we skip Live)
            cleanupVodOrphans()
            Log.i(TAG, "Silent VOD sync completed")
        } finally {
            syncTrace.stop()
        }
    }

    /** Lightweight cleanup for VOD-only orphaned data (seasons/episodes with no matching series). */
    private fun cleanupVodOrphans() {
        try {
            val seriesIds = seriesBox.query().build().use { it.property(Series_.id).findLongs().toSet() }
            boxStore.runInTx {
                val episodeBox = boxStore.boxFor(Episode::class.java)
                val seasonBox = boxStore.boxFor(Season::class.java)
                val orphanedEpisodes = episodeBox.query().build().use { q -> q.find().filter { it.seriesId !in seriesIds } }
                episodeBox.remove(orphanedEpisodes)
                val orphanedSeasons = seasonBox.query().build().use { q -> q.find().filter { it.seriesId !in seriesIds } }
                seasonBox.remove(orphanedSeasons)
            }
        } catch (e: Exception) {
            Log.e(TAG, "VOD cleanup failed: ${e.message}")
        }
    }

    private fun cleanupOrphanedData() {
        try {
            // Optimized Cleanup: Only fetch IDs
            val liveIds = liveBox.query().build().use { it.property(LiveStream_.id).findLongs().toSet() }
            val seriesIds = seriesBox.query().build().use { it.property(Series_.id).findLongs().toSet() }
            
            boxStore.runInTx {
                val episodeBox = boxStore.boxFor(Episode::class.java)
                val seasonBox = boxStore.boxFor(Season::class.java)
                
                // Native Query removal is faster. 
                // ObjectBox doesn't have a direct "id NOT IN (list)" for removal in a single call easily 
                // without fetching, but we can at least minimize the data.
                
                val orphanedEpisodes = episodeBox.query().build().use { q -> q.find().filter { it.seriesId !in seriesIds } }
                episodeBox.remove(orphanedEpisodes)
                
                val orphanedSeasons = seasonBox.query().build().use { q -> q.find().filter { it.seriesId !in seriesIds } }
                seasonBox.remove(orphanedSeasons)
                
                if (!epgBox.isEmpty) {
                    val orphanedEpg = epgBox.query().build().use { q -> q.find().filter { it.channelId !in liveIds } }
                    epgBox.remove(orphanedEpg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup orphaned data failed: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private suspend fun syncStreamType(account: XtreamAccount, action: String, parseAction: suspend (InputStream) -> Unit) {
        // Build an ordered list of host URLs: current account host first, then all other active hosts.
        val accountBase = account.serverUrl.trimEnd('/') + "/"
        val allHosts = configRepository.getActiveHosts()
            .map { h -> if (h.url.endsWith("/")) h.url else "${h.url}/" }
            .distinct()
        val orderedHosts = buildList {
            add(accountBase)
            addAll(allHosts.filter { it != accountBase })
        }

        var lastError: Exception? = null
        for (host in orderedHosts) {
            val url = "${host}player_api.php?username=${account.username}&password=${account.password}&action=$action"
            val request = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.Builder().noCache().noStore().build())
                .build()

            var attempts = 0
            val maxAttempts = 2
            var succeeded = false
            while (attempts < maxAttempts) {
                try {
                    withContext(Dispatchers.IO) {
                        okHttpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                Log.w(TAG, "syncStreamType ($action) returned HTTP ${response.code} from $host")
                                return@use
                            }
                            val contentLength = response.header("Content-Length")
                            Log.i(TAG, "Syncing $action from $host: Content-Length=$contentLength")
                            response.body?.byteStream()?.let { parseAction(it) }
                        }
                    }
                    succeeded = true
                    break
                } catch (e: Exception) {
                    lastError = e
                    attempts++
                    Log.w(TAG, "syncStreamType ($action) attempt $attempts on $host failed: ${e.message}")
                    if (attempts < maxAttempts) delay(1000L * attempts)
                }
            }
            if (succeeded) {
                // Update account host to the working one so subsequent syncs start here.
                if (account.serverUrl != host) {
                    Log.i(TAG, "syncStreamType: updating account host from ${account.serverUrl} to $host")
                    account.serverUrl = host
                    accountBox.put(account)
                }
                return
            }
            Log.w(TAG, "syncStreamType ($action) host $host exhausted. Trying next host...")
        }

        Log.e(TAG, "syncStreamType ($action) failed on all ${orderedHosts.size} hosts")
        lastError?.let { FirebaseCrashlytics.getInstance().recordException(it) }
    }

    suspend fun fetchEpgForChannel(channel: LiveStream) = withContext(Dispatchers.IO) {
        // EPG fetching disabled as per user request
        return@withContext

        val account = resolveWorkingAccount() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        try {
            // 1. First, cleanup OLD EPG data for ALL channels to keep DB size small
            // We remove anything that stopped more than 2 hours ago
            val cleanupThreshold = System.currentTimeMillis() - (2 * 60 * 60 * 1000L)
            boxStore.runInTx {
                epgBox.query()
                    .less(EPGProgram_.stopTimestamp, cleanupThreshold)
                    .build()
                    .remove()
            }

            // 2. Fetch fresh EPG for this specific channel
            val response = xtreamApi.getShortEpg(fullUrl, account.username, account.password, streamId = channel.id)
            response.listings?.let { listings ->
                val epgPrograms = listings.map {
                    EPGProgram(
                        title = it.title,
                        description = it.description,
                        startTimestamp = it.startTimestamp * 1000,
                        stopTimestamp = it.stopTimestamp * 1000,
                        channelId = channel.id
                    )
                }
                val epgBox = boxStore.boxFor(EPGProgram::class.java)
                boxStore.runInTx {
                    epgBox.query().equal(EPGProgram_.channelId, channel.id).build().use { it.remove() }
                    epgBox.put(epgPrograms)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    suspend fun fetchSeriesDetails(series: Series) = withContext(Dispatchers.IO) {
        val account = resolveWorkingAccount() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        val trace = FirebasePerformance.getInstance().newTrace("fetch_series_details")
        trace.start()
        try {
            val response = xtreamApi.getSeriesInfo(fullUrl, account.username, account.password, seriesId = series.id)
            
            val seasonBox = boxStore.boxFor(Season::class.java)
            val episodeBox = boxStore.boxFor(Episode::class.java)

            // Update Series Metadata
            response.info?.let { info ->
                series.plot = info.plot ?: info.description
                series.cast = info.cast
                series.director = info.director
                series.genre = info.genre
                series.releaseDate = info.releaseDate
                
                // --- SMART Poster Enrichment logic ---
                val providerIcon = info.cover ?: info.movieImage ?: series.cover
                if (!isUrlWorking(providerIcon)) {
                    try {
                        val search = tmdbApi.searchTv(TMDB_API_KEY, series.name)
                        search.results.firstOrNull()?.let { result ->
                            series.cover = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                            series.backdropUrl = result.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "TMDB search failed for series ${series.name}")
                    }
                } else if (series.cover == null) {
                    series.cover = providerIcon
                }

                info.rating?.toDoubleOrNull()?.let { if (it > 0) series.rating = it }
                series.backdropUrl = info.backdropPath?.firstOrNull() ?: info.movieImage ?: info.cover
                
                // Re-read existing series from ObjectBox to preserve user flags (e.g. isFavorite)
                seriesBox.get(series.id)?.let { existing ->
                    series.isFavorite = existing.isFavorite
                    series.lastWatchedAt = existing.lastWatchedAt
                }
                seriesBox.put(series)
            }
            
            boxStore.runInTx {
                // 1. Snapshot existing progress for all episodes of this series
                val existingEpisodes = episodeBox.query().equal(Episode_.seriesId, series.id).build().use { it.find() }
                val progressMap = existingEpisodes.associate { it.id to Triple(it.watchProgress, it.totalDuration, it.lastWatchedAt) }

                // 2. Clear old seasons/episodes for this series
                val oldSeasons = seasonBox.query().equal(Season_.seriesId, series.id).build().use { it.find() }
                seasonBox.remove(oldSeasons)
                episodeBox.remove(existingEpisodes)

                // Always derive seasons from episode map keys first (authoritative), then overlay metadata
                val seasonNumbersFromEpisodes = response.episodes?.keys?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
                val seasonMetadataMap = response.seasons?.associateBy { it.seasonNumber } ?: emptyMap()

                val seasonsToProcess = seasonNumbersFromEpisodes.sorted().map { seasonNum ->
                    seasonMetadataMap[seasonNum] ?: XtreamSeasonResponse(seasonNum, "Season $seasonNum")
                }

                // 3. Add new seasons and episodes, restoring progress
                seasonsToProcess.forEach { s ->
                    val season = Season(
                        name = s.name ?: "Season ${s.seasonNumber}",
                        seasonNumber = s.seasonNumber,
                        seriesId = series.id
                    )
                    val seasonId = seasonBox.put(season)
                    
                    response.episodes?.get(s.seasonNumber.toString())?.forEach { eDto ->
                        val epId = eDto.id?.toLongOrNull() ?: 0L
                        val progress = progressMap[epId]

                        val episode = Episode(
                            id = epId,
                            title = eDto.title ?: eDto.infoMetadata?.name ?: "Episode ${eDto.episodeNum}",
                            containerExtension = eDto.containerExtension,
                            seasonNumber = eDto.season,
                            episodeNumber = eDto.episodeNum,
                            duration = eDto.infoMetadata?.duration,
                            plot = eDto.infoMetadata?.plot,
                            coverUrl = eDto.infoMetadata?.movieImage,
                            seriesId = series.id,
                            watchProgress = progress?.first ?: 0L,
                            totalDuration = progress?.second ?: 0L,
                            lastWatchedAt = progress?.third ?: 0L
                        )
                        episode.season.targetId = seasonId
                        episodeBox.put(episode)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchSeriesDetails failed: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            trace.stop()
        }
    }

    suspend fun fetchMovieDetails(movie: Movie) = withContext(Dispatchers.IO) {
        val account = resolveWorkingAccount() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        val trace = FirebasePerformance.getInstance().newTrace("fetch_movie_details")
        trace.start()
        try {
            val response = xtreamApi.getMovieInfo(fullUrl, account.username, account.password, streamId = movie.id)
            response.info?.let { info ->
                movie.plot = info.plot ?: info.description
                movie.cast = info.cast
                movie.director = info.director
                movie.genre = info.genre
                movie.duration = info.duration
                movie.backdropUrl = info.backdropUrl?.firstOrNull() ?: info.movieImage

                // --- Poster Enrichment logic ---
                // Only fallback to TMDB if the provider icon is missing or returns 404
                val providerIcon = info.movieImage ?: movie.streamIcon
                if (!isUrlWorking(providerIcon)) {
                    try {
                        val search = tmdbApi.searchMovie(TMDB_API_KEY, movie.name)
                        search.results.firstOrNull()?.let { result ->
                            movie.streamIcon = result.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
                            if (movie.backdropUrl.isNullOrBlank() || !isUrlWorking(movie.backdropUrl)) {
                                movie.backdropUrl = result.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "TMDB search failed for movie ${movie.name}")
                    }
                } else if (movie.streamIcon == null) {
                    movie.streamIcon = info.movieImage
                }

                info.rating?.toDoubleOrNull()?.let { if (it > 0) movie.rating = it }
                
                // Re-read existing movie from ObjectBox to preserve user flags (e.g. isFavorite, progress)
                movieBox.get(movie.id)?.let { existing ->
                    movie.isFavorite = existing.isFavorite
                    movie.lastWatchedAt = existing.lastWatchedAt
                    movie.watchProgress = existing.watchProgress
                    movie.totalDuration = existing.totalDuration
                }
                movieBox.put(movie)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMovieDetails failed: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            trace.stop()
        }
    }
}
