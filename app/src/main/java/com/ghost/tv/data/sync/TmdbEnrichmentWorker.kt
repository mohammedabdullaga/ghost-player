package com.ghost.tv.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ghost.tv.data.local.entity.Movie
import com.ghost.tv.data.local.entity.Movie_
import com.ghost.tv.data.local.entity.Series
import com.ghost.tv.data.local.entity.Series_
import com.ghost.tv.data.remote.TmdbApiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.objectbox.BoxStore

import io.objectbox.query.QueryBuilder.StringOrder

@HiltWorker
class TmdbEnrichmentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val tmdbApi: TmdbApiService,
    private val boxStore: BoxStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val apiKey = "77db3c693088e47469e0df1feebb4a4c"
        
        // 1. Fetch Trending
        try {
            val trendingMovies = tmdbApi.getTrendingMovies(apiKey)
            val movieBox = boxStore.boxFor(Movie::class.java)
            
            boxStore.runInTx {
                // Reset old trending
                val oldTrending = movieBox.query().equal(Movie_.isTrendingThisWeek, true).build().find()
                oldTrending.forEach { it.isTrendingThisWeek = false }
                movieBox.put(oldTrending)
                
                trendingMovies.results.forEachIndexed { index, result ->
                    val title = result.title ?: result.name ?: return@forEachIndexed
                    val matched = movieBox.query()
                        .contains(Movie_.name, title, StringOrder.CASE_INSENSITIVE)
                        .build()
                        .findFirst()
                    matched?.let {
                        it.isTrendingThisWeek = true
                        it.trendingRank = index
                        it.tmdbId = result.id.toLong()
                        it.backdropUrl = result.backdrop_path?.let { path -> "https://image.tmdb.org/t/p/original$path" }
                        movieBox.put(it)
                    }
                }
            }
        } catch (e: Exception) {
            // Log and continue
        }

        return Result.success()
    }
}
