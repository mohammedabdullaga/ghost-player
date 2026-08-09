package com.eliteprime.app.data.repository

import com.eliteprime.app.data.config.ConfigRepository
import com.eliteprime.app.data.local.entity.*
import com.eliteprime.app.data.remote.XtreamApi
import com.eliteprime.app.data.remote.XtreamStreamParser
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
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
    private val accountBox = boxStore.boxFor(XtreamAccount::class.java)
    private val categoryBox = boxStore.boxFor(Category::class.java)

    suspend fun login(user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val hosts = configRepository.getActiveHosts()
        if (hosts.isEmpty()) {
            configRepository.fetchConfig()
        }
        val activeHosts = configRepository.getActiveHosts()
        
        for (host in activeHosts) {
            try {
                val baseUrl = if (host.url.endsWith("/")) host.url else "${host.url}/"
                val fullUrl = "${baseUrl}player_api.php"
                
                val response = xtreamApi.authenticate(fullUrl, user, pass)
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        false
    }

    suspend fun syncCategories() = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        try {
            val liveCats = xtreamApi.getCategories(fullUrl, account.username, account.password, "get_live_categories")
            val movieCats = xtreamApi.getCategories(fullUrl, account.username, account.password, "get_vod_categories")
            val seriesCats = xtreamApi.getCategories(fullUrl, account.username, account.password, "get_series_categories")

            boxStore.runInTx {
                categoryBox.removeAll()
                categoryBox.put(liveCats.map { Category(categoryId = it.categoryId, categoryName = it.categoryName, parentId = it.parentId, type = "live") })
                categoryBox.put(movieCats.map { Category(categoryId = it.categoryId, categoryName = it.categoryName, parentId = it.parentId, type = "movie") })
                categoryBox.put(seriesCats.map { Category(categoryId = it.categoryId, categoryName = it.categoryName, parentId = it.parentId, type = "series") })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncAllStreams(
        onLiveProgress: (Int) -> Unit,
        onMovieProgress: (Int) -> Unit,
        onSeriesProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        
        syncStreamType(account, "get_live_streams") { inputStream -> parser.parseLiveStreams(inputStream, null, onLiveProgress) }
        syncStreamType(account, "get_vod_streams") { inputStream -> parser.parseMovies(inputStream, null, onMovieProgress) }
        syncStreamType(account, "get_series") { inputStream -> parser.parseSeries(inputStream, null, onSeriesProgress) }
    }

    private fun syncStreamType(account: XtreamAccount, action: String, parseAction: (InputStream) -> Unit) {
        val url = "${account.serverUrl}player_api.php?username=${account.username}&password=${account.password}&action=$action"
        val request = Request.Builder().url(url).build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            response.body?.byteStream()?.let { parseAction(it) }
        }
    }

    suspend fun fetchEpgForChannel(channel: LiveStream) = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        try {
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
        }
    }

    suspend fun fetchSeriesDetails(series: Series) = withContext(Dispatchers.IO) {
        val account = accountBox.query().equal(XtreamAccount_.isActive, true).build().findFirst() ?: return@withContext
        val fullUrl = "${account.serverUrl}player_api.php"
        try {
            val response = xtreamApi.getSeriesInfo(fullUrl, account.username, account.password, seriesId = series.id)
            
            val seasonBox = boxStore.boxFor(Season::class.java)
            val episodeBox = boxStore.boxFor(Episode::class.java)
            
            boxStore.runInTx {
                // Clear old seasons/episodes for this series
                val oldSeasons = seasonBox.query().equal(Season_.seriesId, series.id).build().find()
                oldSeasons.forEach { s ->
                    episodeBox.query().equal(Episode_.seriesId, series.id).build().remove()
                }
                seasonBox.remove(oldSeasons)

                // Add new seasons and episodes
                response.seasons?.forEach { s ->
                    val season = Season(
                        name = s.name ?: "Season ${s.seasonNumber}",
                        seasonNumber = s.seasonNumber,
                        seriesId = series.id
                    )
                    val seasonId = seasonBox.put(season)
                    
                    response.episodes?.get(s.seasonNumber.toString())?.forEach { e ->
                        e.seriesId = series.id
                        e.season.targetId = seasonId
                        episodeBox.put(e)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
