package com.eliteprime.app.data.remote

import android.util.Log
import com.eliteprime.app.data.local.entity.LiveStream
import com.eliteprime.app.data.local.entity.LiveStream_
import com.eliteprime.app.data.local.entity.Movie
import com.eliteprime.app.data.local.entity.Movie_
import com.eliteprime.app.data.local.entity.Series
import com.eliteprime.app.data.local.entity.Series_
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamStreamParser @Inject constructor(
    private val boxStore: BoxStore,
    private val gson: Gson
) {
    private val TAG = "XtreamStreamParser"
    private val BATCH_SIZE = 5000 // Increased batch size for performance

    suspend fun parseLiveStreams(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) = coroutineScope {
        val trace = FirebasePerformance.getInstance().newTrace("parse_live_streams")
        trace.start()
        val reader = JsonReader(InputStreamReader(BufferedInputStream(inputStream, 1024 * 256), "UTF-8"))
        reader.setLenient(true)
        val liveBox: Box<LiveStream> = boxStore.boxFor(LiveStream::class.java)
        var count = 0
        val writeChannel = Channel<List<LiveStream>>(capacity = 50) // More headroom for writes
        
        val writerJob = launch(Dispatchers.IO) {
            for (batch in writeChannel) {
                try {
                    // Preserve user data (favorites, last watched) that would be overwritten by sync
                    val existingIds = batch.map { it.id }.toLongArray()
                    val existing = liveBox.query().`in`(LiveStream_.id, existingIds).build().find()
                    val existingMap = existing.associateBy { it.id }
                    batch.forEach { newLive ->
                        existingMap[newLive.id]?.let { old ->
                            newLive.isFavorite = old.isFavorite
                            newLive.lastWatchedAt = old.lastWatchedAt
                        }
                    }
                    liveBox.put(batch)
                } catch (e: Exception) {
                    Log.e(TAG, "Error putting batch of live streams: ${e.message}")
                }
            }
        }
        
        try {
            val seenIds = mutableSetOf<Long>()
            val batch = mutableListOf<LiveStream>()
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
            }
            while (reader.hasNext()) {
                val element = try {
                    JsonParser.parseReader(reader)
                } catch (e: Exception) {
                    Log.e(TAG, "Syntax error in live stream array at count $count. Error: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                    break
                }

                try {
                    if (element.isJsonObject) {
                        val live: LiveStream? = gson.fromJson(element, LiveStream::class.java)
                        if (live != null && live.id != 0L) {
                            if (seenIds.contains(live.id)) {
                                if (count % 1000 == 0) Log.w(TAG, "Duplicate live stream ID: ${live.id}")
                            } else {
                                seenIds.add(live.id)
                                live.order = count
                                if (categoryId == null || live.remoteCategoryId == categoryId) {
                                    batch.add(live)
                                }
                                count++
                            }
                        }
                    } else if (element.isJsonPrimitive) {
                        // Some providers send weird primitives in the middle of the array
                        Log.w(TAG, "Skipping non-object element at count $count: $element")
                    }
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                        if (count % 5000 == 0) Log.i(TAG, "Live Streams Progress: Captured $count channels so far...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Mapping error for live stream at count $count: ${e.message}")
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
            Log.i(TAG, "Finished parsing live streams. Total count: $count")
            trace.putAttribute("count", count.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in parseLiveStreams: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            writeChannel.close()
            writerJob.join()
            onProgress(count)
            try { reader.close() } catch (_: Exception) {}
            trace.stop()
        }
    }

    suspend fun parseMovies(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) = coroutineScope {
        val trace = FirebasePerformance.getInstance().newTrace("parse_movies")
        trace.start()
        val reader = JsonReader(InputStreamReader(BufferedInputStream(inputStream, 1024 * 128), "UTF-8"))
        reader.setLenient(true)
        val movieBox: Box<Movie> = boxStore.boxFor(Movie::class.java)
        var count = 0
        val writeChannel = Channel<List<Movie>>(capacity = 50)
        
        val writerJob = launch(Dispatchers.IO) {
            for (batch in writeChannel) {
                try {
                    // Preserve user data (favorites, progress) that would be overwritten by sync
                    val existingIds = batch.map { it.id }.toLongArray()
                    val existing = movieBox.query().`in`(Movie_.id, existingIds).build().find()
                    val existingMap = existing.associateBy { it.id }
                    batch.forEach { newMovie ->
                        existingMap[newMovie.id]?.let { old ->
                            newMovie.isFavorite = old.isFavorite
                            newMovie.watchProgress = old.watchProgress
                            newMovie.totalDuration = old.totalDuration
                            newMovie.lastWatchedAt = old.lastWatchedAt
                        }
                    }
                    movieBox.put(batch)
                } catch (e: Exception) {
                    Log.e(TAG, "Error putting batch of movies: ${e.message}")
                }
            }
        }
        
        try {
            val batch = mutableListOf<Movie>()
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
            }
            while (reader.hasNext()) {
                val element = try {
                    JsonParser.parseReader(reader)
                } catch (e: Exception) {
                    Log.e(TAG, "Syntax error in movie array at count $count. Error: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                    break
                }

                try {
                    if (element.isJsonObject) {
                        val movie: Movie? = gson.fromJson(element, Movie::class.java)
                        if (movie != null && movie.id != 0L) {
                            movie.order = count
                            if (categoryId == null || movie.remoteCategoryId == categoryId) {
                                batch.add(movie)
                            }
                            count++
                        }
                    }
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                        if (count % 5000 == 0) Log.i(TAG, "Live Streams Progress: Captured $count channels so far...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Mapping error for movie at count $count: ${e.message}")
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
            trace.putAttribute("count", count.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in parseMovies: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            writeChannel.close()
            writerJob.join()
            onProgress(count)
            try { reader.close() } catch (_: Exception) {}
            trace.stop()
        }
    }

    suspend fun parseSeries(inputStream: InputStream, categoryId: String?, onProgress: (Int) -> Unit) = coroutineScope {
        val trace = FirebasePerformance.getInstance().newTrace("parse_series")
        trace.start()
        val reader = JsonReader(InputStreamReader(BufferedInputStream(inputStream, 1024 * 128), "UTF-8"))
        reader.setLenient(true)
        val seriesBox: Box<Series> = boxStore.boxFor(Series::class.java)
        var count = 0
        val writeChannel = Channel<List<Series>>(capacity = 50)
        
        val writerJob = launch(Dispatchers.IO) {
            for (batch in writeChannel) {
                try {
                    // Preserve user data (favorites) that would be overwritten by sync
                    val existingIds = batch.map { it.id }.toLongArray()
                    val existing = seriesBox.query().`in`(Series_.id, existingIds).build().find()
                    val existingMap = existing.associateBy { it.id }
                    batch.forEach { newSeries ->
                        existingMap[newSeries.id]?.let { old ->
                            newSeries.isFavorite = old.isFavorite
                            newSeries.lastWatchedAt = old.lastWatchedAt
                        }
                    }
                    seriesBox.put(batch)
                } catch (e: Exception) {
                    Log.e(TAG, "Error putting batch of series: ${e.message}")
                }
            }
        }
        
        try {
            val batch = mutableListOf<Series>()
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray()
            }
            while (reader.hasNext()) {
                val element = try {
                    JsonParser.parseReader(reader)
                } catch (e: Exception) {
                    Log.e(TAG, "Syntax error in series array at count $count. Error: ${e.message}")
                    FirebaseCrashlytics.getInstance().recordException(e)
                    break
                }

                try {
                    if (element.isJsonObject) {
                        val series: Series? = gson.fromJson(element, Series::class.java)
                        if (series != null && series.id != 0L) {
                            series.order = count
                            if (categoryId == null || series.remoteCategoryId == categoryId) {
                                batch.add(series)
                            }
                            count++
                        }
                    }
                    
                    if (batch.size >= BATCH_SIZE) {
                        writeChannel.send(batch.toList())
                        batch.clear()
                        onProgress(count)
                        if (count % 5000 == 0) Log.i(TAG, "Live Streams Progress: Captured $count channels so far...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Mapping error for series at count $count: ${e.message}")
                }
            }
            if (batch.isNotEmpty()) {
                writeChannel.send(batch)
            }
            trace.putAttribute("count", count.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in parseSeries: ${e.message}")
            FirebaseCrashlytics.getInstance().recordException(e)
        } finally {
            writeChannel.close()
            writerJob.join()
            onProgress(count)
            try { reader.close() } catch (_: Exception) {}
            trace.stop()
        }
    }
}
