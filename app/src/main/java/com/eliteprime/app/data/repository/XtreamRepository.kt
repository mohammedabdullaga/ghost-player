package com.eliteprime.app.data.repository

import android.util.Log
import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.XtreamApi
import com.eliteprime.app.data.remote.XtreamSeasonResponse
import com.eliteprime.app.data.remote.XtreamStreamParser
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRepository @Inject constructor(
    private val xtreamApi: XtreamApi,
    private val boxStore: BoxStore,
    private val okHttpClient: OkHttpClient,
    private val configRepository: ConfigRepository,
    private val parser: XtreamStreamParser
) {
    private val TAG = "XtreamRepository"
    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val categoryBox = boxStore.boxFor(Category::class.java)
    private val liveBox = boxStore.boxFor(LiveStream::class.java)
    private val movieBox = boxStore.boxFor(Movie::class.java)
    private val seriesBox = boxStore.boxFor(Series::class.java)
    private val epgBox = boxStore.boxFor(EPGProgram::class.java)

    suspend fun login(user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val hosts = configRepository.getActiveHosts()
        if (hosts.isEmpty()) {
            Log.d(TAG, "Hosts list empty, fetching config...")
            configRepository.fetchConfig()
        }
        val activeHosts = configRepository.getActiveHosts()
        Log.d(TAG, "Attempting login with ${activeHosts.size} hosts")
        
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
                            name = "ArenaLive",
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login failed for host ${host.url}: ${e.message}")
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
        false
    }

    suspend fun syncCategories() = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
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
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    suspend fun syncAllStreams(
        onLiveProgress: (Int) -> Unit,
        onMovieProgress: (Int) -> Unit,
        onSeriesProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        
        coroutineScope {
            launch { syncStreamType(account, "get_live_streams") { inputStream -> parser.parseLiveStreams(inputStream, null, onLiveProgress) } }
            launch { syncStreamType(account, "get_vod_streams") { inputStream -> parser.parseMovies(inputStream, null, onMovieProgress) } }
            launch { syncStreamType(account, "get_series") { inputStream -> parser.parseSeries(inputStream, null, onSeriesProgress) } }
        }

        // Cleanup: Remove orphaned data for channels that no longer exist
        cleanupOrphanedData()
    }

    private fun cleanupOrphanedData() {
        try {
            // Optimized Cleanup: Only fetch IDs
            val liveIds = liveBox.query().build().property(LiveStream_.id).findLongs().toSet()
            val seriesIds = seriesBox.query().build().property(Series_.id).findLongs().toSet()
            
            boxStore.runInTx {
                val episodeBox = boxStore.boxFor(Episode::class.java)
                val seasonBox = boxStore.boxFor(Season::class.java)
                
                // Native Query removal is faster. 
                // ObjectBox doesn't have a direct "id NOT IN (list)" for removal in a single call easily 
                // without fetching, but we can at least minimize the data.
                
                val orphanedEpisodes = episodeBox.query().build().find().filter { it.seriesId !in seriesIds }
                episodeBox.remove(orphanedEpisodes)
                
                val orphanedSeasons = seasonBox.query().build().find().filter { it.seriesId !in seriesIds }
                seasonBox.remove(orphanedSeasons)
                
                if (!epgBox.isEmpty) {
                    val orphanedEpg = epgBox.query().build().find().filter { it.channelId !in liveIds }
                    epgBox.remove(orphanedEpg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup orphaned data failed: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private suspend fun syncStreamType(account: XtreamAccount, action: String, parseAction: suspend (InputStream) -> Unit) {
        val url = "${account.serverUrl}player_api.php?username=${account.username}&password=${account.password}&action=$action"
        val request = Request.Builder().url(url).build()
        
        withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                response.body?.byteStream()?.let { parseAction(it) }
            }
        }
    }

    suspend fun fetchEpgForChannel(channel: LiveStream) = withContext(Dispatchers.IO) {
        // EPG fetching disabled as per user request
        return@withContext

        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
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
                    epgBox.query().equal(EPGProgram_.channelId, channel.id).build().remove()
                    epgBox.put(epgPrograms)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    suspend fun fetchSeriesDetails(series: Series) = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
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
                info.rating?.toDoubleOrNull()?.let { if (it > 0) series.rating = it }
                series.backdropUrl = info.backdropPath?.firstOrNull() ?: info.movieImage ?: info.cover
                seriesBox.put(series)
            }
            
            boxStore.runInTx {
                // 1. Snapshot existing progress for all episodes of this series
                val existingEpisodes = episodeBox.query().equal(Episode_.seriesId, series.id).build().find()
                val progressMap = existingEpisodes.associate { it.id to Triple(it.watchProgress, it.totalDuration, it.lastWatchedAt) }

                // 2. Clear old seasons/episodes for this series
                val oldSeasons = seasonBox.query().equal(Season_.seriesId, series.id).build().find()
                seasonBox.remove(oldSeasons)
                episodeBox.remove(existingEpisodes)

                // Fallback for seasons if list is empty but episodes map is not
                val seasonsToProcess = if (response.seasons.isNullOrEmpty()) {
                    response.episodes?.keys?.mapNotNull { it.toIntOrNull() }?.sorted()?.map { 
                        XtreamSeasonResponse(it, "Season $it") 
                    } ?: emptyList()
                } else {
                    response.seasons!!
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
                            title = eDto.title ?: eDto.info?.name ?: "Episode ${eDto.episodeNum}",
                            containerExtension = eDto.containerExtension,
                            seasonNumber = eDto.season,
                            episodeNumber = eDto.episodeNum,
                            duration = eDto.info?.duration,
                            plot = eDto.info?.plot,
                            coverUrl = eDto.info?.movieImage,
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
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    suspend fun fetchMovieDetails(movie: Movie) = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        try {
            val response = xtreamApi.getMovieInfo(fullUrl, account.username, account.password, streamId = movie.id)
            response.info?.let { info ->
                movie.plot = info.plot ?: info.description
                movie.cast = info.cast
                movie.director = info.director
                movie.genre = info.genre
                movie.duration = info.duration
                movie.backdropUrl = info.backdropUrl?.firstOrNull() ?: info.movieImage
                info.rating?.toDoubleOrNull()?.let { if (it > 0) movie.rating = it }
                movieBox.put(movie)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}
